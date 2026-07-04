package ai.rever.boss.plugin.dynamic.toolcreator

import java.io.File

/**
 * AI coding CLIs the Tool Creator can hand a freshly scaffolded plugin to.
 *
 * Each agent gets the tool-creator skill written into the scaffolded repo in its
 * own native format, and is launched in a new BossTerm tab with a kick-off prompt
 * that engages that skill.
 */
enum class CliAgent(
    val displayName: String,
    val binary: String,
) {
    CLAUDE_CODE("Claude Code", "claude"),
    CODEX("Codex", "codex"),
    GEMINI("Gemini", "gemini"),
    OPENCODE("OpenCode", "opencode");

    /**
     * Shell command that opens the CLI inside the scaffolded repo with the
     * tool-creator skill already engaged. Kept apostrophe-free so it survives
     * shell quoting untouched.
     */
    fun launchCommand(): String = when (this) {
        CLAUDE_CODE -> "claude \"/tool-creator\""
        CODEX -> "codex \"Load the tool-creator skill in .codex/skills/tool-creator/SKILL.md and follow it to build this tool.\""
        GEMINI -> "gemini -i \"Follow the tool-creator instructions in GEMINI.md to build this tool.\""
        OPENCODE -> "opencode --prompt \"Follow the tool-creator command in .opencode/command/tool-creator.md to build this tool.\""
    }

    /** Best-effort check whether the CLI binary is on PATH (or in common install dirs). */
    fun isInstalled(): Boolean {
        val home = System.getProperty("user.home")
        val extraDirs = listOf("$home/.local/bin", "/opt/homebrew/bin", "/usr/local/bin", "/usr/bin")
        val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
        return (pathDirs + extraDirs).any { dir ->
            dir.isNotBlank() && File(dir, binary).let { it.isFile && it.canExecute() }
        }
    }
}
