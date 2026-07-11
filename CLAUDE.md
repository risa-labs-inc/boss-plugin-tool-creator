# CLAUDE.md

## Project Overview

**Tool Creator** (`ai.rever.boss.plugin.dynamic.toolcreator`) is a dynamic plugin for the BOSS desktop application.

Scaffold new BOSS plugins and start building them with Claude Code, Codex, Gemini, or OpenCode

- **Plugin ID**: `ai.rever.boss.plugin.dynamic.toolcreator`
- **Main Class**: `ai.rever.boss.plugin.dynamic.toolcreator.ToolCreatorDynamicPlugin`
- **API Version**: 1.0.51
- **Install gate**: `requiredPermissions: ["plugins.admin.publish", "api_key.create"]` —
  held by the `boss_admin` role; the `admin` role bypasses permission checks. Enforced
  at store download, Toolbox install, and host activation. Do NOT use the legacy
  `requiresAdmin` flag (it matches only the literal `admin` role and would exclude
  `boss_admin`).

## Essential Commands

```bash
./gradlew buildPluginJar    # Build plugin JAR (output: build/libs/)
./gradlew build              # Full build
./gradlew processResources   # Process resources (syncs version)
```

## Workflow Rules

- Do NOT run the BOSS application to test. The user will test manually.
- After building, copy JAR to `~/.boss/plugins/` for local testing.

## Architecture

### Plugin Structure
```
src/main/kotlin/   → Plugin source code (package: ai.rever.boss.plugin.dynamic.toolcreator)
src/main/resources/META-INF/boss-plugin/plugin.json → Plugin manifest
src/main/resources/scaffold/ → Templates written into every scaffolded plugin repo
build.gradle.kts   → Build config + version (single source of truth)
```

### What this plugin does

The sidebar panel opens a creation dialog (plugin name, tool description,
permissions, AI CLI picker). "Start building" then:

1. `ScaffoldGenerator` renders `src/main/resources/scaffold/` templates into a
   new repo (build files, gradle wrapper, plugin.json with the chosen
   `requiredPermissions`, skeleton panel sources, and the CI caller workflows —
   release on push to `main` plus Claude Code review on PRs, both delegating to
   shared workflows in `risa-labs-inc/BossConsole-Releases`).
2. Writes the `tool-creator` skill in all four CLI formats:
   `.claude/skills/tool-creator/SKILL.md`, `.codex/skills/tool-creator/SKILL.md`,
   `.gemini/commands/tool-creator.toml`, `.opencode/command/tool-creator.md` —
   the shared body lives in `scaffold/skill/skill-body.md.tmpl` and includes MCP
   tool-provider context.
3. Bundles the newest `boss-plugin-api-*.jar` it finds (sibling checkout,
   `~/.boss_debug/plugins`, `~/.boss/plugins`) into the repo's `libs/`.
4. `git init` + initial commit; optionally `gh repo create
   risa-labs-inc/boss-plugin-<slug> --push`, installs the
   `BOSS_STORE_PLUGIN_PUBLISH_KEY` secret (minted via
   `pluginStoreApiKeyProvider.createApiKey(scopes=["publish"])`), and — when the
   scaffold location is the boss_plugins umbrella root — registers the repo as a
   git submodule there (`git submodule add` + local commit, push left to user).
5. Opens a BossTerm tab via `splitViewOperations.openTab(TerminalTabInfo(...))`
   cd'd into the repo, running the chosen CLI with a kick-off prompt that
   engages the skill (`CliAgent.launchCommand()`).

Template tokens are `@@NAME@@`; Kotlin string contexts use `_KT` variants,
JSON contexts `_JSON` (see `ScaffoldGenerator.tokensFor`).

### Key Patterns
- Entry point: `DynamicPlugin` interface with `register(context)` and `dispose()`
- UI: `PanelComponentWithUI` with `@Composable Content()`
- State: ViewModel pattern with `StateFlow`
- Providers from `PluginContext`: `splitViewOperations`, `directoryPickerProvider`, `notificationProvider`, `pluginStoreApiKeyProvider`
- Null-safe provider access: providers may be null, UI must handle gracefully

### Dependencies
- **boss-plugin-api**: compileOnly (provided by host app at runtime)
- **Compose Desktop**: UI framework
- **Decompose**: Navigation and component lifecycle
- **Coroutines**: Async operations

## Version Management

**`build.gradle.kts` is the single source of truth for version.**

The `processResources` task automatically syncs the version into `plugin.json` at build time. Never manually edit the version in `plugin.json` — only change it in `build.gradle.kts`.

## Code Quality

- Use Compose Multiplatform APIs (not Android-specific)
- All Kotlin files must end with a newline
- Handle null providers gracefully — show fallback UI, never crash

## CI/CD

Pushes to `main` trigger the release workflow which:
1. Builds the plugin JAR
2. Creates a GitHub release
3. Publishes to the BOSS Plugin Store

The workflow is defined in `.github/workflows/build.yml` and delegates to the shared workflow in `risa-labs-inc/BossConsole-Releases`.
