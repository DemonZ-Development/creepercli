# CurseForge listing

- URL: https://www.curseforge.com/minecraft/bukkit-plugins/
- Project type: Bukkit plugin
- Supported: 1.21.x, Java 21
- License: Apache 2.0

## Description

CreeperCLI is two things: a plugin and a command-line app. The plugin runs on the server, the CLI runs on your machine, and they talk over TCP. No web panel, nothing to learn in-game.

You get a real terminal into the server. Browse files. Open one in your own editor and push changes back. Push, pull, and sync folders with checksums. Run console commands from your shell. Watch the console stream by, check TPS, keep an eye on memory and disk.

### Install

1. Put `CreeperCLI-1.0.0.jar` in `plugins/` and restart.
2. Create a login: `/creepercli user add <name> <password>`
3. Install the CLI: `npm install -g creepercli`
4. Log in: `creepercli login --host <ip> --port 45678`

### Commands

One command at a time, straight from your terminal:

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

Or drop the prefix and use the shell instead:

```
creepercli repl
> ls /
> cat server.properties
> exec list
> stats
> exit
```

### Server-side commands

| Command | What it does |
|---|---|
| `/creepercli user add <name> <password>` | Create a user |
| `/creepercli user remove <name>` | Delete a user |
| `/creepercli user list` | List users |
| `/creepercli status` | Plugin status, banned IPs |
| `/creepercli reload` | Reload config |
| `/creepercli update` | Check for updates |

### Security

The port is a door into your server, so it stays locked down. Passwords are bcrypt-hashed, 2FA is optional but available, sessions expire after 15 idle minutes and stay bound to the IP that started them. The plugin throttles failed logins and bans IPs that keep failing. File access is jailed to the server folder, so `..` and symlink tricks can't escape it. Every action lands in an append-only audit log.

By default it binds `0.0.0.0:45678`, which works on hosting panels as-is. If the machine is yours, set `network.host: "127.0.0.1"` and reach it through an SSH tunnel:

```
ssh -N -L 45678:127.0.0.1:45678 user@server
```

### Settings

Everything lives in `plugins/CreeperCLI/config.yml`. When you update the plugin, missing keys get added on their own, so your config survives upgrades. The ones people touch most: `network.host`, `network.port`, `auth.session-timeout-minutes`, `sandbox.server-root`, `exec.allowlist`, and `limits.commands-per-second`.

The plugin sends basic usage stats to bStats: versions, OS, player count, online-mode. No files, commands, or usernames. Shut it off in `plugins/bStats/config.yml` if you'd rather not.

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool.