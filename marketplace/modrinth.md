# Modrinth listing

- URL: https://modrinth.com/project/creepercli
- Project ID: `fPKEvBZo` (already wired into the plugin's update checker)
- Summary: `Remote Minecraft server admin from your shell. File sync, console, TPS, 2FA.` (73 chars)
- Loaders: Paper, Spigot
- Game versions: 1.21.x
- Categories: server utility, server management
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
> cd plugins
> exec list
> stats
> exit
```

The REPL keeps history in `~/.creepercli/history`, sticks `cd` between commands, and supports tab completion on paths and command names.

### Server-side commands

| Command | What it does |
|---|---|
| `/creepercli user add <name> <password>` | Create a user |
| `/creepercli user remove <name>` | Delete a user |
| `/creepercli user list` | List users |
| `/creepercli status` | Plugin status, banned IPs |
| `/creepercli reload` | Reload config |
| `/creepercli update` | Check for updates |

### Workflows

Edit a server config from your own editor and push it back under a lock:

```
creepercli edit plugins/WorldGuard/config.yml
# vim opens the file, you make changes, save and quit
# CreeperCLI shows the diff and uploads the new content
```

Back up a folder to your home machine:

```
creepercli cpush world/ ~/backups/mc-world-$(date +%F) --force
creepercli cpull crash-reports/ ~/crashes/
```

Sync a config folder, weekly, with the server as the source of truth:

```
creepercli csync /etc/mc-configs ./config-templates --yes
```

Stream the console for errors:

```
creepercli log --grep 'ERROR|WARN' | tee console.log
```

Cron-friendly one-shots (each command logs in with a saved session token):

```
0 4 * * * creepercli cpush /world /backups/daily/world.tgz --force >> /var/log/creeper-backup.log 2>&1
*/5 * * * * creepercli tps | grep -q "1m 1[0-9]" && echo "TPS low: $(creepercli tps)" | mail -s "MC TPS alert" admin@example.com
```

### Multi-server

The CLI stores one session per host:port in `~/.creepercli/creds`, so you can hold sessions to several servers at once:

```
creepercli login --host 127.0.0.1 --port 45678   # survival
creepercli login --host 127.0.0.1 --port 45679   # lobby
creepercli --port 45679 exec list
```

### Common errors

| Code | What it means |
|---|---|
| `E_AUTH_FAILED` | Wrong username or password. The server won't say which. |
| `E_TOTP_REQUIRED` | Account has 2FA on. Pass a TOTP code with the login. |
| `E_BANNED` | IP banned by fail2ban. Wait it out or use a different IP. |
| `E_RATE_LIMITED` | Too many commands. Slow down or raise `limits.commands-per-second`. |
| `E_ALLOWLIST_DENIED` | That console command isn't in `exec.allowlist`. Add it and `/creepercli reload`. |
| `E_PATH_ESCAPE` | Tried to read or write outside the sandbox. Symlink and `..` escapes get rejected. |
| `E_LOCKED` | Someone else is editing that file. Wait, or run `unlock`. |
| `E_CHECKSUM_MISMATCH` | Transfer failed verification. The partial file is removed, retry. |

### Security

The port is a door into your server, so it stays locked down. Passwords are bcrypt-hashed, 2FA is optional but available, sessions expire after 15 idle minutes and stay bound to the IP that started them. The plugin throttles failed logins and bans IPs that keep failing. File access is jailed to the server folder, so `..` and symlink tricks can't escape it. Every action lands in an append-only audit log.

By default it binds `0.0.0.0:45678`, which works on hosting panels as-is. If the machine is yours, set `network.host: "127.0.0.1"` and reach it through an SSH tunnel:

```
ssh -N -L 45678:127.0.0.1:45678 user@server
```

Run the server as a non-root OS user. CreeperCLI is a full admin channel: file access and console included.

### Settings

Everything lives in `plugins/CreeperCLI/config.yml`. When you update the plugin, missing keys get added on their own, so your config survives upgrades. The ones people touch most:

| Key | What it controls | Default |
|---|---|---|
| `network.host` | Bind address | `0.0.0.0` |
| `network.port` | TCP port | `45678` |
| `auth.session-timeout-minutes` | Idle session expiry | `15` |
| `sandbox.server-root` | Folder the sandbox jails to | `.` |
| `exec.allowlist` | Console commands the CLI can run | `list`, `whitelist *`, `say *`, `restart` |
| `limits.commands-per-second` | Per-session rate limit | `30` |

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool.