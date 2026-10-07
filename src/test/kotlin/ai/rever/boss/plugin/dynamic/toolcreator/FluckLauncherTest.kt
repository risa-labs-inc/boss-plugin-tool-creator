package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolRegistry
import ai.rever.boss.plugin.api.McpToolResult
import ai.rever.boss.plugin.api.RegisteredMcpTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FluckLauncherTest {
    @Test
    fun `native launch passes exact repository and full instructions through the registry`() = runBlocking<Unit> {
        val directory = "/Users/example/Plugin work/O'Brien \"tools\""
        val registry = RecordingRegistry(reply(directory))
        val tabId = launchFluck(registry, "Invoice Extractor", directory)
        val arguments = Json.parseToJsonElement(registry.arguments).jsonObject
        assertEquals("fluck_launch", registry.toolName)
        assertEquals("native-session-id", tabId)
        assertEquals(directory, arguments["project"]?.jsonPrimitive?.content)
        assertEquals("Invoice Extractor", arguments["title"]?.jsonPrimitive?.content)
        val prompt = arguments.getValue("prompt").jsonPrimitive.content
        assertTrue(prompt.contains(directory))
        assertTrue(prompt.contains("AGENTS.md"))
        assertTrue(prompt.contains(".codex/skills/tool-creator/SKILL.md"))
    }

    @Test
    fun `missing disabled or wrong provider tools cannot trigger a launch`() = runBlocking<Unit> {
        assertFalse(isFluckAvailable(null))
        val missing = RecordingRegistry(reply("/tmp/project"), exposed = false)
        assertFalse(isFluckAvailable(missing))
        assertFailsWith<IllegalStateException> { launchFluck(missing, "Tool", "/tmp/project") }
        assertEquals("", missing.arguments)
        val otherProvider = RecordingRegistry(reply("/tmp/project"), provider = "other.plugin")
        assertFalse(isFluckAvailable(otherProvider))
        assertFailsWith<IllegalStateException> { launchFluck(otherProvider, "Tool", "/tmp/project") }
    }

    @Test
    fun `host namespaced provider is accepted while lookalike namespaces are rejected`() = runBlocking<Unit> {
        val pluginId = "ai.rever.boss.plugin.dynamic.fluckagent"
        val namespaced = RecordingRegistry(reply("/tmp/project"), provider = "$pluginId::$pluginId")
        assertTrue(isFluckAvailable(namespaced))
        assertEquals("native-session-id", launchFluck(namespaced, "Tool", "/tmp/project"))
        listOf("$pluginId::", "$pluginId-extra::$pluginId", "$pluginId.evil::$pluginId").forEach { provider ->
            val lookalike = RecordingRegistry(reply("/tmp/project"), provider = provider)
            assertFalse(isFluckAvailable(lookalike))
            assertFailsWith<IllegalStateException> { launchFluck(lookalike, "Tool", "/tmp/project") }
            assertEquals("", lookalike.arguments)
        }
    }

    @Test
    fun `tool error never becomes a successful launch`() = runBlocking<Unit> {
        val registry = RecordingRegistry(McpToolResult("Project unavailable", isError = true))
        val error = assertFailsWith<IllegalStateException> { launchFluck(registry, "Tool", "/tmp/project") }
        assertTrue(error.message.orEmpty().contains("Project unavailable"))
    }

    @Test
    fun `malformed or missing tab identifiers fail with an actionable message`() = runBlocking<Unit> {
        listOf("not JSON", "[]", "{}", "{\"tab_id\":42,\"project\":\"/tmp/project\"}", "{\"tab_id\":\"\",\"project\":\"/tmp/project\"}").forEach { text ->
            val registry = RecordingRegistry(McpToolResult(text))
            val error = assertFailsWith<IllegalStateException> { launchFluck(registry, "Tool", "/tmp/project") }
            assertTrue(error.message.orEmpty().contains("try again"))
        }
    }

    @Test
    fun `launch must confirm the chosen repository`() = runBlocking<Unit> {
        val registry = RecordingRegistry(reply("/tmp/another-project"))
        val error = assertFailsWith<IllegalStateException> { launchFluck(registry, "Tool", "/tmp/project") }
        assertTrue(error.message.orEmpty().contains("confirm this repository"))
    }

    @Test
    fun `native agent cannot accidentally generate a shell command`() {
        assertTrue(CliAgent.FLUCK_AGENT.isNative)
        assertFailsWith<IllegalStateException> { CliAgent.FLUCK_AGENT.launchCommand() }
        assertEquals("claude \"/tool-creator\"", CliAgent.CLAUDE_CODE.launchCommand())
    }

    private fun reply(project: String) = McpToolResult(buildJsonObject {
        put("tab_id", "native-session-id")
        put("project", project)
    }.toString())

    private class RecordingRegistry(
        private val result: McpToolResult,
        exposed: Boolean = true,
        provider: String = "ai.rever.boss.plugin.dynamic.fluckagent",
    ) : McpToolRegistry {
        private val definitions = listOf(RegisteredMcpTool(provider, McpToolDefinition(
            name = "fluck_launch", description = "Launch native Fluck", readOnly = false,
            handler = McpToolHandler { result },
        )))
        override val tools = MutableStateFlow(if (exposed) definitions else emptyList())
        override val allTools = MutableStateFlow(definitions)
        override val disabledToolNames = MutableStateFlow(emptySet<String>())
        var arguments = ""
        var toolName = ""
        override fun setToolEnabled(toolName: String, enabled: Boolean) = Unit
        override suspend fun invoke(toolName: String, arguments: String): McpToolResult {
            this.toolName = toolName
            this.arguments = arguments
            return result
        }
    }
}
