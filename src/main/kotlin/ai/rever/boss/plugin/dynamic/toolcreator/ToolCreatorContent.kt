package ai.rever.boss.plugin.dynamic.toolcreator

import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
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

    LaunchedEffect(Unit) {
        viewModel.refreshPublishApiKeyStatus()
        if (viewModel.consumePendingOpenRequest()) viewModel.openDialog()
    }

    BossTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = BossThemeColors.SurfaceColor) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = BossThemeColors.AccentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                        Icon(FeatherIcons.Tool, null, tint = BossThemeColors.AccentColor, modifier = Modifier.padding(10.dp).size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Tool Creator", color = BossThemeColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Turn an idea into a BOSS plugin", color = BossThemeColors.TextSecondary, fontSize = 12.sp)
                    }
                }
                CreatorCard {
                    Text("What do you want to build?", color = BossThemeColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Describe your tool, choose an agent, and start with a ready-to-build repository.", color = BossThemeColors.TextSecondary, fontSize = 12.sp)
                    Button(
                        onClick = viewModel::openDialog,
                        colors = ButtonDefaults.buttonColors(backgroundColor = BossThemeColors.AccentColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    ) { Text("Create a plugin", color = BossThemeColors.TextPrimary) }
                    if (jobs.isEmpty()) {
                        WorkflowStep("1", "Describe", "Name your plugin and tell the agent what it should do.")
                        WorkflowStep("2", "Build", "Use Fluck inside BOSS or your preferred coding CLI.")
                        WorkflowStep("3", "Publish", "Connect GitHub and use your own store publishing key.")
                    }
                }
                if (publishApiKey.shouldShowSection) {
                    PublishApiKeySection(publishApiKey, viewModel::createPublishApiKey, viewModel::copyPublishApiKey, viewModel::refreshPublishApiKeyStatus)
                }
                if (jobs.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("This session", color = BossThemeColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("${jobs.size} projects", color = BossThemeColors.TextMuted, fontSize = 11.sp)
                    }
                    jobs.forEach { JobRow(it, viewModel) }
                }
            }
        }
        if (showDialog) CreateToolDialog(viewModel)
    }
}

