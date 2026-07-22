package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.PluginContext
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import java.util.concurrent.atomic.AtomicBoolean

class ToolCreatorComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    pluginContext: PluginContext,
    pendingNewTool: AtomicBoolean = AtomicBoolean(false),
) : PanelComponentWithUI, ComponentContext by ctx {

    private val viewModel = ToolCreatorViewModel(pluginContext, pendingNewTool)

    init {
        lifecycle.doOnDestroy { viewModel.dispose() }
    }

    @Composable
    override fun Content() {
        ToolCreatorContent(viewModel)
    }
}
