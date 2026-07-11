package ai.rever.boss.plugin.dynamic.toolcreator

import java.io.File
import java.util.concurrent.TimeUnit

class ScaffoldException(message: String) : Exception(message)

/**
 * Writes a complete, buildable BOSS plugin repo from the templates bundled in
 * this plugin's resources, then wires git (and optionally GitHub + release CI).
 *
 * Fatal problems throw [ScaffoldException]; recoverable ones (missing git/gh,
 * no API jar found) are reported through the log callback and skipped so the
 * user still gets a working local scaffold.
 */
class ScaffoldGenerator {

    /** @param publishKey Plugin Store API key to install as the repo's CI secret, if available. */
    fun scaffold(
        spec: ScaffoldSpec,
        publishKey: String?,
        log: (String) -> Unit,
    ): File {
        val dir = spec.repoDir
        if (dir.exists()) throw ScaffoldException("Directory already exists: ${dir.absolutePath}")
        if (!dir.mkdirs()) throw ScaffoldException("Could not create ${dir.absolutePath}")

        val tokens = tokensFor(spec)
        log("Scaffolding ${spec.repoName} in ${dir.absolutePath}")

        writeTemplates(dir, spec, tokens)
        writeSkills(dir, spec, tokens)
        writeGradleWrapper(dir)
        copyApiJar(dir, spec, log)
        initGit(dir, spec, log)
        if (spec.createGitHubRepo) setupGitHub(dir, spec, publishKey, log)

        log("Scaffold complete")
        return dir
    }

    // ---------------------------------------------------------------- tokens

    private fun tokensFor(spec: ScaffoldSpec): Map<String, String> {
        // Dialog permissions deliberately do NOT go into the scaffolded manifest's
        // requiredPermissions: the store/host treat that field as an RBAC install
        // gate, and these ids aren't in the RBAC catalog. They inform the AI agent
        // via the skill body and README instead.
        val permissionsBullets =
            if (spec.permissions.isEmpty()) "- (none requested)"
            else spec.permissions.joinToString("\n") { "- `${it.id}` — ${it.description}" }
        return mapOf(
            "TOOL_NAME" to spec.toolName.trim(),
            "TOOL_NAME_JSON" to jsonEscape(spec.toolName.trim()),
            "TOOL_NAME_KT" to ktEscape(spec.toolName.trim()),
            "TOOL_DESCRIPTION" to spec.description.trim(),
            "TOOL_DESCRIPTION_JSON" to jsonEscape(spec.description.trim()),
            "TOOL_DESCRIPTION_KT" to ktEscape(spec.description.trim()),
            "SLUG" to spec.slug,
            "PACKAGE" to spec.packageName,
            "CLASS_PREFIX" to spec.classPrefix,
            "PLUGIN_ID" to spec.pluginId,
            "REPO_NAME" to spec.repoName,
            "PERMISSIONS_BULLETS" to permissionsBullets,
            "AGENT_DISPLAY" to spec.agent.displayName,
        )
    }

    private fun jsonEscape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace("\t", "\\t")

    /** Escape for embedding inside a Kotlin string literal in generated sources. */
    private fun ktEscape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace("\t", "\\t")

    private fun render(resource: String, tokens: Map<String, String>): String {
        val raw = readResourceText(resource)
        return tokens.entries.fold(raw) { acc, (key, value) -> acc.replace("@@$key@@", value) }
    }

    private fun readResourceText(resource: String): String =
        javaClass.classLoader.getResourceAsStream(resource)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw ScaffoldException("Bundled template missing: $resource")

    private fun readResourceBytes(resource: String): ByteArray =
        javaClass.classLoader.getResourceAsStream(resource)?.use { it.readBytes() }
            ?: throw ScaffoldException("Bundled template missing: $resource")

    private fun write(dir: File, relPath: String, content: String) {
        val target = File(dir, relPath)
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
    }

    // ------------------------------------------------------------- templates

