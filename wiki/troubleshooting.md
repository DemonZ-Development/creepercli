# Troubleshooting

## Connection errors

### `Error: Cannot connect to <ip>:45678 — ECONNREFUSED`

The plugin is not listening where you looked.

1. Check the plugin loaded: the console shows `CreeperCLI enabled (config v1). TCP listener on <host>:<port>`.
2. Check the listener: `ss -tlnp | grep 45678` on the server.
3. The default bind is `0.0.0.0`, so a refused connection usually means a wrong port, a wrong IP, or a firewall. On your own machine, prefer `network.host: "127.0.0.1"` plus an SSH tunnel (`ssh -N -L 45678:127.0.0.1:45678 user@server`).
4. `network.port` and `network.host` changes require a restart, not `/creepercli reload`.

### `Error: Connection closed by server` or `Request timed out (auth.login)`

You are talking to the Minecraft game port, not the CreeperCLI listener.

1. The game port (e.g. `30048`) speaks Minecraft's protocol, not JSON lines, and drops the connection.
2. A port collision makes the plugin fail to bind: `Failed to bind TCP ... Address already in use`. Make sure `network.port` differs from `server-port` in `server.properties`.
3. On hosted servers (Pterodactyl, Apex, PebbleHost), allocate an additional port (e.g. `30049`), set `network.port: 30049`, restart, and connect with `creepercli login --host <ip> --port 30049`.

### `Error: Not authenticated and stdin is not a TTY. Run "creepercli login" first.`

The CLI has no stored session and input was piped. Either log in once interactively, or provide credentials on stdin:

```bash
printf 'alice\npassword\n' | creepercli ls / --host 127.0.0.1 --port 45678
```

## Session and auth errors

### `Error: Session expired, please login again (E_SESSION_EXPIRED)`

- The 15-minute inactivity timeout elapsed. Run `creepercli login` again.
- The password changed elsewhere, which invalidates other sessions.
- The token was presented from a different IP; tokens are IP-bound.

### `Error: Command rate limit exceeded [slow down] (E_RATE_LIMITED)`

Fast scripted loops exceed `limits.commands-per-second` (default 30/s per session). Raise it in `config.yml` and `/creepercli reload`.

### `Error: TOTP code required (E_TOTP_REQUIRED)`

The account has 2FA. Enter the 6-digit code when prompted. Lost the device? An admin resets 2FA by running `/creepercli user add <name> <newpass>` again, which replaces the account and clears the TOTP secret.

### `Error: TOTP setup expired, restart setup`

`totp setup` secrets are valid for 10 minutes. Run it again.

## File and command errors

### `Error: Remote file exists (pass force to overwrite) (E_ALREADY_EXISTS)`

`cpush` refuses to overwrite. Pass `--force` or remove the remote file first.

### `Error: Locked: ... (E_LOCKED)` when editing

Someone else is editing the file, or a crashed session left a lock. Wait for the 5-minute TTL; a server restart clears all locks.

### `Error: path blocked by the server sandbox (E_PATH_ESCAPE)`

You tried to read or write outside the jail root. Absolute paths are jail-relative: `cat /server.properties` reads `<root>/server.properties`.

### `Error: Command not in allowlist (E_ALLOWLIST_DENIED)`

The command is not in `exec.allowlist`. Add a pattern (`list`, `say *`, `whitelist *`) and `/creepercli reload`. Never add a bare `*`.

### `Error: Command reported failure` after `exec ...`

The command ran, since it is allowlisted, but reported failure (for example `whitelist add x` when x exists). Exit code is 1.

## Server-side checks

### Plugin not enabling?

The console shows one of:

- `Failed to bind TCP ...` — port in use or host invalid. Change `network.*` and restart.
- `Cannot initialize sandbox root: ...` — `sandbox.server-root` is invalid or unreadable.
- A Java version error — run Paper on Java 21+.

### Where's the audit trail?

`plugins/CreeperCLI/creepercli-audit.log`. Every action, one line:

```
2026-08-01T19:49:35.188Z | 127.0.0.1 | test | fs.edit.push | {"path":"..."}
```

### `/creepercli status` shows a banned IP

fail2ban counted `auth.fail2ban.max-failures` failed logins. The ban lifts after `ban-minutes`. Check the audit log for the failing usernames and IPs.

## FAQ

**Is the connection encrypted?**
Not yet; TLS is on the roadmap. The intended path is an SSH tunnel, which encrypts the whole connection. Tokens are 128-bit random, IP-bound, and expire after 15 idle minutes.

**Can a user read files outside the server folder?**
No. The sandbox resolves everything inside `sandbox.server-root` and blocks `..` and symlink escapes.

**Can the CLI restart my server?**
Only if `restart` is in `exec.allowlist`. It is by default; remove it if you don't want that.

**How do I invalidate all sessions?**
`creepercli passwd` invalidates all other sessions, `creepercli logout` ends the current one, and a server restart clears the in-memory session store.

**Multiple admins on one server?**
Create separate users. Edit locks prevent file conflicts; the audit log attributes actions to individuals.

**Does the CLI need npm dependencies?**
It has one runtime dependency, `qrcode-terminal`, and runs on Node.js 18+.

**What does the plugin send to bStats?**
Anonymous usage metrics: plugin and server versions, Java, OS, player count, online-mode, plus the charts listed in [configuration.md](configuration.md#usage-metrics-bstats). Disable in `plugins/bStats/config.yml`.

## Running the tests

```bash
cd plugin && mvn test      # PathSanitizer suite, 53 tests
cd cli && npm test         # CLI tests
```

Note: the four symlink tests skip on Windows and run on Linux CI.