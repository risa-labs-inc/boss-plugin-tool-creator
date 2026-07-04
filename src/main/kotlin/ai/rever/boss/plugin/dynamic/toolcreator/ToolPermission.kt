package ai.rever.boss.plugin.dynamic.toolcreator

/**
 * Permission choices offered in the creation dialog.
 *
 * The selected ids land in the scaffolded plugin.json as `requiredPermissions`
 * and in the generated skill so the AI agent knows the tool's allowed surface.
 */
enum class ToolPermission(
    val id: String,
    val label: String,
    val description: String,
) {
    READ_FILES("files.read", "Read workspace files", "Read files and directories in the user workspace"),
    WRITE_FILES("files.write", "Write workspace files", "Create and modify files in the user workspace"),
    RUN_COMMANDS("shell.execute", "Run shell commands", "Spawn external processes on the user machine"),
    NETWORK("network.access", "Network access", "Make outbound HTTP or socket connections"),
    BROWSER("browser.control", "Control browser tabs", "Open and drive browser tabs in BOSS"),
    SECRETS("secrets.read", "Read secrets", "Access the BOSS secret manager"),
    MCP_TOOLS("mcp.tools", "Expose MCP tools", "Offer tools to AI agents via the boss MCP server"),
}