@Composable
private fun CreatorCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BossThemeColors.SurfaceColor,
        border = BorderStroke(1.dp, BossThemeColors.BorderColor),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun WorkflowStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(color = BossThemeColors.AccentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
            Text(number, color = BossThemeColors.AccentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, color = BossThemeColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = BossThemeColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun JobRow(job: ToolCreatorViewModel.ToolJob, viewModel: ToolCreatorViewModel) {
    var showLog by remember(job.id) { mutableStateOf(false) }
    val statusColor = when (job.status) {
        ToolCreatorViewModel.JobStatus.RUNNING -> BossThemeColors.AccentColor
        ToolCreatorViewModel.JobStatus.SUCCESS -> BossThemeColors.SuccessColor
        ToolCreatorViewModel.JobStatus.FAILED -> BossThemeColors.ErrorColor
    }
    CreatorCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (job.status == ToolCreatorViewModel.JobStatus.RUNNING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = statusColor, strokeWidth = 2.dp)
            } else {
                Icon(if (job.status == ToolCreatorViewModel.JobStatus.SUCCESS) FeatherIcons.Check else FeatherIcons.AlertTriangle,
                    null, tint = statusColor, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(job.toolName, color = BossThemeColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(when (job.status) {
                    ToolCreatorViewModel.JobStatus.RUNNING -> "Preparing project…"
                    ToolCreatorViewModel.JobStatus.SUCCESS -> "Project ready · ${job.agent.displayName}"
                    ToolCreatorViewModel.JobStatus.FAILED -> "Setup needs attention"
                }, color = statusColor, fontSize = 11.sp)
            }
        }
        Text(job.path, color = BossThemeColors.TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        job.log.lastOrNull()?.let {
            Text(it, color = if (job.status == ToolCreatorViewModel.JobStatus.FAILED) BossThemeColors.ErrorColor else BossThemeColors.TextSecondary,
                fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (job.status != ToolCreatorViewModel.JobStatus.RUNNING) {
            TextButton(onClick = { viewModel.reopenTerminal(job) }) {
                Icon(FeatherIcons.RefreshCw, null, tint = BossThemeColors.AccentColor, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open ${job.agent.displayName}", color = BossThemeColors.AccentColor, fontSize = 12.sp)
            }
        }
        if (job.log.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showLog = !showLog }) {
                    Text(if (showLog) "Hide setup log" else "Show setup log", color = BossThemeColors.TextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                if (job.status != ToolCreatorViewModel.JobStatus.RUNNING) {
                    TextButton(onClick = { viewModel.dismissJob(job) }) { Text("Dismiss", color = BossThemeColors.TextMuted, fontSize = 11.sp) }
                }
            }
        }
        if (showLog) {
            Divider(color = BossThemeColors.BorderColor)
            Text(job.log.joinToString("\n"), color = BossThemeColors.TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CreateToolDialog(viewModel: ToolCreatorViewModel) {
    val screen = remember { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
    DialogWindow(
        onCloseRequest = viewModel::dismissDialog,
        state = rememberDialogState(width = minOf(620, screen.width - 80).dp, height = minOf(780, screen.height - 120).dp),
        title = "Create a BOSS plugin",
    ) { CreateToolForm(viewModel, window) }
}

// The native dialog has its own composition root, so apply the host theme here too.
@Composable
private fun CreateToolForm(viewModel: ToolCreatorViewModel, dialogWindow: java.awt.Window?) {
    val form by viewModel.form.collectAsState()
    val env by viewModel.env.collectAsState()
    val publishApiKey by viewModel.publishApiKey.collectAsState()
    BossTheme {
        Surface(Modifier.fillMaxSize(), color = BossThemeColors.SurfaceColor) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Create a plugin", color = BossThemeColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("A ready-to-build project, with an agent to bring it to life.", color = BossThemeColors.TextSecondary, fontSize = 12.sp)
                }
                Divider(color = BossThemeColors.BorderColor)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CreatorCard {
                        WorkflowStep("1", "Describe your plugin", "Give the agent a clear goal and the details it needs.")
                        DialogTextField(form.toolName, viewModel::setToolName, "Plugin name", "e.g. Invoice Extractor")
                        DialogTextField(form.description, viewModel::setDescription, "What should it do?", "Describe the workflow, inputs, and result you want.", singleLine = false, minHeight = 100.dp)
                        var expanded by remember { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (expanded) FeatherIcons.ChevronDown else FeatherIcons.ChevronRight, null, tint = BossThemeColors.TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Capabilities", color = BossThemeColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("${form.permissions.size} selected", color = BossThemeColors.TextMuted, fontSize = 11.sp)
                        }
                        if (expanded) {
                            Text("Tell the agent which capabilities your tool needs. These guide implementation; they do not grant account permissions.", color = BossThemeColors.TextSecondary, fontSize = 11.sp)
                            ToolPermission.entries.forEach { permission ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(permission in form.permissions, { viewModel.togglePermission(permission) },
                                        colors = CheckboxDefaults.colors(checkedColor = BossThemeColors.AccentColor, uncheckedColor = BossThemeColors.TextMuted))
                                    Column(Modifier.weight(1f)) {
                                        Text(permission.label, color = BossThemeColors.TextPrimary, fontSize = 12.sp)
                                        Text(permission.description, color = BossThemeColors.TextMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        } else if (form.permissions.isNotEmpty()) {
                            Text(form.permissions.sortedBy { it.ordinal }.joinToString { it.label }, color = BossThemeColors.TextMuted, fontSize = 11.sp)
                        }
                    }
                    CreatorCard {
                        WorkflowStep("2", "Choose your coding agent", "Fluck works inside BOSS. CLI agents open in a terminal.")
                        AgentChoice(CliAgent.FLUCK_AGENT, form.agent == CliAgent.FLUCK_AGENT, env.checked && !env.fluckAvailable,
                            { viewModel.setAgent(CliAgent.FLUCK_AGENT) }, Modifier.fillMaxWidth())
                        BoxWithConstraints {
                            val columns = if (maxWidth < 420.dp) 1 else 2
                            CliAgent.entries.filterNot { it.isNative }.chunked(columns).let { rows ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rows.forEach { agents ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            agents.forEach { agent ->
                                                AgentChoice(agent, form.agent == agent, env.checked && agent in env.missingAgents,
                                                    { viewModel.setAgent(agent) }, Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (form.agent.isNative && env.checked && !env.fluckAvailable) {
                            FormNotice("Install or enable Fluck Agent in the Toolbox. If it is already enabled, update BOSS and Fluck, then reopen this dialog.")
                        } else if (!form.agent.isNative && env.checked && form.agent in env.missingAgents) {
                            FormNotice("${form.agent.binary} was not found. Install it or choose another agent; shell-managed installations may still work.")
                        }
                    }
                    CreatorCard {
                        WorkflowStep("3", "Set up the repository", "Build locally, or connect GitHub for automated releases.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { DialogTextField(form.parentDir, viewModel::setParentDir, "Parent folder", "Where to create the project") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { viewModel.browseParentDir(dialogWindow) }) {
                                Icon(FeatherIcons.Folder, "Browse for parent folder", tint = BossThemeColors.AccentColor, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (form.toolName.isNotBlank()) {
                            Text("Project folder: ${ScaffoldSpec(form.toolName, form.description, form.permissions, form.agent, form.parentDir, form.createGitHubRepo).repoDir.absolutePath}",
                                color = BossThemeColors.TextMuted, fontSize = 11.sp)
                        }
                        if (env.checked && !env.gitAvailable) FormNotice("Git was not found. Repository initialization will be skipped.")
                        Row(verticalAlignment = Alignment.Top) {
                            Checkbox(form.createGitHubRepo, viewModel::setCreateGitHubRepo,
                                colors = CheckboxDefaults.colors(checkedColor = BossThemeColors.AccentColor, uncheckedColor = BossThemeColors.TextMuted))
                            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Create GitHub repository", color = BossThemeColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Creates a private repository in risa-labs-inc with release CI and your publishing secret. Requires GitHub organisation access.", color = BossThemeColors.TextSecondary, fontSize = 11.sp)
                                Text("Leave this off to connect your own GitHub repository later.", color = BossThemeColors.TextMuted, fontSize = 11.sp)
                            }
                        }
                        if (form.createGitHubRepo && env.checked && !env.ghInstalled) FormNotice("GitHub CLI (gh) was not found. GitHub setup will be skipped.")
                        else if (form.createGitHubRepo && env.checked && !env.ghAuthenticated) FormNotice("Sign in with gh auth login to create the GitHub repository.")
                        if (publishApiKey.shouldShowSection) PublishApiKeySection(publishApiKey, viewModel::createPublishApiKey, viewModel::copyPublishApiKey, viewModel::refreshPublishApiKeyStatus)
                    }
                }
                Divider(color = BossThemeColors.BorderColor)
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    form.error?.let { Text(it, color = BossThemeColors.ErrorColor, fontSize = 12.sp) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(viewModel::dismissDialog) { Text("Cancel", color = BossThemeColors.TextSecondary) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = viewModel::startBuilding,
                            enabled = !(form.agent.isNative && env.checked && !env.fluckAvailable),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = BossThemeColors.AccentColor),
                        ) { Text("Create & open ${form.agent.displayName}", color = BossThemeColors.TextPrimary, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentChoice(agent: CliAgent, selected: Boolean, unavailable: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = BossThemeColors.AccentColor
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accent.copy(alpha = 0.10f) else BossThemeColors.SurfaceColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else BossThemeColors.BorderColor),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(agent.displayName, color = BossThemeColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(when {
                    unavailable && agent.isNative -> "Install or enable in Toolbox"
                    unavailable -> "CLI not detected"
                    agent.isNative -> "Inside BOSS · no CLI needed"
                    else -> "Terminal · ${agent.binary}"
                }, color = if (unavailable) BossThemeColors.WarningColor else BossThemeColors.TextMuted, fontSize = 11.sp)
            }
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Icon(FeatherIcons.Check, "Selected", tint = accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FormNotice(message: String) {
    Text(message, color = BossThemeColors.WarningColor, fontSize = 11.sp)
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
        shape = RoundedCornerShape(12.dp),
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
                                if (state.justCopied) "Copied" else "Copy API key",
                                color = BossThemeColors.TextPrimary,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Clipboard clears automatically after 45 seconds.",
                            color = BossThemeColors.TextMuted,
                            fontSize = 9.sp,
                        )
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
        shape = RoundedCornerShape(12.dp),
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
