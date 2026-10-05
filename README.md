# CreeperCLI

Manage Paper/Spigot servers and BungeeCord/Waterfall or Velocity proxies from the terminal.

CreeperCLI is a remote administration tool in two parts:

| Component | Location | What it does |
|---|---|---|
| **Plugin** (Java 21, Paper/Spigot 1.21+, BungeeCord/Waterfall, Velocity) | `plugin/` | Runs inside the server or proxy. Owns authentication, the file sandbox, allowlisted console commands, monitoring, and live log streaming over a small TCP protocol. |
| **CLI** (Node.js 18+, one runtime dependency) | `cli/` | Installs from npm. Gives you an interactive shell, local-editor workflow, verified file transfers, live console stream, and monitoring dashboards. |

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

1. Drop `CreeperCLI-1.1.0.jar` into the server or proxy's `plugins/` folder and restart.
2. Create a user from the server console: `/creepercli user add steve <strong-password>`.
3. Install the CLI on your computer: `npm install -g creepercli`.
4. Connect: `creepercli login --host <server-ip> --port 45678`. The CLI drops you into the interactive shell.

Full walkthrough: [Getting started](https://demonz-development.github.io/creepercli/getting-started/).

## Read the security notes first

The plugin binds `127.0.0.1:45678` by default because the protocol is not encrypted.

- Reach it through an SSH tunnel: `ssh -N -L 45678:127.0.0.1:45678 user@server`. Use `0.0.0.0` only behind a trusted private network, encrypted tunnel, or VPN.
- Run the server as a non-root OS user. CreeperCLI is a full admin channel.
- Enable 2FA with `creepercli totp setup`. The limiter and fail2ban only slow attackers; 2FA stops credential theft.

The full threat model and hardening checklist is in [Security](https://demonz-development.github.io/creepercli/security/).

## Documentation

The docs at [demonz-development.github.io/creepercli](https://demonz-development.github.io/creepercli/) are the single source of truth.

| Page | Covers |
|---|---|
| [Getting started](https://demonz-development.github.io/creepercli/getting-started/) | Install, first user, first login, SSH tunnel |
| [CLI commands](https://demonz-development.github.io/creepercli/cli-commands/) | Every command, flag, and example |
| [Console commands](https://demonz-development.github.io/creepercli/console-commands/) | `/creepercli` administration |
| [Configuration](https://demonz-development.github.io/creepercli/configuration/) | Every `config.yml` key, auto-migration |
| [Security](https://demonz-development.github.io/creepercli/security/) | Threat model, 2FA, sandbox, rate limits |
| [Protocol](https://demonz-development.github.io/creepercli/protocol/) | Wire format, actions, events, error codes |
| [Plugin API](https://demonz-development.github.io/creepercli/plugin-api/) | Extension API for Paper/Spigot developers |
| [Architecture](https://demonz-development.github.io/creepercli/architecture/) | Threading model, sandbox design, extensions |
| [Advanced](https://demonz-development.github.io/creepercli/advanced/) | Editing, sync, monitoring, scripting |
| [Troubleshooting](https://demonz-development.github.io/creepercli/troubleshooting/) | Common errors, FAQ, tests |

Ready-to-post listings for Modrinth, SpigotMC, Hangar, CurseForge, npm, and GitHub Releases live in [`marketplace/`](marketplace/).

## Development

- Plugin: JDK 21 and Maven. Run `mvn -B clean verify` in `plugin/`.
- CLI: Node.js 18+. Run `npm ci`, `npm test`, and `npm pack --dry-run` in `cli/`.

## Releases

SemVer with `v*` tags. The plugin builds and the CLI publishes to npm on tag. See [CHANGELOG.md](CHANGELOG.md) and [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
