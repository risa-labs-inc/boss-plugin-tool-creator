package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.NotificationType
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.tab.terminal.TerminalTabInfo
import ai.rever.boss.plugin.tab.terminal.TerminalTabType
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ToolCreatorViewModel(private val context: PluginContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val generator = ScaffoldGenerator()

    data class FormState(
        val toolName: String = "",
        val description: String = "",
        val permissions: Set<ToolPermission> = setOf(ToolPermission.READ_FILES),
        val agent: CliAgent = CliAgent.CLAUDE_CODE,
        val parentDir: String = defaultParentDir(),
        val createGitHubRepo: Boolean = false,
        val error: String? = null,
    )

    data class CreatedTool(
        val toolName: String,
        val path: String,
        val agent: CliAgent,
    )

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _createdTools = MutableStateFlow<List<CreatedTool>>(emptyList())
    val createdTools: StateFlow<List<CreatedTool>> = _createdTools.asStateFlow()

    // ------------------------------------------------------------ dialog API

    fun openDialog() {
        _form.value = FormState(parentDir = _form.value.parentDir)
        _showDialog.value = true
    }

    fun dismissDialog() {
        if (!_busy.value) _showDialog.value = false
    }

    fun setToolName(value: String) = _form.update { it.copy(toolName = value, error = null) }
    fun setDescription(value: String) = _form.update { it.copy(description = value, error = null) }
    fun setAgent(value: CliAgent) = _form.update { it.copy(agent = value) }
    fun setParentDir(value: String) = _form.update { it.copy(parentDir = value, error = null) }
    fun setCreateGitHubRepo(value: Boolean) = _form.update { it.copy(createGitHubRepo = value) }

    fun togglePermission(permission: ToolPermission) = _form.update {
        val next = if (permission in it.permissions) it.permissions - permission else it.permissions + permission
        it.copy(permissions = next)
    }

    fun browseParentDir() {
        val picker = context.directoryPickerProvider ?: run {
            appendLog("Directory picker unavailable — type the location instead")
            return
        }
        picker.pickDirectory { path -> if (path != null) setParentDir(path) }
    }

    // ------------------------------------------------------------- build it

    fun startBuilding() {
        val state = _form.value
        val error = ScaffoldSpec.validate(state.toolName, state.description, state.parentDir)
        if (error != null) {
            _form.update { it.copy(error = error) }
            return
        }
        val spec = ScaffoldSpec(
            toolName = state.toolName,
            description = state.description,
            permissions = state.permissions,
            agent = state.agent,
            parentDir = state.parentDir,
            createGitHubRepo = state.createGitHubRepo,
        )
        if (!spec.agent.isInstalled()) {
            appendLog("Note: ${spec.agent.binary} not found on PATH — the terminal will report it if missing")
        }

        _busy.value = true
        _log.value = emptyList()
        scope.launch(Dispatchers.IO) {
            try {
                val publishKey = if (spec.createGitHubRepo) mintPublishKey(spec) else null
                val dir = generator.scaffold(spec, publishKey, ::appendLog)
                _createdTools.update { listOf(CreatedTool(spec.toolName, dir.absolutePath, spec.agent)) + it }
                _busy.value = false
                _showDialog.value = false
                openAgentTerminal(spec, dir)
                context.notificationProvider?.showToast(
                    message = "${spec.toolName} scaffolded — ${spec.agent.displayName} is taking over",
                    type = NotificationType.SUCCESS,
                    title = "Tool Creator",
                )
            } catch (e: Exception) {
                appendLog("Failed: ${e.message}")
                _busy.value = false
                _form.update { it.copy(error = e.message ?: "Scaffold failed") }
                context.notificationProvider?.showToast(
                    message = e.message ?: "Scaffold failed",
                    type = NotificationType.ERROR,
                    title = "Tool Creator",
                )
            }
        }
    }

    /** Reopen the agent terminal for an already-created tool. */
    fun reopenTerminal(tool: CreatedTool) {
        openTerminalTab(tool.toolName, tool.agent, tool.path)
    }

    private fun openAgentTerminal(spec: ScaffoldSpec, dir: File) {
        openTerminalTab(spec.toolName, spec.agent, dir.absolutePath)
    }

    private fun openTerminalTab(title: String, agent: CliAgent, workingDirectory: String) {
        val ops = context.splitViewOperations ?: run {
            appendLog("Terminal unavailable — run manually: cd $workingDirectory && ${agent.launchCommand()}")
            return
        }
        ops.openTab(
            TerminalTabInfo(
                id = "tool-creator-${System.currentTimeMillis()}",
                typeId = TerminalTabType.typeId,
                title = title,
                initialCommand = agent.launchCommand(),
                workingDirectory = workingDirectory,
            )
        )
        appendLog("Opened ${agent.displayName} in a terminal tab at $workingDirectory")
    }

    /**
     * Mints a publish-scoped Plugin Store API key for the new repo's release CI.
     * Best-effort: returns null when the provider is missing or the user lacks
     * key-management rights.
     */
    private suspend fun mintPublishKey(spec: ScaffoldSpec): String? {
        val provider = context.pluginStoreApiKeyProvider ?: return null
        return runCatching {
            provider.createApiKey(
                name = "tool-creator: ${spec.repoName}",
                scopes = listOf("publish"),
            ).getOrNull()?.apiKey
        }.getOrNull()
    }

    private fun appendLog(line: String) = _log.update { it + line }

    fun dispose() {
        // Scope uses SupervisorJob; cancel would go here if we held long-lived work.
    }

    companion object {
        fun defaultParentDir(): String {
            val home = System.getProperty("user.home")
            val bossPlugins = File(home, "Development/Boss/boss_plugins")
            return if (bossPlugins.isDirectory) bossPlugins.absolutePath
            else File(home, "BossTools").absolutePath.also { File(it).mkdirs() }
        }
    }
}
