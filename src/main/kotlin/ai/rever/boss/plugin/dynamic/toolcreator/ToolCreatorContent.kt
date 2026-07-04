package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import compose.icons.FeatherIcons
import compose.icons.feathericons.Folder
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Tool

@Composable
fun ToolCreatorContent(viewModel: ToolCreatorViewModel) {
    val showDialog by viewModel.showDialog.collectAsState()
    val createdTools by viewModel.createdTools.collectAsState()
    val log by viewModel.log.collectAsState()

    // Clicking the sidebar icon should drop the user straight into the form.
    LaunchedEffect(Unit) {
        if (createdTools.isEmpty()) viewModel.openDialog()
    }

    BossTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = BossThemeColors.SurfaceColor) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        FeatherIcons.Tool,
                        contentDescription = null,
                        tint = BossThemeColors.AccentColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tool Creator",
                        color = BossThemeColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Scaffold a BOSS plugin and hand it to an AI coding agent.",
                    color = BossThemeColors.TextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.openDialog() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = BossThemeColors.AccentColor),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("New Tool…", color = BossThemeColors.TextPrimary)
                }

                if (createdTools.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Created this session",
                        color = BossThemeColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    createdTools.forEach { tool ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tool.toolName, color = BossThemeColors.TextPrimary, fontSize = 12.sp)
                                Text(
                                    tool.path,
                                    color = BossThemeColors.TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { viewModel.reopenTerminal(tool) }) {
                                Icon(
                                    FeatherIcons.RefreshCw,
                                    contentDescription = "Reopen ${tool.agent.displayName}",
                                    tint = BossThemeColors.AccentColor,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(tool.agent.displayName, color = BossThemeColors.AccentColor, fontSize = 11.sp)
                            }
                        }
                        Divider(color = BossThemeColors.BorderColor)
                    }
                }

                if (log.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        log.forEach { line ->
                            Text(
                                line,
                                color = if (line.startsWith("Warning") || line.startsWith("Failed"))
                                    BossThemeColors.WarningColor else BossThemeColors.TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        if (showDialog) {
            CreateToolDialog(viewModel)
        }
    }
}

@Composable
private fun CreateToolDialog(viewModel: ToolCreatorViewModel) {
    val form by viewModel.form.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val log by viewModel.log.collectAsState()

    Dialog(onDismissRequest = { viewModel.dismissDialog() }) {
        Surface(
            modifier = Modifier.width(460.dp),
            color = BossThemeColors.SurfaceColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Create New Tool",
                    color = BossThemeColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))

                DialogTextField(
                    value = form.toolName,
                    onValueChange = viewModel::setToolName,
                    label = "Plugin name",
                    placeholder = "e.g. Invoice Extractor",
                    enabled = !busy,
                )
                Spacer(Modifier.height(8.dp))
                DialogTextField(
                    value = form.description,
                    onValueChange = viewModel::setDescription,
                    label = "Tool description",
                    placeholder = "What should this tool do?",
                    enabled = !busy,
                    singleLine = false,
                    minHeight = 72.dp,
                )

                Spacer(Modifier.height(12.dp))
                Text("Tool permissions", color = BossThemeColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                ToolPermission.entries.forEach { permission ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = permission in form.permissions,
                            onCheckedChange = { viewModel.togglePermission(permission) },
                            enabled = !busy,
                            colors = CheckboxDefaults.colors(
                                checkedColor = BossThemeColors.AccentColor,
                                uncheckedColor = BossThemeColors.TextMuted,
                            ),
                        )
                        Column {
                            Text(permission.label, color = BossThemeColors.TextPrimary, fontSize = 12.sp)
                            Text(permission.description, color = BossThemeColors.TextMuted, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Start building with", color = BossThemeColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                CliAgent.entries.chunked(2).forEach { rowAgents ->
                    Row(Modifier.fillMaxWidth()) {
                        rowAgents.forEach { agent ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                RadioButton(
                                    selected = form.agent == agent,
                                    onClick = { viewModel.setAgent(agent) },
                                    enabled = !busy,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = BossThemeColors.AccentColor,
                                        unselectedColor = BossThemeColors.TextMuted,
                                    ),
                                )
                                Text(agent.displayName, color = BossThemeColors.TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        DialogTextField(
                            value = form.parentDir,
                            onValueChange = viewModel::setParentDir,
                            label = "Location",
                            placeholder = "Parent directory for the new repo",
                            enabled = !busy,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.browseParentDir() }, enabled = !busy) {
                        Icon(
                            FeatherIcons.Folder,
                            contentDescription = "Browse",
                            tint = BossThemeColors.AccentColor,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = form.createGitHubRepo,
                        onCheckedChange = viewModel::setCreateGitHubRepo,
                        enabled = !busy,
                        colors = CheckboxDefaults.colors(
                            checkedColor = BossThemeColors.AccentColor,
                            uncheckedColor = BossThemeColors.TextMuted,
                        ),
                    )
                    Column {
                        Text("Create GitHub repo + release CI", color = BossThemeColors.TextPrimary, fontSize = 12.sp)
                        Text(
                            "Uses gh to create risa-labs-inc/boss-plugin-… and install the publish secret",
                            color = BossThemeColors.TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                }

                form.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = BossThemeColors.ErrorColor, fontSize = 11.sp)
                }

                if (busy && log.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    log.takeLast(3).forEach { line ->
                        Text(
                            line,
                            color = BossThemeColors.TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { viewModel.dismissDialog() }, enabled = !busy) {
                        Text("Cancel", color = BossThemeColors.TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.startBuilding() },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(backgroundColor = BossThemeColors.AccentColor),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = BossThemeColors.TextPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (busy) "Building…" else "Start building", color = BossThemeColors.TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { Text(placeholder, fontSize = 12.sp, color = BossThemeColors.TextMuted) },
        enabled = enabled,
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth().let { if (minHeight > 0.dp) it.height(minHeight) else it },
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = BossThemeColors.TextPrimary,
            focusedBorderColor = BossThemeColors.AccentColor,
            unfocusedBorderColor = BossThemeColors.BorderColor,
            focusedLabelColor = BossThemeColors.AccentColor,
            unfocusedLabelColor = BossThemeColors.TextSecondary,
            cursorColor = BossThemeColors.AccentColor,
        ),
    )
}
