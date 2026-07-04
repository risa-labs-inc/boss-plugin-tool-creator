package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

class ToolCreatorDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.toolcreator"
    override val displayName: String = "Tool Creator"
    override val version: String = "0.1.0"
    override val description: String =
        "Scaffold new BOSS plugins and start building them with Claude Code, Codex, Gemini, or OpenCode"
    override val author: String = "Risa Labs"
    override val url: String = "https://github.com/risa-labs-inc/boss-plugin-tool-creator"

    override fun register(context: PluginContext) {
        context.panelRegistry.registerPanel(ToolCreatorInfo) { ctx, panelInfo ->
            ToolCreatorComponent(ctx, panelInfo, context)
        }
    }

    override fun dispose() {}
}
