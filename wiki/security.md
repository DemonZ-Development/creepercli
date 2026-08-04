# Security

CreeperCLI assumes the TCP port may be reachable by strangers: LAN exposure, a misconfigured firewall, a panel that opens the port. Every control below is enforced server-side and cannot be bypassed by a custom client.

## Before you deploy

1. **Run the server as a non-root OS user.** CreeperCLI is a full admin channel: file access, console commands, restart. Compromise of it is compromise of the host.
2. **Decide how the port is reached.** The default bind is `0.0.0.0:45678`, chosen so hosted panels work out of the box. On a machine you control, set `network.host: "127.0.0.1"` in `config.yml`, restart, and reach the server through an SSH tunnel. Do not open the port to the public internet.
3. **Enable 2FA** with `creepercli totp setup` for every admin account.
4. **Watch the audit log** (`plugins/CreeperCLI/creepercli-audit.log`) and the Banned IPs counter in `/creepercli status`.

## Threat model

| Threat | Mitigation |
|---|---|
| Password sniffing on the wire | Passwords never travel after login; sessions use 128-bit tokens. Transport is not encrypted yet: tunnel with SSH, or wait for TLS in a future release. |
| Brute force / credential stuffing | bcrypt cost 12 (about 0.3 s per verify), per-IP login limiter, fail2ban |
| Token theft / reuse | 15-minute inactivity timeout, token bound to the login IP, `logout` invalidates, `passwd` invalidates other sessions |
| Reading files outside the server | Sandbox jail: `..`, symlink, and absolute-path escapes blocked (`E_PATH_ESCAPE`) |
| Running arbitrary console commands | Default-deny allowlist only |
| API flooding | Per-session token bucket (30/s), connection cap, payload cap |
| Conflicting edits | Per-file locks with 5-minute TTL |
| No accountability | Append-only audit log with timestamp, IP, user, action, params |

## Authentication

- Users live in `creepercli-users.yml` as bcrypt hashes (cost 12).
- Login: password verify, then a TOTP code if the account has 2FA, then a session token.
- TOTP is RFC 6238, 6-digit, 30-second period. `totp setup` secrets expire after 10 minutes; `totp disable` needs a password and a valid code.
- A nonexistent user and a wrong password both return `E_AUTH_FAILED`. The server never reveals which failed.

## Sessions

- 128-bit tokens, held in memory only (`SessionManager`).
- Bound to the login IP. Presenting a token from a different IP ends the session.
- 15-minute inactivity timeout; a sweeper runs every minute.
- `auth.resume` revalidates the token on every CLI run, so scripts skip the password.
- `logout` revokes the token server-side; `passwd` revokes every other session of that user.
- The CLI stores the token in `~/.creepercli/creds`, mode 0600, keyed per host and port.

## Rate limiting and fail2ban

1. **Login limiter**: per-IP sliding window, default 3 failures / 5 minutes → `E_RATE_LIMITED`.
2. **fail2ban**: more failures within its window (default 3 / 10 minutes) bans the IP for 10 minutes → `E_BANNED` with the remaining time. A successful login clears the failure history.
3. **Command rate limit**: token bucket per session, default 30/s → `E_RATE_LIMITED` on bursts.

## Network

- Default bind `0.0.0.0:45678`. On a machine you control, set `network.host` to `127.0.0.1` and tunnel. Changing host or port requires a restart.
- `max-connections` (16): extra sockets get `E_SERVER_FULL` and close.
- `max-payload-bytes` (10 MiB): oversized frames are rejected before processing.
- Every response carries `v: 1`; unknown frames are ignored.

## File sandbox

`PathSanitizer` resolves every path against the jail root (`sandbox.server-root`, default the server directory):

- `..` cannot escape the jail → `E_PATH_ESCAPE`.
- Absolute paths are jail-relative: `/etc/passwd` reads `<root>/etc/passwd`.
- Symlinks are resolved to their real target and checked against the jail. A link to the host's `/etc/passwd` is rejected.
- NUL bytes, drive letters, and backslashes are normalized or rejected.

## Console execution

Executed commands pass the allowlist first, then dispatch on the main thread as a proxied `ConsoleCommandSender`. Output is captured and returned to the client. Pattern matching:

```
list          matches "list" only
whitelist *   matches "whitelist add bob", "whitelist list"
say *         matches "say hello everyone"
restart       matches "restart" only
```

Anything else gets `E_ALLOWLIST_DENIED`. Do not add a bare `*`.

## Transfers

- SHA-256 verified on both ends of push and pull; a mismatch returns `E_CHECKSUM_MISMATCH` and removes the partial file.
- Chunked streaming, capped at `limits.max-transfer-bytes` (1 GiB default).
- A dropped connection aborts the transfer and cleans up server-side.

## Editing

- `edit` takes a per-file lock; concurrent editors get `E_LOCKED`. Locks release on disconnect, unlock, or the 5-minute TTL.
- Pushes carry a SHA-256 of the new content; the server rejects mismatches.

## Audit log

Every authenticated action appends to `creepercli-audit.log`:

```
2026-08-01T19:49:35.188Z | 127.0.0.1 | test | fs.edit.push | {"path":"/e2e-test/sp-copy.txt",...}
```

Format: `timestamp | source-ip | username | action | params`. Rotates at `audit.max-mb` (10 MiB). Failed logins are logged with the attempted username, which makes brute-force patterns visible even before fail2ban triggers.

## Roadmap (deferred)

Scoped beyond v1.0.0, deliberately not in the release:

- TLS transport with certificate pinning
- Idle-connection timeout and per-IP connection caps
- Session token rotation on resume
- Persistent fail2ban bans
- Password policy options and forced 2FA
- Per-user roles (`admin` / `member`)
