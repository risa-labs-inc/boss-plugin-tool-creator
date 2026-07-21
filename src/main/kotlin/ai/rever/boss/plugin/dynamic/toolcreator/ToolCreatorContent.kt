package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertTriangle
import compose.icons.feathericons.Check
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Folder
import compose.icons.feathericons.Key
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Tool

@Composable
fun ToolCreatorContent(viewModel: ToolCreatorViewModel) {
    val showDialog by viewModel.showDialog.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val publishApiKey by viewModel.publishApiKey.collectAsState()

    // Open the dialog when the panel appears if either: another plugin (the
    // Toolbox "Create a new plugin") requested it, or this is a fresh panel with
    // no builds yet. Otherwise just show the panel with its "New Tool…" button.
    LaunchedEffect(Unit) {
        viewModel.refreshPublishApiKeyStatus()
        if (viewModel.consumePendingOpenRequest() || jobs.isEmpty()) viewModel.openDialog()
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
                if (publishApiKey.shouldShowSection) {
                    Spacer(Modifier.height(12.dp))
                    PublishApiKeySection(
                        state = publishApiKey,
                        onCreate = viewModel::createPublishApiKey,
                        onCopy = viewModel::copyPublishApiKey,
                        onRetry = viewModel::refreshPublishApiKeyStatus,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.openDialog() },
                    colors = ButtonDefaults.buttonColors(backgroundColor = BossThemeColors.AccentColor),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("New Tool…", color = BossThemeColors.TextPrimary)
                }

                if (jobs.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Builds this session",
                        color = BossThemeColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        jobs.forEach { job ->
                            JobRow(job, viewModel)
                            Divider(color = BossThemeColors.BorderColor)
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
private fun JobRow(job: ToolCreatorViewModel.ToolJob, viewModel: ToolCreatorViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (job.status) {
            ToolCreatorViewModel.JobStatus.RUNNING -> CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = BossThemeColors.AccentColor,
                strokeWidth = 2.dp,
            )
            ToolCreatorViewModel.JobStatus.SUCCESS -> Icon(
                FeatherIcons.Check,
                contentDescription = "Succeeded",
                tint = BossThemeColors.SuccessColor,
                modifier = Modifier.size(12.dp),
            )
            ToolCreatorViewModel.JobStatus.FAILED -> Icon(
                FeatherIcons.AlertTriangle,
                contentDescription = "Failed",
                tint = BossThemeColors.ErrorColor,
                modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(job.toolName, color = BossThemeColors.TextPrimary, fontSize = 12.sp)
            Text(
                job.path,
                color = BossThemeColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (job.status != ToolCreatorViewModel.JobStatus.SUCCESS) {
                job.log.lastOrNull()?.let { line ->
                    Text(
                        line,
                        color = if (job.status == ToolCreatorViewModel.JobStatus.FAILED)
                            BossThemeColors.ErrorColor else BossThemeColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when (job.status) {
            ToolCreatorViewModel.JobStatus.RUNNING -> {}
            ToolCreatorViewModel.JobStatus.SUCCESS -> TextButton(onClick = { viewModel.reopenTerminal(job) }) {
                Icon(
                    FeatherIcons.RefreshCw,
                    contentDescription = "Reopen ${job.agent.displayName}",
                    tint = BossThemeColors.AccentColor,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(job.agent.displayName, color = BossThemeColors.AccentColor, fontSize = 11.sp)
            }
            ToolCreatorViewModel.JobStatus.FAILED -> TextButton(onClick = { viewModel.dismissJob(job) }) {
                Text("Dismiss", color = BossThemeColors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CreateToolDialog(viewModel: ToolCreatorViewModel) {
    DialogWindow(
        onCloseRequest = { viewModel.dismissDialog() },
        state = rememberDialogState(width = 520.dp, height = 680.dp),
        title = "Create New Tool",
    ) {
        CreateToolForm(viewModel, window)
    }
}

// The dialog window is its own composition root — composition locals from the
// panel (including theme) don't reach it, so BossTheme is re-applied here.
@Composable
private fun CreateToolForm(viewModel: ToolCreatorViewModel, dialogWindow: java.awt.Window?) {
    val form by viewModel.form.collectAsState()
    val env by viewModel.env.collectAsState()
    val publishApiKey by viewModel.publishApiKey.collectAsState()

    BossTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BossThemeColors.SurfaceColor,
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                if (publishApiKey.shouldShowSection) {
                    PublishApiKeySection(
                        state = publishApiKey,
                        onCreate = viewModel::createPublishApiKey,
                        onCopy = viewModel::copyPublishApiKey,
                        onRetry = viewModel::refreshPublishApiKeyStatus,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                DialogTextField(
                    value = form.toolName,
                    onValueChange = viewModel::setToolName,
                    label = "Plugin name",
                    placeholder = "e.g. Invoice Extractor",
                )
                Spacer(Modifier.height(8.dp))
                DialogTextField(
                    value = form.description,
                    onValueChange = viewModel::setDescription,
                    label = "Tool description",
                    placeholder = "What should this tool do?",
                    singleLine = false,
                    minHeight = 72.dp,
                )

                Spacer(Modifier.height(12.dp))
                var permissionsExpanded by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { permissionsExpanded = !permissionsExpanded },
                ) {
                    Icon(
                        if (permissionsExpanded) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight,
                        contentDescription = if (permissionsExpanded) "Collapse permissions" else "Expand permissions",
                        tint = BossThemeColors.TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Tool permissions", color = BossThemeColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (form.permissions.isEmpty()) "none selected" else "${form.permissions.size} selected",
                        color = BossThemeColors.TextMuted,
                        fontSize = 10.sp,
                    )
                }
                if (permissionsExpanded) {
                    Spacer(Modifier.height(4.dp))
                    ToolPermission.entries.forEach { permission ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = permission in form.permissions,
                                onCheckedChange = { viewModel.togglePermission(permission) },
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
                } else if (form.permissions.isNotEmpty()) {
                    Text(
                        form.permissions.sortedBy { it.ordinal }.joinToString { it.label },
                        color = BossThemeColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 18.dp, top = 2.dp),
                    )
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
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = BossThemeColors.AccentColor,
                                        unselectedColor = BossThemeColors.TextMuted,
                                    ),
                                )
                                val missing = env.checked && agent in env.missingAgents
                                Text(
                                    if (missing) "${agent.displayName} (not installed)" else agent.displayName,
                                    color = if (missing) BossThemeColors.TextMuted else BossThemeColors.TextPrimary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
                if (env.checked && form.agent in env.missingAgents) {
                    Text(
                        "${form.agent.binary} was not found on PATH — install it or pick another agent (the terminal will fail otherwise)",
                        color = BossThemeColors.WarningColor,
                        fontSize = 10.sp,
                    )
                }
                if (env.checked && !env.gitAvailable) {
                    Text(
                        "git was not found — the scaffold will skip repo init",
                        color = BossThemeColors.WarningColor,
                        fontSize = 10.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        DialogTextField(
                            value = form.parentDir,
                            onValueChange = viewModel::setParentDir,
                            label = "Location",
                            placeholder = "Parent directory for the new repo",
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.browseParentDir(dialogWindow) }) {
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
                        colors = CheckboxDefaults.colors(
                            checkedColor = BossThemeColors.AccentColor,
                            uncheckedColor = BossThemeColors.TextMuted,
                        ),
                    )
                    Column {
                        Text("Create GitHub repo + release CI", color = BossThemeColors.TextPrimary, fontSize = 12.sp)
                        Text(
                            "Uses gh to create risa-labs-inc/boss-plugin-…, install the publish secret, and register it as a boss_plugins submodule",
                            color = BossThemeColors.TextMuted,
                            fontSize = 10.sp,
                        )
                        if (form.createGitHubRepo && env.checked && !env.ghInstalled) {
                            Text(
                                "GitHub CLI (gh) was not found — this step will be skipped",
                                color = BossThemeColors.WarningColor,
                                fontSize = 10.sp,
                            )
                        } else if (form.createGitHubRepo && env.checked && !env.ghAuthenticated) {
                            Text(
                                "gh is not authenticated — run `gh auth login` first or this step will be skipped",
                                color = BossThemeColors.WarningColor,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }

                form.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = BossThemeColors.ErrorColor, fontSize = 11.sp)
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text("Cancel", color = BossThemeColors.TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.startBuilding() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = BossThemeColors.AccentColor),
                    ) {
                        Text("Start building", color = BossThemeColors.TextPrimary)
                    }
                }
            }
        }
    }
}

private val ToolCreatorViewModel.PublishApiKeyState.shouldShowSection: Boolean
    get() = permissionChecked && (error != null || canManageApiKeys && hasPublishApiKey != null)

@Composable
private fun PublishApiKeySection(
    state: ToolCreatorViewModel.PublishApiKeyState,
    onCreate: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
) {
    if (state.hasPublishApiKey == true) {
        ExistingPublishApiKey(state, onCopy)
    } else {
        MissingPublishApiKey(state, onCreate, onRetry)
    }
}

@Composable
private fun ExistingPublishApiKey(
    state: ToolCreatorViewModel.PublishApiKeyState,
    onCopy: () -> Unit,
) {
    var expanded by remember(state.keyPrefix) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BossThemeColors.SuccessColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, BossThemeColors.SuccessColor.copy(alpha = 0.4f)),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
            ) {
                Icon(
                    FeatherIcons.Key,
                    contentDescription = null,
                    tint = BossThemeColors.SuccessColor,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Publishing API key",
                        color = BossThemeColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        state.keyName ?: "Publish key configured",
                        color = BossThemeColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("Configured", color = BossThemeColors.SuccessColor, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (expanded) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight,
                    contentDescription = if (expanded) "Hide API key actions" else "Show API key actions",
                    tint = BossThemeColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }

            if (expanded) {
                Divider(color = BossThemeColors.BorderColor)
                Column(Modifier.padding(12.dp)) {
                    Text(
                        state.keyPrefix?.let { "$it…" } ?: "Publish-scoped key",
                        color = BossThemeColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.canCopy) {
                        Button(
                            onClick = onCopy,
                            colors = ButtonDefaults.buttonColors(backgroundColor = BossThemeColors.SuccessColor),
                        ) {
                            Icon(
                                FeatherIcons.Copy,
                                contentDescription = null,
                                tint = BossThemeColors.TextPrimary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.justCopied) "Copied — clears in 45s" else "Copy API key",
                                color = BossThemeColors.TextPrimary,
                                fontSize = 11.sp,
                            )
                        }
                    } else {
                        Text(
                            "The full key is not available here. Copy it from Secret Manager, or create a new key when needed.",
                            color = BossThemeColors.TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                    state.copyError?.let { error ->
                        Spacer(Modifier.height(6.dp))
                        Text(error, color = BossThemeColors.ErrorColor, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingPublishApiKey(
    state: ToolCreatorViewModel.PublishApiKeyState,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BossThemeColors.WarningColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, BossThemeColors.WarningColor.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    FeatherIcons.Key,
                    contentDescription = null,
                    tint = BossThemeColors.WarningColor,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Publishing setup",
                    color = BossThemeColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.error == null) {
                    "Create a publish-scoped API key for the next plugin repository's release workflow."
                } else {
                    state.error
                },
                color = if (state.error == null) BossThemeColors.TextSecondary else BossThemeColors.ErrorColor,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = if (state.hasPublishApiKey == false) onCreate else onRetry,
                enabled = !state.isCreating && !state.isChecking,
                colors = ButtonDefaults.buttonColors(backgroundColor = BossThemeColors.WarningColor),
            ) {
                if (state.isCreating || state.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = BossThemeColors.TextPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    when {
                        state.isCreating -> "Creating…"
                        state.isChecking -> "Checking…"
                        state.error != null && state.hasPublishApiKey == false -> "Try creating again"
                        state.error != null -> "Retry check"
                        else -> "Create publish API key"
                    },
                    color = BossThemeColors.TextPrimary,
                    fontSize = 11.sp,
                )
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
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { Text(placeholder, fontSize = 12.sp, color = BossThemeColors.TextMuted) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth().let { if (minHeight > 0.dp) it.heightIn(min = minHeight) else it },
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