    private fun writeTemplates(dir: File, spec: ScaffoldSpec, tokens: Map<String, String>) {
        val pkgPath = "src/main/kotlin/ai/rever/boss/plugin/dynamic/${spec.packageName}"
        val files = mapOf(
            "settings.gradle.kts" to "scaffold/settings.gradle.kts.tmpl",
            "build.gradle.kts" to "scaffold/build.gradle.kts.tmpl",
            ".gitignore" to "scaffold/gitignore.tmpl",
            ".github/workflows/build.yml" to "scaffold/workflow-build.yml.tmpl",
            ".github/workflows/claude-code-review.yml" to "scaffold/workflow-claude-review.yml.tmpl",
            "README.md" to "scaffold/README.md.tmpl",
            "CLAUDE.md" to "scaffold/CLAUDE.md.tmpl",
            "AGENTS.md" to "scaffold/AGENTS.md.tmpl",
            "GEMINI.md" to "scaffold/GEMINI.md.tmpl",
            "src/main/resources/META-INF/boss-plugin/plugin.json" to "scaffold/plugin.json.tmpl",
            "$pkgPath/${spec.classPrefix}DynamicPlugin.kt" to "scaffold/src/DynamicPlugin.kt.tmpl",
            "$pkgPath/${spec.classPrefix}Info.kt" to "scaffold/src/Info.kt.tmpl",
            "$pkgPath/${spec.classPrefix}Component.kt" to "scaffold/src/Component.kt.tmpl",
            "$pkgPath/${spec.classPrefix}ViewModel.kt" to "scaffold/src/ViewModel.kt.tmpl",
            "$pkgPath/${spec.classPrefix}Content.kt" to "scaffold/src/Content.kt.tmpl",
        )
        files.forEach { (relPath, resource) -> write(dir, relPath, render(resource, tokens)) }
    }

    /**
     * The tool-creator skill, in every CLI's native format, so the repo works
     * with whichever agent the user opens it with later — not just the one
     * launched now.
     */
    private fun writeSkills(dir: File, spec: ScaffoldSpec, tokens: Map<String, String>) {
        val body = render("scaffold/skill/skill-body.md.tmpl", tokens)
        val description =
            "Build the ${spec.toolName.trim()} BOSS plugin in this repo following house conventions"

        val skillMd = """
            |---
            |name: tool-creator
            |description: $description
            |---
            |
            |$body
        """.trimMargin()
        write(dir, ".claude/skills/tool-creator/SKILL.md", skillMd)
        write(dir, ".codex/skills/tool-creator/SKILL.md", skillMd)

        val toml = """
            |description = "${description.replace("\"", "\\\"")}"
            |
            |prompt = ""${'"'}
            |$body
            |""${'"'}
        """.trimMargin()
        write(dir, ".gemini/commands/tool-creator.toml", toml)

        val openCodeCmd = """
            |---
            |description: $description
            |---
            |
            |$body
        """.trimMargin()
        write(dir, ".opencode/command/tool-creator.md", openCodeCmd)
    }

    private fun writeGradleWrapper(dir: File) {
        File(dir, "gradle/wrapper").mkdirs()
        File(dir, "gradle/wrapper/gradle-wrapper.jar")
            .writeBytes(readResourceBytes("scaffold/wrapper/gradle-wrapper.jar"))
        write(dir, "gradle/wrapper/gradle-wrapper.properties", readResourceText("scaffold/wrapper/gradle-wrapper.properties"))
        write(dir, "gradlew", readResourceText("scaffold/wrapper/gradlew"))
        write(dir, "gradlew.bat", readResourceText("scaffold/wrapper/gradlew.bat"))
        File(dir, "gradlew").setExecutable(true, false)
    }

    // ---------------------------------------------------------- boss-plugin-api jar

    /**
     * Copies the newest boss-plugin-api jar it can find into `libs/` so the
     * scaffold compiles standalone even outside the Boss workspace.
     */
    private fun copyApiJar(dir: File, spec: ScaffoldSpec, log: (String) -> Unit) {
        val home = System.getProperty("user.home")
        val candidates = listOf(
            File(spec.parentDir, "boss-plugin-api/build/libs"),
            File(home, ".boss_debug/plugins"),
            File(home, ".boss/plugins"),
        ).flatMap { location ->
            location.listFiles()?.filter {
                it.name.startsWith("boss-plugin-api-") && it.name.endsWith(".jar")
            } ?: emptyList()
        }
        val newest = candidates.maxByOrNull { versionKey(it.name) }
        if (newest == null) {
            log("Warning: no boss-plugin-api jar found — local builds need ../boss-plugin-api or libs/")
            return
        }
        val libs = File(dir, "libs").apply { mkdirs() }
        newest.copyTo(File(libs, newest.name))
        log("Bundled ${newest.name} into libs/")
    }

    private fun versionKey(jarName: String): String = jarName
        .removePrefix("boss-plugin-api-").removeSuffix(".jar")
        .split(".")
        .joinToString(".") { (it.toIntOrNull() ?: 0).toString().padStart(4, '0') }

