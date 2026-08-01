# CreeperCLI Wiki

CreeperCLI is a remote administration tool for Minecraft (Paper 1.21+): a Java 21 plugin that runs
a hardened TCP admin service inside your server, plus a Node.js CLI (`creepercli`) you run from
your computer. Everything is designed to be safe to expose only through an SSH tunnel — but every
layer (auth, sandbox, rate limits, allowlists, auditing) works even if the port is open on the LAN.

## Architecture at a glance

```
Your computer                         Minecraft server (Paper 1.21+)
┌─────────────────────┐               ┌──────────────────────────────────────┐
│ creepercli CLI      │  TCP / JSON   │ CreeperCLI plugin                    │
│ (Node.js, no deps)  │◄────────────►│  TcpServer  (port 45678 by default)  │
│  - one-shot cmds    │  newline-     │  CommandRouter (auth + actions)     │
│  - REPL             │  delimited    │  Sandbox  (server-root jail)        │
│  - transfers        │  messages     │  ExecAllowlist  (console commands)  │
└─────────────────────┘               │  AuditLogger  (append-only)         │
                                      └──────────────────────────────────────┘
```

- **Server**: Java 21, Paper 1.21 API. The plugin binds a TCP socket (default `127.0.0.1:45678`)
  and speaks a tiny JSON-lines protocol (see [protocol.md](protocol.md)).
- **CLI**: plain Node.js (no npm dependencies — zero-install beyond Node 18+). Sends one command
  per invocation (one-shot mode) or runs an interactive REPL.
- **No console commands are exposed over the wire except those you allowlist.** File access is
  jailed inside the server's root directory. Logins are bcrypt + optional TOTP 2FA.

## Table of contents

| Document | What it covers |
|---|---|
| [Getting started](getting-started.md) | Install, first user, first login, SSH tunnel, first commands |
| [CLI commands](cli-commands.md) | Every command: usage, flags, examples (the big reference) |
| [Console commands](console-commands.md) | `/creepercli` in-game/console administration |
| [Configuration](configuration.md) | Every `config.yml` key and default |
| [Security](security.md) | Threat model, auth, 2FA, sessions, rate limits, fail2ban, sandbox, allowlist, audit |
| [Protocol](protocol.md) | Wire format, actions, events, error codes |
| [Advanced usage](advanced.md) | `edit` + `$EDITOR`, sync workflows, monitoring, automation/scripting |
| [Troubleshooting](troubleshooting.md) | Common errors, fixes, FAQ, the E2E test harness |

## Quick start (TL;DR)

```bash
# On the server (console):
/creepercli user add alice 'a-strong-password-123'

# On your computer:
creepercli login --host 127.0.0.1 --port 45678   # type alice's credentials
creepercli ls /                                  # list server root
creepercli stats                                 # CPU/RAM/disk
creepercli exec list                             # Minecraft "list" command (allowlisted)
```

See [getting-started.md](getting-started.md) for the full walkthrough.

## Repository layout

```
plugin/   Java 21 Paper plugin (Maven, build: mvn package)
cli/      Node.js CLI (bin/creepercli.js, src/)
wiki/     this documentation
```

## Version

- Plugin: `1.0.0`, protocol version `1`.
- Requirements: Java 21, Paper 1.21.x (spigot/vanilla APIs lacking `ServerCommandSender` etc.
  are not supported — use Paper).
