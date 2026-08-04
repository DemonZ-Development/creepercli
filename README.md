# CreeperCLI

Manage your Paper or Spigot Minecraft server from the terminal.

CreeperCLI is a remote administration tool in two parts:

| Component | Location | What it does |
|---|---|---|
| **Plugin** (Java 21, Paper 1.21+) | `plugin/` | Runs inside the server. Owns authentication, the file sandbox, allowlisted console commands, monitoring, and live log streaming over a small TCP protocol. |
| **CLI** (Node.js 18+, no dependencies) | `cli/` | Installs from npm. Gives you an interactive shell, local-editor workflow, verified file transfers, live console stream, and monitoring dashboards. |

Both parts are required. The plugin does nothing without a client; the CLI is a client for this plugin.

## Features

- **Sandboxed filesystem**. Every path resolves inside the server root. `..` escapes, symlink swaps, and absolute paths that leave the jail are rejected with `E_PATH_ESCAPE`.
- **Password plus TOTP 2FA**. Bcrypt (cost 12) hashes, RFC 6238 one-time codes with QR setup, 15-minute session tokens bound to your IP.
- **Brute-force defense**. Per-IP login limiter (3 failures / 5 min) and fail2ban (3 failures, 10-minute ban).
- **Console control without full access**. Only allowlisted commands run: `list`, `say *`, `whitelist *`, `restart` by default. Add your own patterns.
- **Full file toolset**. `ls`, `cat`, `edit` in your local `$EDITOR`, `grep`, `find`, `tree`, `cp`, `mv`, `rm`, `head`, `tail`, `wc`.
- **Verified transfers**. `cpush`, `cpull`, and `csync` stream in 64 KB chunks and check SHA-256 on both ends.
- **Live monitoring**. `stats` for CPU/RAM/disk, `tps`, a `top` dashboard, and a live console stream with `log --grep`.
- **Accountability**. Every action appends to an audit log with timestamp, source IP, user, and parameters.

## Install

1. Drop `CreeperCLI-1.0.0.jar` into the server's `plugins/` folder and restart. First boot creates `plugins/CreeperCLI/config.yml`.
2. Create a user from the server console: `/creepercli user add steve <strong-password>`.
3. Install the CLI on your computer: `npm install -g creepercli`.
4. Connect: `creepercli login --host <server-ip> --port 45678`. The CLI drops you into the interactive shell.

Full walkthrough: [wiki/getting-started.md](wiki/getting-started.md).

## Read the security notes first

The plugin binds `0.0.0.0:45678` by default so hosted panels and containers work out of the box. That means the port is reachable from anywhere that can reach the machine.

- On a machine you control, set `network.host: "127.0.0.1"` in `plugins/CreeperCLI/config.yml` and reach it through an SSH tunnel: `ssh -N -L 45678:127.0.0.1:45678 user@server`.
- Run the server as a non-root OS user. CreeperCLI is a full admin channel.
- Enable 2FA with `creepercli totp setup`. The limiter and fail2ban only slow attackers; 2FA stops credential theft.

The full threat model and hardening checklist is in [wiki/security.md](wiki/security.md).

## Documentation

The wiki in [`wiki/`](wiki/README.md) is the single source of truth.

| Page | Covers |
|---|---|
| [Getting started](wiki/getting-started.md) | Install, first user, first login, SSH tunnel |
| [CLI commands](wiki/cli-commands.md) | Every command, flag, and example |
| [Console commands](wiki/console-commands.md) | `/creepercli` administration |
| [Configuration](wiki/configuration.md) | Every `config.yml` key, auto-migration |
| [Security](wiki/security.md) | Threat model, 2FA, sandbox, rate limits |
| [Protocol](wiki/protocol.md) | Wire format, actions, events, error codes |
| [Plugin API](wiki/plugin-api.md) | Extension API for Paper/Spigot developers |
| [Architecture](wiki/architecture.md) | Threading model, sandbox design, extensions |
| [Advanced](wiki/advanced.md) | Editing, sync, monitoring, scripting |
| [Troubleshooting](wiki/troubleshooting.md) | Common errors, FAQ, tests |

Ready-to-post listings for Modrinth, SpigotMC, Hangar, CurseForge, npm, and GitHub Releases live in [`marketplace/`](marketplace/).

## Development

- Plugin: JDK 21 and Maven. `mvn -B package` to build in `plugin/`; `mvn test` for the PathSanitizer suite.
- CLI: Node.js 18+. `npm install` in `cli/`; `npm test` for tests.

## Releases

SemVer with `v*` tags. The plugin builds and the CLI publishes to npm on tag. v1.0.0 is out on npm as `creepercli`. See [CHANGELOG.md](CHANGELOG.md) and [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).