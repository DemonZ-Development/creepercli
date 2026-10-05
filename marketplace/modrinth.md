CreeperCLI lets you manage your Minecraft server from your terminal. Install the plugin on your server and the Node.js CLI on your computer, then connect over TCP.

Browse server files, open a config in your editor, and upload your changes when you're done. You can also transfer files, sync folders, run console commands, and watch the server log. TPS, memory, and disk stats are available from the same terminal.

## Install

1. Put `CreeperCLI-1.0.0.jar` in `plugins/` and restart.
2. Create a login: `/creepercli user add <name> <password>`
3. Install the CLI: `npm install -g creepercli`
4. Log in: `creepercli login --host <ip> --port 45678`

### Commands

**Simple CLI commands:**

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

The REPL saves your command history in `~/.creepercli/history`, remembers your current directory between commands, and supports tab completion for paths and command names.

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

To edit a server config, run `creepercli edit plugins/WorldGuard/config.yml`.

The file opens in your configured editor. Make your changes, save, and close the editor. CreeperCLI uploads the updated file and reports how many lines changed. It holds an edit lock while you work.

To save a copy of a server file on your computer, run `creepercli cpull server.properties ./server.properties.backup`.

To upload a local file, run `creepercli cpush ./config.yml plugins/WorldGuard/config.yml`.

To sync a server folder with a local folder, run `creepercli csync plugins/WorldGuard ./config-templates`. Sync works in both directions and asks before transferring files. Add `--yes` to skip those prompts.

To watch warnings and errors while saving them locally, run `creepercli log --grep 'ERROR|WARN' | tee console.log`.


### Multi-server

The CLI stores one session per host:port in `~/.creepercli/creds`, so you can hold sessions to several servers at once:

```
creepercli login --host 127.0.0.1 --port 45678
creepercli login --host 127.0.0.1 --port 45679
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
| `E_CHECKSUM_MISMATCH` | Transfer failed verification. The partial file is removed; retry. |

## Security

The TCP port is like a door into your server for the CLI, so it stays locked down. Passwords are bcrypt-hashed, 2FA is optional but available, sessions expire after 15 idle minutes, and they stay bound to the IP that started them. The plugin throttles failed logins and bans IPs that keep failing. File access is jailed to the server folder, so `..` and symlink tricks can't escape it. Every action lands in an append-only audit log.

By default, it listens on `0.0.0.0:45678`. If you have SSH access to the machine, set `network.host` to `127.0.0.1` and connect through an SSH tunnel:

```
ssh -N -L 45678:127.0.0.1:45678 user@server
```

Run the server as a non-root OS user. CreeperCLI is a full admin channel: file access and console are included.

### Settings

Everything lives in `plugins/CreeperCLI/config.yml`. When you update the plugin, missing keys get added on their own, so your config survives upgrades. The ones people touch most:

| Key | What it controls | Default |
|---|---|---|
| `network.host` | Bind address | `0.0.0.0` |
| `network.port` | TCP port | `45678` |
| `auth.session-timeout-minutes` | Idle session expiry | `15` |
| `sandbox.server-root` | Folder the sandbox jails in | `.` |
| `exec.allowlist` | Console commands the CLI can run | `list`, `whitelist *`, `say *`, `restart` |
| `limits.commands-per-second` | Per-session rate limit | `30` |

CreeperCLI gives you remote admin access to a Minecraft server. You're responsible for how you use it; the creator isn't liable for misuse. If you need help setting it up or troubleshooting, ask in our Discord server.
