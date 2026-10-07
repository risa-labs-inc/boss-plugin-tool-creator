# BOSS Tool Creator Plugin

Sidebar plugin that scaffolds new BOSS plugins and hands them straight to an AI
coding agent.

> **Permission-gated**: installing this plugin requires the `plugins.create` and
> `api_key.create` permissions. Hosted BOSS grants both to the baseline `user`
> role for existing and new users; sign out and back in after a grant. Both are
> also held by `boss_plugin_admin` and inherited by `boss_admin`; `admin` bypasses.

**[Create and publish a plugin](https://github.com/risa-labs-inc/BossConsole/wiki/Create-and-Publish-a-Plugin)** covers **Toolbox → Create**, agent handoff, GitHub setup, and your publish key. Automatic repo creation targets `risa-labs-inc`; use the guide's personal-repository path if you do not have access to that organisation.

Choose **Create a plugin** in Tool Creator (or **Toolbox → Create**) to enter:

- **Plugin name** and **tool description**
- **Tool permissions** (files, shell, network, browser, secrets, MCP tools)
- **Coding agent**: Fluck Agent inside BOSS, or Claude Code, Codex, Gemini, or OpenCode in a terminal

"Start building" then:

1. Scaffolds a complete plugin repo (build files, gradle wrapper, manifest,
   skeleton panel UI, release CI workflow). The permissions you picked shape the
   skill and README, not the manifest's `requiredPermissions` - that field is an
   RBAC install gate and only accepts permissions in the RBAC catalog.
2. Writes the `tool-creator` skill in every CLI's native format, pre-filled with
   the tool's name, description, permissions, and BOSS plugin conventions -
   including how to expose the tool via MCP.
3. Initializes git (optionally creates `risa-labs-inc/boss-plugin-<name>` on
   GitHub with the Plugin Store publish secret installed).
4. Opens a Fluck Agent tab scoped to the new repository, with the tool-creator
   instructions as its first task, or a BossTerm tab running the chosen CLI.
   Fluck must be installed and enabled; no Fluck CLI is required. The project
   cards show setup progress and logs, and reopen the existing agent tab.

The native handoff uses Fluck Agent’s `fluck_launch` tool through the BOSS MCP
registry. Install or update Fluck Agent and keep that tool enabled in the Toolbox.
The chosen repository is passed explicitly and confirmed by the launch response.

## Build

```bash
./gradlew buildPluginJar    # output: build/libs/boss-plugin-tool-creator-<version>.jar
```

Copy the jar to `~/.boss/plugins/` (or `~/.boss_debug/plugins` for dev-mode
hosts) and reload plugins in BOSS.
