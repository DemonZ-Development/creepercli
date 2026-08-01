# Security

CreeperCLI is a full remote-admin channel for your Minecraft server. Treat it as seriously as root SSH. This document describes the threat model, the built-in defenses, and the operational rules that make them effective.

## Threat model

| Threat | Mitigation |
|---|---|
| Network attacker guesses credentials | Auth limiter (3 attempts / 5 min per IP) + Fail2Ban (3 failures -> 10 min ban) + bcrypt (12 rounds) |
| Stolen password (no 2FA) | TOTP 2FA, optional per user; session tokens expire after 15 min inactivity |
| Attacker on the LAN / MITM | Token sessions are IP-bound; strong recommendation: SSH tunnel only |
| Path traversal / symlink escapes | `PathSanitizer`: normalization, real-path resolution (`toRealPath()`), nearest-existing-parent fallback, chroot-style jail (see below) |
| Abuse of console commands | Default-deny `exec` allowlist; `say`/`restart` are aliases that still pass through it |
| Command flooding / DoS | Per-session token bucket (10 cmd/s), max connections, max frame size, exec timeout |
| File conflicts / data loss | Edit locks with 5-minute TTL (auto-release), atomic writes, SHA-256 verification on transfers |
| Audit tampering | Append-only audit log; rotation at 10MB |
| Server compromise via CLI user | The jail only reaches the server root, but that is the whole server — only give accounts to trusted admins |

## Operational rules (non-negotiable)

1. **Run the server as a non-root OS user.** A root server + this plugin = full remote root. Use a dedicated `minecraft` user.
2. **Never expose the TCP port to the internet.** Keep `network.host: 127.0.0.1` and reach it via SSH tunnel:
   ```
   ssh -L 45678:127.0.0.1:45678 minecraft@server
   ```
   If you must bind a real interface, firewall it to your VPN/LAN subnet only.
3. **Keep the exec allowlist minimal.** Start with the defaults (`list`, `whitelist *`, `say *`, `restart`) and add only what you need. `op *`, `stop`, `ban-ip *`, `pardon-ip *` are deliberately not default-allowed.
4. **Enable TOTP** on every CLI account: `creepercli totp setup`.
5. **Watch the audit log** (`plugins/CreeperCLI/creepercli-audit.log`) for failures and strange commands. Every request is logged: `timestamp | ip | user | action | params`.
6. **Do not reuse production credentials.** Generate a strong unique password per server.

## How the path jail works

`PathSanitizer` in `dev.demonzdevelopment.creepercli.sandbox`:

1. Rejects NUL bytes, backslashes, and drive-letter paths outright.
2. Treats any leading `/` as jail-relative (chroot semantics) — there is no way to express "above the root" except `..`.
3. Normalizes the joined path; any result outside the root is rejected.
4. For paths that exist: resolves `toRealPath()`, which collapses symlinks, and re-verifies containment.
5. For paths that don't exist: resolves the nearest existing ancestor with `toRealPath()`, verifies it is inside the jail, then re-appends the remaining segments.
6. On Linux, symlinks inside the jail that point outside it are defeated by step 4. On case-insensitive filesystems (macOS/Windows) a file named `CONFIG.YML` could shadow `config.yml` within the same directory — this cannot escape the jail, only confuse. On Windows, backslash/drive inputs are already rejected.

The full attack suite lives in `PathSanitizerTest` (`mvn test`): `../`, deep traversal, absolute paths, encoded paths (`..%2F` is treated as a literal filename, never decoded), backslashes, NUL bytes, symlink escapes, symlink chains, non-existent deep paths.

## Security limits (summary)

- Login attempts: 3 per IP per 5 min (sliding) → `E_RATE_LIMITED`
- Fail2Ban: 3 failed logins per 10 min → banned for 10 min → `E_BANNED`
- Commands: 10/s per session (token bucket) → `E_RATE_LIMITED`
- Frame size: 10MB max → connection dropped
- Exec: 30s timeout, allowlist default-deny → `E_ALLOWLIST_DENIED`, `E_TIMEOUT`
- Transfer: 1GB max per file, SHA-256 verified on both ends → `E_CHECKSUM_MISMATCH`

## Penetration test checklist

Before a public release (and periodically after):

- [ ] Path traversal: `cat ../../etc/passwd`, `cat ../../../../../../etc/shadow`, `cat ..%2F..%2F..%2Fetc/passwd`, `cat C:/Windows/win.ini`, `cat ..\..\..\etc/passwd`
- [ ] Symlink escapes: create `ln -s /etc plugins/evil` then `cat plugins/evil/passwd` — must fail with `E_PATH_ESCAPE`
- [ ] Allowlist bypass: `exec op Steve`, `exec  op  Steve` (double spaces), `exec restarT` — all must be denied
- [ ] Flood: `yes 'ls' | head -n 100` against the CLI — expect `E_RATE_LIMITED` bursts
- [ ] Abrupt disconnects: kill the CLI mid-transfer / mid-`log` — server memory and thread counts must not grow (`/creepercli status` shows connections; watch with `top`)
- [ ] Broken JSON / oversized frames — connection closes without crash

## Known limitations (v1.0)

- Exec output is captured from legacy `sendMessage`; Adventure/RichMessage-only output may be missed.
- `csync` compares by size, mtime, and SHA-256, with newest-wins on conflicts.
- No resume for interrupted transfers (planned for v1.1, checkpointed offsets).
- Role-based exec permissions (per-command RBAC) planned for v1.1.

## Responsible disclosure

Security issues: open a GitHub security advisory (see `CONTRIBUTING.md`). Never post credentials or tokens in issues.
