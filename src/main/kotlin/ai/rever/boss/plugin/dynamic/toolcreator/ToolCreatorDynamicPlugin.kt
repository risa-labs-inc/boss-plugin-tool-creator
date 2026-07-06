package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.CustomPluginEvent
import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ToolCreatorDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.toolcreator"
    override val displayName: String = "Tool Creator"
    override val version: String = "0.1.0"
    override val description: String =
        "Scaffold new BOSS plugins and start building them with Claude Code, Codex, Gemini, or OpenCode"
    override val author: String = "Risa Labs"
    override val url: String = "https://github.com/risa-labs-inc/boss-plugin-tool-creator"

    // Set when another plugin (the Toolbox) asks us to open the New Tool dialog;
    // consumed by the panel when it next composes. Plugin-scoped so the request
    // survives even if the panel was never opened yet this session.
    private val pendingNewTool = AtomicBoolean(false)
    private var eventJob: Job? = null

    override fun register(context: PluginContext) {
        context.applicationEventBus?.let { bus ->
            eventJob = context.pluginScope.launch {
                bus.eventsOfType(CustomPluginEvent::class.java).collect { event ->
                    if (event.eventName == OPEN_NEW_TOOL_EVENT) pendingNewTool.set(true)
                }
            }
        }
        context.panelRegistry.registerPanel(ToolCreatorInfo) { ctx, panelInfo ->
            ToolCreatorComponent(ctx, panelInfo, context, pendingNewTool)
        }
    }

    override fun dispose() {
        eventJob?.cancel()
        eventJob = null
    }

    companion object {
        /** Event name the Toolbox publishes to request the New Tool dialog. */
        const val OPEN_NEW_TOOL_EVENT = "open-new-tool"
    }
}