    // ------------------------------------------------------------------- git

    private fun initGit(dir: File, spec: ScaffoldSpec, log: (String) -> Unit) {
        if (run(dir, "git", "--version").exitCode != 0) {
            log("Warning: git not found — skipping repo init")
            return
        }
        run(dir, "git", "init", "-b", "main")
        run(dir, "git", "add", "-A")
        val commit = run(dir, "git", "commit", "-m", "🎉 Scaffold ${spec.toolName.trim()} via BOSS Tool Creator")
        if (commit.exitCode == 0) log("Initialized git repo with initial commit")
        else log("Warning: git commit failed: ${commit.output.take(200)}")
    }

    private fun setupGitHub(dir: File, spec: ScaffoldSpec, publishKey: String?, log: (String) -> Unit) {
        if (run(dir, "gh", "--version").exitCode != 0) {
            log("Warning: gh CLI not found — create the GitHub repo manually")
            return
        }
        if (run(dir, "gh", "auth", "status").exitCode != 0) {
            log("Warning: gh is not authenticated — create the GitHub repo manually")
            return
        }
        val fullRepo = "risa-labs-inc/${spec.repoName}"
        val create = run(
            dir, "gh", "repo", "create", fullRepo,
            "--private", "--source=.", "--remote=origin", "--push",
            timeoutSec = 300,
        )
        if (create.exitCode != 0) {
            log("Warning: gh repo create failed: ${create.output.take(300)}")
            return
        }
        log("Created and pushed https://github.com/$fullRepo")

        if (publishKey.isNullOrBlank()) {
            log("Warning: no Plugin Store key available — set BOSS_STORE_PLUGIN_PUBLISH_KEY on the repo before releasing")
        } else {
            val secret = run(
                dir, "gh", "secret", "set", "BOSS_STORE_PLUGIN_PUBLISH_KEY", "--repo", fullRepo,
                stdin = publishKey,
            )
            if (secret.exitCode == 0) log("Installed BOSS_STORE_PLUGIN_PUBLISH_KEY secret — release CI is ready")
            else log("Warning: setting CI secret failed: ${secret.output.take(200)}")
        }

        registerAsSubmodule(spec, log)
    }

    /**
     * When the new repo was scaffolded directly inside the boss_plugins umbrella
     * checkout, register it there as a git submodule (matching .gitmodules house
     * pattern) and commit the wiring locally. The umbrella push is left to the
     * user. Skipped silently-with-a-log for any other location.
     */
    private fun registerAsSubmodule(spec: ScaffoldSpec, log: (String) -> Unit) {
        val parent = File(spec.parentDir)
        val topLevel = run(parent, "git", "rev-parse", "--show-toplevel")
        val isUmbrellaRoot = topLevel.exitCode == 0 &&
            File(topLevel.output.trim()).canonicalFile == parent.canonicalFile &&
            File(parent, ".gitmodules").isFile
        if (!isUmbrellaRoot) {
            log("Skipping submodule registration — ${parent.absolutePath} is not the boss_plugins repo root")
            return
        }

        val url = "https://github.com/risa-labs-inc/${spec.repoName}.git"
        val add = run(parent, "git", "submodule", "add", url, spec.slug)
        if (add.exitCode != 0) {
            log("Warning: git submodule add failed: ${add.output.take(200)}")
            return
        }
        val commit = run(
            parent, "git", "commit",
            "-m", "➕ Add ${spec.slug} plugin as submodule",
            "--", ".gitmodules", spec.slug,
        )
        if (commit.exitCode == 0) {
            log("Registered ${spec.slug} as a boss_plugins submodule (committed locally — push boss_plugins when ready)")
        } else {
            log("Warning: submodule commit failed: ${commit.output.take(200)}")
        }
    }

    // --------------------------------------------------------------- process

    private data class CmdResult(val exitCode: Int, val output: String)

    private fun run(
        dir: File,
        vararg cmd: String,
        stdin: String? = null,
        timeoutSec: Long = 120,
    ): CmdResult = try {
        val process = ProcessBuilder(*cmd)
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        if (stdin != null) {
            process.outputStream.bufferedWriter().use { it.write(stdin) }
        } else {
            process.outputStream.close()
        }
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            CmdResult(-1, "timed out after ${timeoutSec}s")
        } else {
            CmdResult(process.exitValue(), output.trim())
        }
    } catch (e: Exception) {
        CmdResult(-1, e.message ?: e.javaClass.simpleName)
    }
}
