# CreeperCLI wiki

CreeperCLI manages Paper and Spigot Minecraft servers from the terminal. A Java plugin runs on the server; a Node CLI (`creepercli`, installed from npm) runs on your computer. Licensed under the Apache License 2.0.

## Pages

| Page | Covers |
|---|---|
| [Getting started](getting-started.md) | Install, first user, first login, SSH tunnel |
| [CLI commands](cli-commands.md) | Every command, flag, and example |
| [Console commands](console-commands.md) | `/creepercli` administration |
| [Configuration](configuration.md) | Every `config.yml` key, auto-migration |
| [Security](security.md) | Threat model, 2FA, sandbox, rate limits |
| [Protocol](protocol.md) | Wire format, actions, events, error codes |
| [Plugin API](plugin-api.md) | Extension API for Paper/Spigot developers |
| [Architecture](architecture.md) | Threading model, sandbox design, extensions |
| [Advanced](advanced.md) | Editing, sync, monitoring, scripting |
| [Troubleshooting](troubleshooting.md) | Common errors, FAQ, tests |

## Quick start

```bash
# server console
/creepercli user add alice 'a-strong-password-123'

# your machine
npm install -g creepercli
creepercli login --host <server-ip> --port 45678
```

You land in the interactive shell. Try `ls /`, `stats`, `exec list`. `q` quits.

## Security in one line

The plugin binds `0.0.0.0:45678` by default. On a machine you control, set `network.host: "127.0.0.1"` and reach it through an SSH tunnel. Full details on the [security](security.md) page.