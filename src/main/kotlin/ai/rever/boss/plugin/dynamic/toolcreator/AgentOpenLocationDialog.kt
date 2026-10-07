package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.ui.BossDialog
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Columns
import compose.icons.feathericons.ExternalLink
import compose.icons.feathericons.PlusSquare
import compose.icons.feathericons.Server

internal enum class AgentOpenLocation(val value: String) {
    NEW_TAB("new_tab"), EXISTING_SPLIT("existing_split"),
    SPLIT_RIGHT("split_right"), SPLIT_DOWN("split_down"),
}

/** Same destination cards as BOSS's link chooser; opening requires an explicit choice. */
@Composable
internal fun AgentOpenLocationDialog(title: String, onChoose: (AgentOpenLocation) -> Unit, onDismiss: () -> Unit) {
    BossDialog(onDismissRequest = onDismiss) {
        Surface(color = BossThemeColors.SurfaceColor, shape = RoundedCornerShape(12.dp), elevation = 8.dp, modifier = Modifier.width(420.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = BossThemeColors.TextPrimary)
                Text("Where should it open?", fontSize = 12.sp, color = BossThemeColors.TextSecondary)
                val options = listOf(
                    Triple(AgentOpenLocation.EXISTING_SPLIT, "Existing Split", "Open in the other panel"),
                    Triple(AgentOpenLocation.SPLIT_RIGHT, "New Vertical Split", "Open alongside the current tab"),
                    Triple(AgentOpenLocation.SPLIT_DOWN, "New Horizontal Split", "Open below the current tab"),
                    Triple(AgentOpenLocation.NEW_TAB, "New Tab", "Open in the active main panel"),
                )
                options.forEach { (location, label, subtitle) ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onChoose(location) }, backgroundColor = BossThemeColors.TextPrimary.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp), elevation = 0.dp) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (location) {
                                AgentOpenLocation.NEW_TAB -> FeatherIcons.PlusSquare
                                AgentOpenLocation.EXISTING_SPLIT -> FeatherIcons.ExternalLink
                                AgentOpenLocation.SPLIT_RIGHT -> FeatherIcons.Columns
                                AgentOpenLocation.SPLIT_DOWN -> FeatherIcons.Server
                            }
                            Icon(icon, null, tint = BossThemeColors.AccentColor, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BossThemeColors.TextPrimary)
                                Text(subtitle, fontSize = 12.sp, color = BossThemeColors.TextSecondary)
                            }
                        }
                    }
                }
                TextButton(onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel", color = BossThemeColors.AccentColor) }
            }
        }
    }
}
