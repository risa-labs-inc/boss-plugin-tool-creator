package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.NewTabContext
import ai.rever.boss.plugin.api.TabInfo
import ai.rever.boss.plugin.api.TabTypeInfo

/** Use the registered native factory, so Fluck owns its tab and conversation lifecycle. */
internal fun createFluckTab(
    factory: TabTypeInfo?,
    toolName: String,
    workingDirectory: String,
    windowId: String?,
): TabInfo {
    check(factory?.newTabSpec != null) { "Fluck Agent is unavailable. Install, enable, or update it in the Toolbox, then reopen this project." }
    val prompt = """
        Build the $toolName BOSS plugin in the repository at:
        $workingDirectory

        Before implementing, read AGENTS.md and the tool-creator skill at
        .codex/skills/tool-creator/SKILL.md in that repository and follow its full instructions.
        The skill contains the requested behavior, capabilities, build, local testing, and publishing workflow.
        Work in this repository. Build the plugin and hot reload it for local testing.
        Keep credentials out of code and logs.
    """.trimIndent()
    return factory.createTabInfo(prompt, NewTabContext(projectPath = workingDirectory, windowId = windowId))
        ?: error("Fluck Agent could not open this project. Enable its tab support and try again.")
}
