package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Tool

object ToolCreatorInfo : PanelInfo {
    override val id = PanelId("tool-creator", 30)
    override val displayName = "Tool Creator"
    override val icon = FeatherIcons.Tool
    override val defaultSlotPosition = left.bottom
}
