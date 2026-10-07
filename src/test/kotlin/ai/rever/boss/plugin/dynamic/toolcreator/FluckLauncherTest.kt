package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.NewTabContext
import ai.rever.boss.plugin.api.NewTabSpec
import ai.rever.boss.plugin.api.TabInfo
import ai.rever.boss.plugin.api.TabTypeId
import ai.rever.boss.plugin.api.TabTypeInfo
import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.FeatherIcons
import compose.icons.feathericons.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FluckLauncherTest {
    @Test
    fun `native handoff binds the chosen repository and skill to the real factory`() {
        val factory = RecordingFactory()
        val directory = "/Users/example/Plugin work/O'Brien"
        val tab = createFluckTab(factory, "Invoice Extractor", directory, "window-2")

        assertSame(factory.tab, tab)
        assertEquals(directory, factory.context?.projectPath)
        assertEquals("window-2", factory.context?.windowId)
        assertTrue(factory.prompt.contains(directory))
        assertTrue(factory.prompt.contains("Invoice Extractor"))
        assertTrue(factory.prompt.contains("AGENTS.md"))
        assertTrue(factory.prompt.contains(".codex/skills/tool-creator/SKILL.md"))
    }

    @Test
    fun `missing or unsupported native factory fails without a terminal fallback`() {
        assertFailsWith<IllegalStateException> { createFluckTab(null, "Tool", "/tmp/project", null) }
        val unavailable = RecordingFactory(supported = false)
        assertFailsWith<IllegalStateException> { createFluckTab(unavailable, "Tool", "/tmp/project", null) }
        assertEquals("", unavailable.prompt)
    }

    @Test
    fun `factory rejection produces an actionable error`() {
        val rejected = RecordingFactory(reject = true)
        val error = assertFailsWith<IllegalStateException> { createFluckTab(rejected, "Tool", "/tmp/project", null) }
        assertTrue(error.message.orEmpty().contains("try again"))
    }

    @Test
    fun `native agent cannot accidentally generate a shell command`() {
        assertTrue(CliAgent.FLUCK_AGENT.isNative)
        assertFailsWith<IllegalStateException> { CliAgent.FLUCK_AGENT.launchCommand() }
        assertEquals("claude \"/tool-creator\"", CliAgent.CLAUDE_CODE.launchCommand())
    }

    private class RecordingFactory(val supported: Boolean = true, val reject: Boolean = false) : TabTypeInfo {
        override val typeId = TabTypeId("fluck-agent", "ai.rever.boss.plugin.dynamic.fluckagent")
        override val displayName = "Fluck Agent"
        override val icon: ImageVector = FeatherIcons.Tool
        override val newTabSpec = if (supported) NewTabSpec() else null
        var context: NewTabContext? = null
        var prompt = ""
        val tab = object : TabInfo {
            override val id = "native-session-id"
            override val typeId = this@RecordingFactory.typeId
            override val title = "Fluck Agent"
            override val icon = FeatherIcons.Tool
        }

        override fun createTabInfo(input: String, context: NewTabContext): TabInfo? {
            prompt = input
            this.context = context
            return tab.takeUnless { reject }
        }
    }
}
