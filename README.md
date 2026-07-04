# BOSS Tool Creator Plugin

Sidebar plugin that scaffolds new BOSS plugins and hands them straight to an AI
coding agent.

Clicking the Tool Creator icon opens a dialog asking for:

- **Plugin name** and **tool description**
- **Tool permissions** (files, shell, network, browser, secrets, MCP tools)
- **Which CLI to build with**: Claude Code, Codex, Gemini, or OpenCode

"Start building" then:

1. Scaffolds a complete plugin repo (build files, gradle wrapper, manifest with
   the requested permissions, skeleton panel UI, release CI workflow).
2. Writes the `tool-creator` skill in every CLI's native format, pre-filled with
   the tool's name, description, permissions, and BOSS plugin conventions —
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
