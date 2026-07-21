package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.ApiKeyCreationResult
import ai.rever.boss.plugin.api.ApiKeyInfo
import ai.rever.boss.plugin.api.CreateSecretRequestData
import ai.rever.boss.plugin.api.NotificationType
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.tab.terminal.TerminalTabInfo
import ai.rever.boss.plugin.tab.terminal.TerminalTabType
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PUBLISH_SCOPE = "publish"
private const val API_KEY_SECRET_WEBSITE = "boss_plugin_store_api_key"
private const val CLIPBOARD_STATUS_MS = 4_000L
private const val CLIPBOARD_CLEAR_MS = 45_000L

class ToolCreatorViewModel(
    private val context: PluginContext,
    private val pendingNewTool: java.util.concurrent.atomic.AtomicBoolean =
        java.util.concurrent.atomic.AtomicBoolean(false),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val generator = ScaffoldGenerator()
    private val jobIds = AtomicLong(0)
    private val pendingPublishKey = AtomicReference<String?>(null)
    private val storedPublishKey = AtomicReference<String?>(null)
    private val clipboardCopyGeneration = AtomicLong(0)

    data class FormState(
        val toolName: String = "",
        val description: String = "",
        val permissions: Set<ToolPermission> = setOf(ToolPermission.READ_FILES),
        val agent: CliAgent = CliAgent.CLAUDE_CODE,
        val parentDir: String = defaultParentDir(),
        val createGitHubRepo: Boolean = true,
        val error: String? = null,
    )

    /**
     * Availability of the external CLIs the plugin shells out to. Advisory only:
     * detection can miss shell-rc-managed PATHs (nvm etc.), and the scaffold
     * degrades gracefully, so warnings never block the build.
     */
    data class EnvStatus(
        val missingAgents: Set<CliAgent> = emptySet(),
        val gitAvailable: Boolean = true,
        val ghInstalled: Boolean = true,
        val ghAuthenticated: Boolean = true,
        val checked: Boolean = false,
    )

    data class PublishApiKeyState(
        val permissionChecked: Boolean = false,
        val canManageApiKeys: Boolean = false,
        val hasPublishApiKey: Boolean? = null,
        val isChecking: Boolean = false,
        val isCreating: Boolean = false,
        val keyName: String? = null,
        val keyPrefix: String? = null,
        val canCopy: Boolean = false,
        val justCopied: Boolean = false,
        val copyError: String? = null,
        val error: String? = null,
    )

    enum class JobStatus { RUNNING, SUCCESS, FAILED }

    /** One tool build. Several can run concurrently, each with its own log. */
    data class ToolJob(
        val id: Long,
        val toolName: String,
        val agent: CliAgent,
        val path: String,
        val status: JobStatus,
        val log: List<String> = emptyList(),
    )

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    private val _jobs = MutableStateFlow<List<ToolJob>>(emptyList())
    val jobs: StateFlow<List<ToolJob>> = _jobs.asStateFlow()

    private val _env = MutableStateFlow(EnvStatus())
    val env: StateFlow<EnvStatus> = _env.asStateFlow()

    private val _publishApiKey = MutableStateFlow(PublishApiKeyState())
    val publishApiKey: StateFlow<PublishApiKeyState> = _publishApiKey.asStateFlow()

    // ------------------------------------------------------------ dialog API

    fun openDialog() {
        _form.value = FormState(parentDir = _form.value.parentDir)
        _showDialog.value = true
        refreshEnvStatus()
    }

    /** True (once) if another plugin requested the New Tool dialog since the last check. */
    fun consumePendingOpenRequest(): Boolean = pendingNewTool.getAndSet(false)

    /**
     * Check publishing-key readiness without exposing the setup UI to users who
     * do not have API-key management permission. This mirrors Secret Manager's
     * runtime permission gate in addition to the plugin manifest's install gate.
     */
    fun refreshPublishApiKeyStatus() {
        val provider = context.pluginStoreApiKeyProvider
        if (provider == null) {
            _publishApiKey.value = PublishApiKeyState(permissionChecked = true)
            return
        }
        if (_publishApiKey.value.isChecking || _publishApiKey.value.isCreating) return

        _publishApiKey.update { it.copy(isChecking = true, error = null) }
        scope.launch(Dispatchers.IO) {
            try {
                val canManage = provider.canManageApiKeys()
                if (!canManage) {
                    storedPublishKey.set(null)
                    _publishApiKey.value = PublishApiKeyState(
                        permissionChecked = true,
                        canManageApiKeys = false,
                    )
                    return@launch
                }

                val keys = provider.listApiKeys().getOrElse { error ->
                    _publishApiKey.value = PublishApiKeyState(
                        permissionChecked = true,
                        canManageApiKeys = true,
                        error = error.message ?: "Failed to check API keys",
                    )
                    return@launch
                }
                val now = System.currentTimeMillis()
                val activeKeys = keys
                    .filter { key ->
                        !key.isRevoked &&
                            PUBLISH_SCOPE in key.scopes &&
                            (key.expiresAt?.let { it > now } != false)
                    }
                    .sortedByDescending { it.createdAt }
                val storedKey = findStoredPublishKey(activeKeys)
                val displayedKey = storedKey?.keyInfo ?: activeKeys.firstOrNull()
                storedPublishKey.set(storedKey?.value)
                _publishApiKey.value = PublishApiKeyState(
                    permissionChecked = true,
                    canManageApiKeys = true,
                    hasPublishApiKey = displayedKey != null,
                    keyName = displayedKey?.name,
                    keyPrefix = displayedKey?.keyPrefix,
                    canCopy = storedKey != null && context.clipboardProvider != null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _publishApiKey.value = PublishApiKeyState(
                    permissionChecked = true,
                    error = e.message ?: "Failed to check API-key permission",
                )
            }
        }
    }

    /**
     * Create the missing publish-scoped key. Its one-time value stays in memory
     * until the next GitHub-backed scaffold installs it as the repository secret.
     */
    fun createPublishApiKey() {
        val provider = context.pluginStoreApiKeyProvider ?: return
        val state = _publishApiKey.value
        if (!state.permissionChecked || !state.canManageApiKeys || state.isCreating) return

        _publishApiKey.update { it.copy(isCreating = true, error = null) }
        scope.launch(Dispatchers.IO) {
            try {
                val result = provider.createApiKey(
                    name = "tool-creator: publishing",
                    scopes = listOf(PUBLISH_SCOPE),
                ).getOrElse { error ->
                    _publishApiKey.update {
                        it.copy(
                            isCreating = false,
                            error = error.message ?: "Failed to create publish API key",
                        )
                    }
                    return@launch
                }
                pendingPublishKey.set(result.apiKey)
                rememberPublishKey(result)
                val stored = storePublishKeySecret(result)
                context.notificationProvider?.showToast(
                    message = if (stored) {
                        "Publish API key created, saved, and ready for the next GitHub repository"
                    } else {
                        "Publish API key created and ready for this session"
                    },
                    type = if (stored) NotificationType.SUCCESS else NotificationType.WARNING,
                    title = "Tool Creator",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _publishApiKey.update {
                    it.copy(
                        isCreating = false,
                        error = e.message ?: "Failed to create publish API key",
                    )
                }
            }
        }
    }

    /** Copy the stored full key without ever exposing it in Compose state. */
    fun copyPublishApiKey() {
        val key = storedPublishKey.get()
        val clipboard = context.clipboardProvider
        if (key == null || clipboard == null) {
            _publishApiKey.update { it.copy(copyError = "Full key is not available to copy") }
            return
        }

        val copied = runCatching { clipboard.setText(key) }.getOrDefault(false)
        if (!copied) {
            _publishApiKey.update { it.copy(copyError = "Could not copy the API key") }
            return
        }

        val generation = clipboardCopyGeneration.incrementAndGet()
        _publishApiKey.update { it.copy(justCopied = true, copyError = null) }
        scope.launch {
            delay(CLIPBOARD_STATUS_MS)
            if (clipboardCopyGeneration.get() == generation) {
                _publishApiKey.update { it.copy(justCopied = false) }
            }
            delay(CLIPBOARD_CLEAR_MS - CLIPBOARD_STATUS_MS)
            val stillCopied = runCatching { clipboard.readText() == key }.getOrDefault(false)
            if (clipboardCopyGeneration.get() == generation && stillCopied) {
                runCatching { clipboard.setText("") }
            }
        }
    }

    private data class StoredPublishKey(val keyInfo: ApiKeyInfo, val value: String)

    /** API-key listings contain only prefixes, so match the key to Secret Manager's saved value. */
    private suspend fun findStoredPublishKey(activeKeys: List<ApiKeyInfo>): StoredPublishKey? {
        if (activeKeys.isEmpty() || !canReadStoredPublishKeys()) return null
        val provider = context.secretDataProvider ?: return null
        val secrets = try {
            provider.searchSecrets(API_KEY_SECRET_WEBSITE, limit = 100)
                .getOrNull()
                ?.data
                .orEmpty()
                .filter { it.website == API_KEY_SECRET_WEBSITE }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

        return activeKeys.firstNotNullOfOrNull { keyInfo ->
            secrets.firstOrNull { it.password.startsWith(keyInfo.keyPrefix) }
                ?.let { StoredPublishKey(keyInfo, it.password) }
        }
    }

    private fun canReadStoredPublishKeys(): Boolean {
        val auth = context.authDataProvider ?: return false
        return auth.isAdmin.value || auth.hasPermission("secret.read")
    }

    private fun rememberPublishKey(result: ApiKeyCreationResult) {
        storedPublishKey.set(result.apiKey)
        _publishApiKey.update {
            it.copy(
                permissionChecked = true,
                canManageApiKeys = true,
                hasPublishApiKey = true,
                isCreating = false,
                keyName = result.keyInfo.name,
                keyPrefix = result.keyInfo.keyPrefix,
                canCopy = context.clipboardProvider != null,
                copyError = null,
                error = null,
            )
        }
    }

    /** Persist the one-time value exactly as Secret Manager does, when permitted. */
    private suspend fun storePublishKeySecret(result: ApiKeyCreationResult): Boolean {
        if (!canReadStoredPublishKeys()) return false
        val provider = context.secretDataProvider ?: return false
        return try {
            provider.createSecret(
                CreateSecretRequestData(
                    website = API_KEY_SECRET_WEBSITE,
                    username = result.keyInfo.name,
                    password = result.apiKey,
                    notes = "Plugin Store API Key\nScopes: ${result.keyInfo.scopes.joinToString(", ")}",
                    tags = listOf("api_key"),
                )
            ).isSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private fun refreshEnvStatus() {
        scope.launch(Dispatchers.IO) {
            val missing = CliAgent.entries.filterNot { it.isInstalled() }.toSet()
            val gitOk = canRun("git", "--version")
            val ghOk = canRun("gh", "--version")
            val ghAuth = ghOk && canRun("gh", "auth", "status")
            _env.value = EnvStatus(missing, gitOk, ghOk, ghAuth, checked = true)
        }
    }

    private fun canRun(vararg cmd: String): Boolean = try {
        val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        process.outputStream.close()
        process.inputStream.bufferedReader().readText()
        process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0
    } catch (e: Exception) {
        false
    }

    fun dismissDialog() {
        _showDialog.value = false
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

    /** @param owner The dialog window, so the native picker stays above it and focused. */
    fun browseParentDir(owner: java.awt.Window?) {
        DirectoryPicker.pick(owner, _form.value.parentDir) { path ->
            if (path != null) setParentDir(path)
        }
    }

    // ------------------------------------------------------------- build it

    /**
     * Validates the form, spawns an independent build job, and frees the dialog
     * immediately — multiple tools can scaffold concurrently, each tracked as a
     * [ToolJob] in the panel.
     */
    fun startBuilding() {
        val state = _form.value
        val error = ScaffoldSpec.validate(state.toolName, state.description, state.parentDir)
        if (error != null) {
            _form.update { it.copy(error = error) }
            return
        }
        if (_jobs.value.any { it.status == JobStatus.RUNNING && it.toolName.equals(state.toolName.trim(), ignoreCase = true) }) {
            _form.update { it.copy(error = "A build for \"${state.toolName.trim()}\" is already running") }
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

        val jobId = jobIds.incrementAndGet()
        _jobs.update {
            listOf(
                ToolJob(jobId, spec.toolName.trim(), spec.agent, spec.repoDir.absolutePath, JobStatus.RUNNING)
            ) + it
        }
        _showDialog.value = false

        if (!spec.agent.isInstalled()) {
            appendJobLog(jobId, "Note: ${spec.agent.binary} not found on PATH — the terminal will report it if missing")
        }

        scope.launch(Dispatchers.IO) {
            try {
                val publishKey = if (spec.createGitHubRepo) mintPublishKey(spec) else null
                val dir = generator.scaffold(spec, publishKey) { appendJobLog(jobId, it) }
                updateJob(jobId) { it.copy(status = JobStatus.SUCCESS) }
                openTerminalTab(jobId, spec.toolName.trim(), spec.agent, dir.absolutePath)
                context.notificationProvider?.showToast(
                    message = "${spec.toolName} scaffolded — ${spec.agent.displayName} is taking over",
                    type = NotificationType.SUCCESS,
                    title = "Tool Creator",
                )
            } catch (e: Exception) {
                appendJobLog(jobId, "Failed: ${e.message}")
                updateJob(jobId) { it.copy(status = JobStatus.FAILED) }
                context.notificationProvider?.showToast(
                    message = e.message ?: "Scaffold failed",
                    type = NotificationType.ERROR,
                    title = "Tool Creator",
                )
            }
        }
    }

    /** Reopen the agent terminal for a finished job. */
    fun reopenTerminal(job: ToolJob) {
        openTerminalTab(job.id, job.toolName, job.agent, job.path)
    }

    /** Drop a finished job from the panel list. */
    fun dismissJob(job: ToolJob) {
        if (job.status != JobStatus.RUNNING) _jobs.update { list -> list.filterNot { it.id == job.id } }
    }

    private fun openTerminalTab(jobId: Long, title: String, agent: CliAgent, workingDirectory: String) {
        val ops = context.splitViewOperations ?: run {
            appendJobLog(jobId, "Terminal unavailable — run manually: cd $workingDirectory && ${agent.launchCommand()}")
            return
        }
        ops.openTab(
            TerminalTabInfo(
                id = "tool-creator-$jobId-${System.currentTimeMillis()}",
                typeId = TerminalTabType.typeId,
                title = title,
                initialCommand = agent.launchCommand(),
                workingDirectory = workingDirectory,
            )
        )
        appendJobLog(jobId, "Opened ${agent.displayName} in a terminal tab at $workingDirectory")
    }

    /**
     * Mints a publish-scoped Plugin Store API key for the new repo's release CI.
     * Best-effort: returns null when the provider is missing or the user lacks
     * key-management rights.
     */
    private suspend fun mintPublishKey(spec: ScaffoldSpec): String? {
        pendingPublishKey.getAndSet(null)?.let { return it }
        val provider = context.pluginStoreApiKeyProvider ?: return null
        val result = runCatching {
            provider.createApiKey(
                name = "tool-creator: ${spec.repoName}",
                scopes = listOf(PUBLISH_SCOPE),
            ).getOrNull()
        }.getOrNull() ?: return null
        rememberPublishKey(result)
        storePublishKeySecret(result)
        return result.apiKey
    }

    private fun appendJobLog(jobId: Long, line: String) =
        updateJob(jobId) { it.copy(log = it.log + line) }

    private fun updateJob(jobId: Long, transform: (ToolJob) -> ToolJob) =
        _jobs.update { list -> list.map { if (it.id == jobId) transform(it) else it } }

    fun dispose() {
        // Scope uses SupervisorJob; cancel would go here if we held long-lived work.
    }

    companion object {
        fun defaultParentDir(): String =
            File(System.getProperty("user.home"), "BossTools")
                .apply { mkdirs() }
                .absolutePath
    }
}
