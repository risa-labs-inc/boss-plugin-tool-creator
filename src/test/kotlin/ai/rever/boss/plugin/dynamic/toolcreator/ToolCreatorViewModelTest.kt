package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.ActiveTabData
import ai.rever.boss.plugin.api.ActiveTabsProvider
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolRegistry
import ai.rever.boss.plugin.api.McpToolResult
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.RegisteredMcpTool
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ToolCreatorViewModelTest {
    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `native creation succeeds without terminal operations`() = runBlocking {
        Harness().use { h ->
            val job = h.create(CliAgent.FLUCK_AGENT)
            assertEquals(ToolCreatorViewModel.JobStatus.SUCCESS, job.status)
            assertEquals("agent-1", job.agentTabId)
            assertEquals(1, h.registry.launches.get())
        }
    }

    @Test
    fun `CLI creation without terminal operations preserves scaffold and manual launch command`() = runBlocking {
        Harness().use { h ->
            val job = h.create(CliAgent.CODEX)
            assertEquals(ToolCreatorViewModel.JobStatus.SUCCESS, job.status)
            assertTrue(java.io.File(job.path, "build.gradle.kts").isFile)
            val quotedPath = "'" + job.path.replace("'", "'\\''") + "'"
            assertTrue(job.log.any { it.contains("run manually: cd $quotedPath && codex ") })
            assertEquals(0, h.registry.launches.get())
        }
    }

    @Test
    fun `reopen refreshes a stale inventory and focuses the original native tab`() = runBlocking {
        Harness().use { h ->
            val job = h.create(CliAgent.FLUCK_AGENT)
            assertTrue(h.cachedTabs.value.isEmpty())
            h.vm.reopenTerminal(job)
            await { h.selections.get() == 1 }
            assertTrue(h.refreshes.get() > 0)
            assertEquals(1, h.registry.launches.get())
        }
    }

    @Test
    fun `concurrent reopens launch once and subsequent stale card uses the new tab identity`() = runBlocking {
        Harness().use { h ->
            val oldJob = h.create(CliAgent.FLUCK_AGENT)
            h.liveTabs = emptyList()
            val release = CompletableDeferred<Unit>()
            h.registry.beforeLaunch = { release.await() }
            h.vm.reopenTerminal(oldJob)
            await { h.registry.launches.get() == 2 }
            h.vm.reopenTerminal(oldJob)
            release.complete(Unit)
            await { h.vm.jobs.value.single().agentTabId == "agent-2" }
            // Wait for the completion callback to release the single-flight guard.
            await { oldJob.id !in h.reopeningJobs() }
            h.vm.reopenTerminal(oldJob)
            await { h.selections.get() == 1 }
            assertEquals(2, h.registry.launches.get())
        }
    }

    @Test
    fun `failed inventory refresh does not classify an existing tab as closed`() = runBlocking {
        Harness().use { h ->
            val job = h.create(CliAgent.FLUCK_AGENT)
            h.failRefresh = true
            h.vm.reopenTerminal(job)
            await { h.vm.jobs.value.single().log.any { it.contains("inventory unavailable") } }
            assertEquals(1, h.registry.launches.get())
        }
    }

    @Test
    fun `missing inventory provider does not relaunch a stored native tab`() = runBlocking {
        Harness().use { h ->
            val job = h.create(CliAgent.FLUCK_AGENT)
            h.inventoryAvailable = false
            h.vm.reopenTerminal(job)
            await { h.vm.jobs.value.single().log.any { it.contains("Cannot check whether") } }
            assertEquals(1, h.registry.launches.get())
        }
    }

    @Test
    fun `tab moved to another window is not relaunched`() = runBlocking {
        Harness().use { h ->
            val job = h.create(CliAgent.FLUCK_AGENT)
            h.otherTabs.value = h.liveTabs.map { it.copy(windowId = "other-window") }
            h.liveTabs = emptyList()
            h.vm.reopenTerminal(job)
            await { h.vm.jobs.value.single().log.any { it.contains("another BOSS window") } }
            assertEquals(1, h.registry.launches.get())
        }
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(15_000) {
        while (!predicate()) delay(10)
    }

    private class Harness : AutoCloseable {
        private val directory = Files.createTempDirectory("creator O'Brien test ").toFile()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cachedTabs = MutableStateFlow<List<ActiveTabData>>(emptyList())
        val otherTabs = MutableStateFlow<List<ActiveTabData>>(emptyList())
        @Volatile var liveTabs = emptyList<ActiveTabData>()
        @Volatile var failRefresh = false
        @Volatile var inventoryAvailable = true
        val refreshes = AtomicInteger()
        val selections = AtomicInteger()
        val registry = Registry { id ->
            liveTabs = listOf(ActiveTabData(id, "fluck-agent", "Plugin", "workspace", "Workspace", "panel", "window"))
        }
        private val tabs: ActiveTabsProvider = proxy(ActiveTabsProvider::class.java) { name ->
            when (name) {
                "getActiveTabs" -> cachedTabs
                "getAllWindowTabs" -> otherTabs
                "refreshTabs" -> {
                    refreshes.incrementAndGet()
                    check(!failRefresh) { "inventory unavailable" }
                    cachedTabs.value = liveTabs
                    Unit
                }
                "refreshAllWindowTabs" -> Unit
                "selectTab" -> { selections.incrementAndGet(); Unit }
                else -> error("Unexpected tab operation: $name")
            }
        }
        private val context: PluginContext = proxy(PluginContext::class.java) { name ->
            when (name) {
                "getPluginScope" -> scope
                "getMcpToolRegistry" -> registry
                "getActiveTabsProvider" -> tabs.takeIf { inventoryAvailable }
                else -> null
            }
        }
        val vm = ToolCreatorViewModel(context)

        suspend fun create(agent: CliAgent): ToolCreatorViewModel.ToolJob {
            vm.setToolName("Test Plugin")
            vm.setDescription("A local test scaffold")
            vm.setParentDir(directory.absolutePath)
            vm.setCreateGitHubRepo(false)
            vm.setAgent(agent)
            vm.startBuilding()
            return withTimeout(15_000) {
                while (vm.jobs.value.firstOrNull()?.status == ToolCreatorViewModel.JobStatus.RUNNING) delay(10)
                checkNotNull(vm.jobs.value.firstOrNull()) { "Creation rejected: ${vm.form.value.error}" }
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun reopeningJobs(): Set<Long> = ToolCreatorViewModel::class.java.getDeclaredField("reopeningJobs").let {
            it.isAccessible = true
            it.get(vm) as Set<Long>
        }

        override fun close() {
            scope.cancel()
            vm.dispose()
            directory.deleteRecursively()
        }
    }

    private class Registry(private val opened: (String) -> Unit) : McpToolRegistry {
        val launches = AtomicInteger()
        @Volatile var beforeLaunch: suspend () -> Unit = {}
        private val definition = RegisteredMcpTool("ai.rever.boss.plugin.dynamic.fluckagent", McpToolDefinition(
            name = "fluck_launch", description = "Test launch", readOnly = false,
            handler = McpToolHandler { McpToolResult("unused") },
        ))
        override val tools = MutableStateFlow(listOf(definition))
        override val allTools = tools
        override val disabledToolNames = MutableStateFlow(emptySet<String>())
        override fun setToolEnabled(toolName: String, enabled: Boolean) = Unit
        override suspend fun invoke(toolName: String, arguments: String): McpToolResult {
            val number = launches.incrementAndGet()
            beforeLaunch()
            val project = Json.parseToJsonElement(arguments).jsonObject.getValue("project").jsonPrimitive.content
            val id = "agent-$number"
            opened(id)
            return McpToolResult(buildJsonObject { put("tab_id", id); put("project", project) }.toString())
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <T> proxy(type: Class<T>, value: (String) -> Any?): T = Proxy.newProxyInstance(
            type.classLoader, arrayOf(type),
        ) { _, method, _ -> value(method.name) } as T
    }
}
