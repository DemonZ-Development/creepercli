# GitHub Releases

- URL: https://github.com/DemonZ-Development/creepercli/releases
- Trigger: tag `v*` (workflows build the plugin and publish the CLI to npm)

## Release notes template (paste per tag)

```markdown
## CreeperCLI vX.Y.Z

### Highlights

- summary of changes for users

### Plugin (Java)

- bullet list from CHANGELOG.md

### CLI (Node.js)

- bullet list from CHANGELOG.md

### Assets

- `CreeperCLI-X.Y.Z.jar`: shaded plugin jar for Paper/Spigot 1.21.x, Java 21.

### Install

1. Replace the jar in `plugins/` and restart.
2. Update the CLI: `npm install -g creepercli@X.Y.Z`.

### Security note

The plugin binds `0.0.0.0:45678` by default. On a machine you control, set `network.host: "127.0.0.1"` and connect through an SSH tunnel. See the [security docs](https://demonz-development.github.io/creepercli/security/).

### Disclaimer

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool.
```

## Release checklist

- [ ] Update `CHANGELOG.md`.
- [ ] Tag `vX.Y.Z` and push. The plugin-build workflow attaches `CreeperCLI-X.Y.Z.jar` to the release; the npm workflow publishes the CLI.
- [ ] Attach the jar manually if the workflow did not.
- [ ] Copy the same release notes to Modrinth, SpigotMC, Hangar, and CurseForge.
