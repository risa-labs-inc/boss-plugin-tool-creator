package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.McpToolRegistry
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private const val FLUCK_PROVIDER = "ai.rever.boss.plugin.dynamic.fluckagent"
private const val FLUCK_LAUNCH_TOOL = "fluck_launch"

internal fun isFluckAvailable(registry: McpToolRegistry?): Boolean = registry?.tools?.value?.any {
    it.providerId == FLUCK_PROVIDER && it.definition.name == FLUCK_LAUNCH_TOOL
} == true

/** Fluck opens and owns its native tab; the host registry applies availability and permission gates. */
internal suspend fun launchFluck(
    registry: McpToolRegistry?,
    toolName: String,
    workingDirectory: String,
): String {
    check(isFluckAvailable(registry)) {
        "Fluck Agent is unavailable. Install, enable, or update it in the Toolbox and enable its fluck_launch tool, then reopen this project."
    }
    val prompt = """
        Build the $toolName BOSS plugin in the repository at:
        $workingDirectory

        Before implementing, read AGENTS.md and the tool-creator skill at
        .codex/skills/tool-creator/SKILL.md in that repository and follow its full instructions.
        The skill contains the requested behavior, capabilities, build, local testing, and publishing workflow.
        Work in this repository. Build the plugin and hot reload it for local testing.
        Keep credentials out of code and logs.
    """.trimIndent()
    val arguments = buildJsonObject {
        put("project", workingDirectory)
        put("prompt", prompt)
        put("title", toolName)
    }.toString()
    val result = checkNotNull(registry).invoke(FLUCK_LAUNCH_TOOL, arguments)
    check(!result.isError) { "Fluck Agent could not open this project: ${result.text.take(500)}" }
    val reply = try {
        Json.parseToJsonElement(result.text).jsonObject
    } catch (e: Exception) {
        throw IllegalStateException("Fluck Agent returned an invalid launch response. Update Fluck Agent and try again.", e)
    }
    val tabId = (reply["tab_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    check(!tabId.isNullOrBlank()) { "Fluck Agent did not return an opened tab. Update Fluck Agent and try again." }
    val project = (reply["project"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    check(!project.isNullOrBlank() && File(project).canonicalPath == File(workingDirectory).canonicalPath) {
        "Fluck Agent did not confirm this repository. Check its selected project before continuing."
    }
    return tabId
}
