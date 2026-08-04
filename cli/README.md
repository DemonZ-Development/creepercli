# CreeperCLI

> Remote administration CLI for Paper and Spigot Minecraft servers.

[![npm version](https://img.shields.io/npm/v/creepercli.svg)](https://www.npmjs.com/package/creepercli)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Discord](https://img.shields.io/badge/chat-Discord-5865F2.svg)](https://discord.com/invite/zCkE44hsBR)

`creepercli` is the client half of CreeperCLI. It talks to the CreeperCLI Paper plugin over a TCP socket and gives you a remote shell: sandboxed filesystem access, console commands through an allowlist, live logs, verified transfers, and monitoring.

The plugin is required. Install it on the server, then create a user from the server console:

```
/creepercli user add steve <strong-password>
```

## Install

```bash
npm install -g creepercli
```

## Use

```bash
creepercli login --host <server-ip> --port 45678
```

Login verifies your password (and a TOTP code if the account has 2FA), stores the session in `~/.creepercli/creds`, and drops you into the interactive shell:

```text
Authenticated as admin. Session valid for 15 minutes.
Entering interactive REPL shell (type "help" for commands, "exit" or "q" to quit)...

admin@server:/> stats
Uptime:       1d 4h 12m
CPU load:     0.15   process: 1.0%
Memory heap:  494.1M / 680.0M (max 8.0G)
Disk (root):  40.3G / 192.7G (usable 152.4G)
Java:         25.0.3 (2 cores)

admin@server:/> tps
TPS: 1m 20.0 | 5m 20.0 | 15m 20.0  [####################]  tick 50ms

admin@server:/> q
```

One-shot mode works for scripts: `creepercli ls /plugins`, `creepercli exec list`, `creepercli cpush ./config.yml /config.yml`.

## Commands

| Command | What it does |
|---|---|
| `login [--host H] [--port P]` | Authenticate and enter the REPL |
| `logout` / `whoami` | End the session / show session info |
| `passwd` | Change password, invalidate other sessions |
| `totp setup` / `totp disable` | Turn 2FA on / off |
| `stats` / `tps` | CPU, RAM, disk, Java / ticks per second |
| `top` | Live dashboard (press `q` to quit) |
| `log [--grep regex]` | Stream the server console live |
| `exec <command>` | Run an allowlisted console command |
| `say <message>` / `restart` | Shorthand for `exec say ...` / `exec restart` |
| `ls`, `cd`, `cat`, `edit`, `grep`, `find`, `tree`, `cp`, `mv`, `rm`, `head`, `tail`, `wc` | Sandboxed filesystem access |
| `cpush <local> <remote>` | Upload with SHA-256 verification |
| `cpull <remote> <local>` | Download with SHA-256 verification |
| `csync <remote> <local>` | Two-way directory sync |

## Security

- The server-side sandbox blocks `..`, symlink, and absolute-path escapes.
- Passwords are bcrypt-hashed (cost 12). Sessions expire after 15 idle minutes and bind to your IP.
- Only allowlisted console commands run (`list`, `say *`, `whitelist *`, `restart` by default).
- The plugin binds `0.0.0.0` by default so hosted panels work. On a machine you control, set `network.host: "127.0.0.1"` and connect through an SSH tunnel.

The full threat model lives in the project wiki: [github.com/DemonZ-Development/creepercli/wiki](https://github.com/DemonZ-Development/creepercli/wiki).

## Links

- GitHub: [DemonZ-Development/creepercli](https://github.com/DemonZ-Development/creepercli)
- Modrinth: [modrinth.com/project/creepercli](https://modrinth.com/project/creepercli)
- Discord: [discord.com/invite/zCkE44hsBR](https://discord.com/invite/zCkE44hsBR)

## License

Apache License 2.0. See [LICENSE](LICENSE).