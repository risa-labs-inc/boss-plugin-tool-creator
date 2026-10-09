package ai.rever.boss.plugin.dynamic.toolcreator

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolDataRootTest {
    @Test
    fun `default tool workspace is contained beneath boss`() {
        val home = Files.createTempDirectory("tool-creator-home")

        val workspace = java.nio.file.Path.of(ToolCreatorViewModel.defaultParentDir(home.toString()))

        val expectedRoot = home.resolve(".boss").toFile().canonicalFile.toPath()
        assertEquals(expectedRoot.resolve("workspaces/tools"), workspace)
        assertTrue(workspace.startsWith(expectedRoot))
        assertTrue(Files.isDirectory(workspace))
    }
}
