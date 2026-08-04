# Hangar listing

- URL: https://hangar.papermc.io/DemonZ-Development/CreeperCLI
- Platforms: Paper (and Spigot compatible), 1.21.x, Java 21
- License: Apache 2.0

## Description

CreeperCLI gives you a terminal into your Minecraft server. A plugin runs on the server, a CLI runs on your machine, they talk over TCP.

### Install

1. Drop `CreeperCLI-1.0.0.jar` into `plugins/` and restart.
2. Server console: `/creepercli user add <name> <password>`
3. Your machine: `npm install -g creepercli`
4. Connect: `creepercli login --host <ip> --port 45678`

### Commands

| Command | What it does |
|---|---|
| `creepercli ls <path>` | List directory |
| `creepercli cat <path>` | Read file |
| `creepercli edit <path>` | Open file in your $EDITOR, push on save |
| `creepercli cpush <local> <remote>` | Upload a file (SHA-256 verified) |
| `creepercli cpull <remote> <local>` | Download a file |
| `creepercli csync <remote> <local>` | Two-way sync a directory |
| `creepercli exec <command>` | Run a console command (allowlisted) |
| `creepercli log --grep <pattern>` | Stream console with grep |
| `creepercli stats` | Server memory, disk, uptime |
| `creepercli tps` | TPS 1m/5m/15m |
| `creepercli top` | Live process monitor |
| `creepercli repl` | Interactive shell |
| `creepercli passwd` | Change password |
| `creepercli totp setup` | Enable 2FA |
| `creepercli logout` | Kill session |

### Server console commands

| Command | What it does |
|---|---|
| `/creepercli user add <name> <password>` | Create a user |
| `/creepercli user remove <name>` | Delete a user |
| `/creepercli user list` | List users |
| `/creepercli status` | Show plugin status, banned IPs |
| `/creepercli reload` | Reload config |
| `/creepercli update` | Check for updates |

### Security

Bcrypt auth, optional 2FA, 15-minute session timeouts, IP-bound tokens, per-IP login throttling, fail2ban, symlink/`..` escape rejection, append-only audit log.

Default bind is `0.0.0.0:45678` for hosting panels. Set `network.host: "127.0.0.1"` in `config.yml` and connect through an SSH tunnel: `ssh -N -L 45678:127.0.0.1:45678 user@server`

### Config

All settings in `plugins/CreeperCLI/config.yml`. Auto-migrates on upgrade. Key options: `network.host`, `network.port`, `auth.session-timeout-minutes`, `sandbox.server-root`, `exec.allowlist`, `limits.commands-per-second`.

Anonymous usage metrics via bStats. Disable in `plugins/bStats/config.yml`.

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool.
