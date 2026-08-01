# Troubleshooting

## Common errors and fixes

### `Error: Cannot connect to 127.0.0.1:45678 — ECONNREFUSED`

The plugin isn't listening there.

1. Check the plugin loaded: server console shows `CreeperCLI enabled. TCP listener on ...`.
2. Check the port: `ss -tlnp | grep 45678` on the server.
3. If connecting remotely, the port binds `127.0.0.1` by default — you **must** use an SSH
   tunnel (`ssh -N -L 45678:127.0.0.1:45678 user@server`) or set `network.host`.
4. `network.port`/`network.host` changes require a **restart**, not `/creepercli reload`.

### `Error: Not authenticated and stdin is not a TTY. Run "creepercli login" first.`

The CLI has no stored session and you piped input instead of using a terminal. Either:

```bash
creepercli login          # interactively, once
# or provide credentials on stdin:
printf 'alice\npassword\n' | creepercli ls / --host 127.0.0.1 --port 45678
```

### `Error: Session expired, please login again (E_SESSION_EXPIRED)`

- Session inactivity timeout (default 15 min) elapsed. Run `creepercli login` again.
- Or the password was changed elsewhere (other sessions are invalidated).
- Or the token was used from a different IP (token is IP-bound).

### `Error: Command rate limit exceeded [slow down] (E_RATE_LIMITED)`

- Scripted loops or very fast manual use exceeding `limits.commands-per-second` (default 30/s
  per session). Raise it in config.yml and `/creepercli reload`.
- Old `config.yml` files from pre-1.0 versions may carry a lower value (10) — check the file.

### `Error: Remote file exists (pass force to overwrite) (E_ALREADY_EXISTS)`

`cpush` refuses to overwrite. Pass `--force`, or remove the remote file first.

### `Error: Locked: ... (E_LOCKED)` when editing

Someone else is editing that file (or a crashed edit left a lock). Wait for them to finish or
for the 5-minute TTL; a server restart also clears locks.

### `Error: path blocked by the server sandbox (E_PATH_ESCAPE)`

You tried to read/write outside the jail root (`sandbox.server-root`). Remember absolute paths
are jail-relative: `cat /server.properties` reads `<root>/server.properties`.

### `Error: Command reported failure` after `exec ...`

The command ran (it's allowlisted) but reported failure — e.g. `whitelist add x` when x exists.
Exit code is 1.

### `Error: Command not in allowlist (E_ALLOWLIST_DENIED)`

The command isn't in `exec.allowlist`. Add it (patterns like `list`, `say *`, `whitelist *`)
and `/creepercli reload`. **Never** add a bare `*`.

### `Error: TOTP code required (E_TOTP_REQUIRED)`

The account has 2FA. Enter the 6-digit code from your authenticator when prompted. If you lost
the device, an admin can reset via `/creepercli user add <name> <newpass>` (clears TOTP).

### `Error: TOTP setup expired, restart setup`

`totp setup` secrets are only valid for 10 minutes. Run `totp setup` again.

### `Error: Connection closed (E_INTERNAL)` in piped REPL

The REPL processes piped input sequentially; if a command is still in flight when input ends,
it reports the closed connection. Put `exit` last and keep commands simple. For real-time
interactive use, run the REPL in a terminal.

## Server-side checks

### Plugin not enabling?

The console shows one of:

- `Failed to bind TCP ...` — port in use or host invalid → change `network.*` and restart.
- `Cannot initialize sandbox root: ...` — `sandbox.server-root` is invalid/unreadable.
- Java version too old → run Paper with Java 21+.

### Where's the audit trail?

`plugins/CreeperCLI/creepercli-audit.log` — every action, e.g.:

```
2026-08-01T19:49:35.188Z | 127.0.0.1 | test | fs.edit.push | {"path":"..."}
```

### `/creepercli status` shows a banned IP

`fail2ban` counted `auth.fail2ban.max-failures` failed logins. It lifts automatically after
`ban-minutes`. Check the audit log for the failing username/IPs.

## FAQ

**Is the connection encrypted?**
Not yet (roadmap: TLS). The listener binds loopback by default and the intended path is an SSH
tunnel, which encrypts the whole connection. Tokens are 128-bit random, IP-bound, and expire
after 15 idle minutes.

**Can a user read files outside the server folder?**
No — the sandbox resolves everything inside `sandbox.server-root` and blocks `..`/symlink
escapes.

**Can the CLI restart my server?**
Only if `restart` is in `exec.allowlist` (it is by default). Remove it if you don't want that.

**How do I invalidate all sessions?**
`creepercli passwd` (invalidates all other sessions), `creepercli logout` (current), or restart
the server (memory-only sessions vanish).

**Multiple admins on one server?**
Create separate users; `user list` shows them. Edit locks prevent file conflicts; audit log
attributes actions to individuals.

**Does the CLI need npm install?**
No. `node bin/creepercli.js` runs with the Node.js standard library only (Node 18+).

## The E2E test harness

For development, the repo's E2E setup runs the plugin against a real Paper 1.21.3 server in a
GitHub Codespace:

- `e2e_setup.sh` — downloads/launches Paper, installs the plugin jar, adds a `test` user,
  exposes a console FIFO (`/tmp/e2e/console.fifo`) and pid file.
- `e2e_battery.sh` — ~42 checks: auth, sessions, filesystem, transfers (push/pull/sync),
  edit + locks, exec allowlist + output capture, monitoring (stats/tps/top/log), REPL, logout.

Run:

```bash
/home/codespace/e2e_setup.sh
/home/codespace/e2e_battery.sh
# expect: PASS=42 FAIL=0
```
