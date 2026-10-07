# BOSS Tool Creator Plugin

Sidebar plugin that scaffolds new BOSS plugins and hands them straight to an AI
coding agent.

> **Permission-gated**: installing this plugin requires the `plugins.create` and
> `api_key.create` permissions. Hosted BOSS grants both to the baseline `user`
> role for existing and new users; refresh your session after a grant. Other
> deployments may grant `boss_plugin_admin`; `admin` bypasses the checks.

**[Create and publish a plugin](https://github.com/risa-labs-inc/BossConsole/wiki/Create-and-Publish-a-Plugin)** covers **Toolbox → Create**, agent handoff, GitHub setup, and your publish key. Automatic repo creation targets `risa-labs-inc`; use the guide's personal-repository path if you do not have access to that organisation.

Clicking the Tool Creator icon opens a dialog asking for:

- **Plugin name** and **tool description**
- **Tool permissions** (files, shell, network, browser, secrets, MCP tools)
- **Which CLI to build with**: Claude Code, Codex, Gemini, or OpenCode

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
4. Opens a BossTerm tab in the new repo running the chosen CLI with the skill
   already engaged.

## Build

```bash
./gradlew buildPluginJar    # output: build/libs/boss-plugin-tool-creator-<version>.jar
```

Copy the jar to `~/.boss/plugins/` (or `~/.boss_debug/plugins` for dev-mode
hosts) and reload plugins in BOSS.
