# Security

CreeperCLI is built on the assumption that the TCP port may be reachable by strangers
(LAN exposure, misconfigured firewall, bad tunnels). Every layer below is therefore enforced
**server-side** and cannot be bypassed by a custom client.

## Threat model

| Threat | Mitigations |
|---|---|
| Cleartext sniffing of passwords/tokens | Session tokens only (passwords never sent after login); TOTP; keep the port loopback + tunnel. TLS is on the roadmap. |
| Password brute force / credential stuffing | bcrypt (cost 12) server-side; per-IP login limiter; fail2ban. |
| Token theft / reuse | 15-minute session inactivity timeout; token bound to IP; `logout` invalidates; password change invalidates other sessions. |
| Unauthorized file access | Path sandbox (jail root, `..`/symlink/absolute-path escape blocked); `E_PATH_ESCAPE`. |
| Unauthorized Minecraft commands | `exec` allowlist only — no arbitrary console access. |
| API flooding | Per-session command rate limit; global connection cap; payload cap. |
| Concurrent edit conflicts | Per-file edit locks with TTL. |
| Accountability | Append-only audit log of every action (who, from which IP, when, what params). |

## Authentication

- Users live in `creepercli-users.yml` as **bcrypt hashes** (cost 12 — ~0.3 s per verify, which
  alone slows down brute force dramatically).
- Login flow: password verify → (if enabled) TOTP code verify → session issued.
- TOTP: standard RFC 6238 6-digit codes, 30-second period. `totp setup` secrets expire after
  10 minutes; `totp disable` requires password + valid code.
- **Timing-safe failures**: a nonexistent user and a wrong password produce the same
  `E_AUTH_FAILED` — the server does not reveal which part failed.

## Sessions

- Random 128-bit tokens (`UUID`-based), stored **only** on the server in memory
  (`SessionManager`).
- Bound to the login IP: presenting a token from a different IP invalidates the session.
- Inactivity timeout (default 15 min) — `sweep` runs every minute.
- `auth.resume` re-validates the token on every CLI invocation (no password re-entry).
- `logout` invalidates the token server-side; `passwd` invalidates **all** other sessions of
  that user.
- The CLI stores the token in `~/.creepercli/creds` with mode `0600`, keyed to host+port.

## Rate limiting & abuse protection

1. **Login limiter** (`auth.login-limiter`): per-IP sliding window (default 3 failures / 5 min)
   → `E_RATE_LIMITED`.
2. **fail2ban** (`auth.fail2ban`): after the failure threshold within the window (default
   3/10 min), the IP is banned for 10 minutes → `E_BANNED` with remaining seconds. Successful
   login clears the IP's failure history.
3. **Command rate limit** (`limits.commands-per-second`): token bucket **per session**
   (default 30/s). One-shot CLI invocations do login+command, so scripted usage is comfortable;
   hostile bursts get `E_RATE_LIMITED`.

## Network hardening

- Binds to `127.0.0.1` by default — the listener is not remotely reachable without a tunnel.
- `max-connections` (16): excess sockets get a structured `E_SERVER_FULL` reply and are closed.
- `max-payload-bytes` (10 MiB): oversized frames are rejected before processing.
- Protocol version check: unknown/bad frames are ignored; every response is JSON with `v: 1`.

## File sandbox

`PathSanitizer` resolves every path against the jail root (`sandbox.server-root`, default the
server root):

- `..` cannot escape the jail (`E_PATH_ESCAPE`);
- absolute paths are jail-relative (a request for `/etc/passwd` is treated as
  `<root>/etc/passwd`);
- symlinks are resolved with `NOFOLLOW` where possible and verified to stay inside;
- the jail root itself is never escapable, by design or by accident.

## Console command execution

The API never maps to `dispatchCommand` freely. `ExecAllowlist` checks patterns against the
allowlist (`exec.allowlist`), e.g.:

```
list          → matches exactly "list"
whitelist *   → matches "whitelist add bob", "whitelist list"
say *         → matches "say hello everyone"
restart       → matches only "restart"
```

Non-matching commands → `E_ALLOWLIST_DENIED`. Wildcards apply token-by-token; there is no
`*` alone entry in the default allowlist.

Executed commands are dispatched as a proxied `ConsoleCommandSender` (no player permissions),
with output captured and returned to the client. Audit logs record every execution.

## Transfer integrity

- SHA-256 computed locally **and** verified server-side on push finish; `cpull` verifies the
  server-provided hash against what was actually received (`E_CHECKSUM_MISMATCH` removes the
  partial file).
- Chunked streaming with server-side size caps (`max-transfer-bytes`, 1 GiB default).
- Mid-transfer connection loss aborts and cleans up server-side.

## Editing safety

- `edit` takes a per-file lock (`E_LOCKED` for concurrent editors) — locks are tied to the
  session token and released on disconnect, unlock, or TTL (5 min).
- Pushes include a SHA-256 of the new content; the server rejects mismatches.

## Audit log

Every authenticated action appends a line to `creepercli-audit.log`:

```
2026-08-01T19:49:35.188Z | 127.0.0.1 | test | fs.edit.push | {"path":"/e2e-test/sp-copy.txt",...}
```

Format: `timestamp | source-ip | username | action | params`. Rotates at `audit.max-mb`
(10 MiB default). Login failures are logged with the attempted username (useful to spot
brute-force patterns even when fail2ban hasn't triggered).

## Operational recommendations

1. **Never** expose the port directly to the internet. Use the SSH tunnel pattern.
2. Keep `plugins/` permissions tight — the users file contains bcrypt hashes and TOTP secrets.
3. Use TOTP 2FA for any admin account.
4. Prefer a restrictive `exec.allowlist`; add entries only when a workflow needs them.
5. Change passwords periodically via `passwd` (invalidates stale sessions automatically).
6. Watch `creepercli-audit.log` + `/creepercli status` (Banned IPs counter) for abuse.
7. For automation, raise `limits.commands-per-second` deliberately — don't lower the session
   timeout below what your scripts need.

## v1.1.0 roadmap (deferred)

These are scoped for the next release and are intentionally **not** in v1.0.0. v1.0.0 is
complete and tested as documented; the items below add defense-in-depth and ergonomics:

- TLS transport with certificate pinning (TOFU) so nothing on the wire is readable even
  without a tunnel.
- Idle-connection timeout and per-IP connection caps.
- Session token rotation on resume.
- Persistent fail2ban bans (survive restarts).
- Password policy options (length/complexity) and optional forced-2FA enrollment.
- Per-user roles (`admin` / `member`) with configurable action restrictions.

This page will be updated as those land.
