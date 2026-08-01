# Getting started

This guide takes you from an empty server to a working `creepercli` session, step by step.

## Prerequisites

| Component | Requirement |
|---|---|
| Minecraft server | Paper 1.21.x (Java Edition) |
| Java | 21 or newer (same JVM as Paper) |
| CLI machine | Node.js 18+ (any OS) — no npm packages needed |
| Network | CLI and server can reach each other (see *SSH tunnel* below) |

## Step 1 — Install the plugin

1. Build it (requires Maven + JDK 21) or download a release jar:

   ```bash
   cd plugin
   mvn -B package -DskipTests
   # produces plugin/target/CreeperCLI-1.0.0.jar
   ```

2. Copy the jar into your server's `plugins/` folder:

   ```bash
   cp plugin/target/CreeperCLI-1.0.0.jar /path/to/server/plugins/
   ```

3. Restart the server. On first boot the plugin:

   - creates `plugins/CreeperCLI/config.yml` (defaults, see [configuration.md](configuration.md));
   - binds the TCP listener (default `127.0.0.1:45678`);
   - prints `CreeperCLI enabled. TCP listener on 127.0.0.1:45678`.

## Step 2 — Create your first user

From the **server console** (or in-game as operator):

```
/creepercli user add alice 'a-strong-password-123'
```

Rules enforced right now:

- password at least 8 characters;
- password must not equal the username.

Users are stored in `plugins/CreeperCLI/creepercli-users.yml` as **bcrypt hashes (cost 12)** —
never plaintext. TOTP secrets are stored in the same file (only if the user enables 2FA).

See [console-commands.md](console-commands.md) for `list`, `remove`, `reload`, `status`.

## Step 3 — Install and configure the CLI

The CLI is plain Node.js; the `bin/creepercli.js` entry point is what you call:

```bash
# convenience (optional)
alias creepercli="node /home/you/creepercli/cli/bin/creepercli.js"

# check it runs
creepercli --help
```

CLI preferences live in `~/.creepercli/config.json` (created on demand). Defaults:

```json
{ "host": "127.0.0.1", "port": 45678, "editor": "vi", "topRefreshMs": 2000 }
```

Every command can override host/port with `--host` / `--port`:

```bash
creepercli ls / --host 192.168.1.20 --port 45678
```

## Step 4 — Log in

```bash
creepercli login --host 127.0.0.1 --port 45678
```

You'll be prompted for username and password (hidden input). If the user has TOTP enabled you'll
be asked for the 6-digit code.

On success the session token is stored in `~/.creepercli/creds` (mode `0600`, keyed to
host+port). Subsequent commands reuse it automatically via `auth.resume`; you usually **never
need to log in again** until the session expires (default 15 minutes of inactivity) or you log out.

```bash
creepercli whoami          # shows session info
creepercli logout          # invalidates the token on the server and clears creds
```

> **Security note**: the token file is `0600` perms, but the credential is still a bearer token.
> Do not run the CLI on shared accounts.

## Step 5 — First commands

```bash
creepercli ping            # "pong"
creepercli pwd             # your current directory on the server
creepercli ls /            # list the server root
creepercli cat server.properties
creepercli stats           # server CPU / RAM / disk
creepercli tps             # TPS 1m/5m/15m
```

File commands work inside a **sandbox**: the jail root is the server root directory
(`sandbox.server-root` in config.yml, empty = server root). You cannot escape it with `..`,
absolute paths outside the root, or symlinks pointing out — attempts return `E_PATH_ESCAPE`.

## Step 6 — Reach a remote server (SSH tunnel)

The default bind is `127.0.0.1` — a remote server's port is **not** reachable from outside.
Open an SSH tunnel and point the CLI at localhost:

```bash
ssh -N -L 45678:127.0.0.1:45678 youruser@your-server-ip
creepercli login --host 127.0.0.1 --port 45678
```

If you must bind the listener to the LAN (not recommended), set `network.host` and make sure
your firewall restricts the port; all auth, rate limiting and fail2ban still apply.

## What's next

- Every command + flag: [cli-commands.md](cli-commands.md)
- Understanding the security model: [security.md](security.md)
- Editing files, syncing directories, monitoring: [advanced.md](advanced.md)
- When something breaks: [troubleshooting.md](troubleshooting.md)
