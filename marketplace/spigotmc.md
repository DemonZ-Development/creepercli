# SpigotMC listing

- URL: https://www.spigotmc.org/resources/
- Supported: Spigot and Paper, 1.21.x, Java 21
- License: Apache 2.0
- Note: SpigotMC descriptions are limited to about 3,000 characters. The body below fits within it.

## Description

CreeperCLI is two things: a plugin and a command-line app. The plugin runs on the server, the CLI runs on your machine, and they talk over TCP. No web panel, nothing to learn in-game.

You get a real terminal into the server. Browse files, open one in your own editor and push changes back, push and pull with checksums, sync folders, run console commands from your shell, watch the console stream by, check TPS and stats.

### Install

1. Put `CreeperCLI-1.0.0.jar` in `plugins/` and restart.
2. Create a login: `/creepercli user add <name> <password>`
3. Install the CLI: `npm install -g creepercli`
4. Log in: `creepercli login --host <ip> --port 45678`

### Commands

| Command | What it does |
|---|---|
| `creepercli ls <path>` | List a directory |
| `creepercli cat <path>` | Show a file |
| `creepercli edit <path>` | Open a file in your editor, push it back on save |
| `creepercli cpush <local> <remote>` | Upload a file, checksum-verified |
| `creepercli cpull <remote> <local>` | Download a file |
| `creepercli csync <remote> <local>` | Sync a folder both ways |
| `creepercli exec <command>` | Run a console command, allowlist only |
| `creepercli log --grep <pattern>` | Watch the console, filtered |
| `creepercli stats` | Memory, disk, uptime |
| `creepercli tps` | TPS over 1, 5, and 15 minutes |
| `creepercli top` | Live process dashboard |
| `creepercli repl` | Interactive shell |
| `creepercli passwd` | Change your password |
| `creepercli totp setup` | Turn on 2FA |
| `creepercli logout` | End the session |

Drop the prefix in REPL mode:

```
creepercli repl
> ls /
> stats
> exit
```

### Server-side commands

`/creepercli user add|remove|list <name> <password>`, `/creepercli status`, `/creepercli reload`, `/creepercli update`.

### Security

The port is a door into your server, so it stays locked down. Passwords are bcrypt-hashed, 2FA is optional but available, sessions expire after 15 idle minutes and stay bound to the IP that started them. The plugin throttles failed logins and bans IPs that keep failing. File access is jailed to the server folder, so `..` and symlink tricks can't escape it. Every action lands in an append-only audit log.

It binds `0.0.0.0:45678` by default. Set `network.host: "127.0.0.1"` and tunnel with SSH:

```
ssh -N -L 45678:127.0.0.1:45678 user@server
```

### Settings

Everything lives in `plugins/CreeperCLI/config.yml`. Missing keys get added on upgrade. Most-touched: `network.host`, `network.port`, `auth.session-timeout-minutes`, `sandbox.server-root`, `exec.allowlist`, `limits.commands-per-second`.

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool.