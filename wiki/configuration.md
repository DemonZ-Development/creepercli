# Configuration reference

The plugin writes a default `plugins/CreeperCLI/config.yml` on first boot. Edit it and either
run `/creepercli reload` or restart the server. **Listener host/port require a restart**
(binding happens once at enable).

Full default file (v1.0.0):

```yaml
network:
  host: 127.0.0.1
  port: 45678
  max-connections: 16
  max-payload-bytes: 10485760
auth:
  session-timeout-minutes: 15
  login-limiter:
    max-attempts: 3
    window-minutes: 5
  fail2ban:
    max-failures: 3
    window-minutes: 10
    ban-minutes: 10
sandbox:
  server-root: ""
exec:
  timeout-seconds: 30
  allowlist:
    - "list"
    - "whitelist *"
    - "say *"
    - "restart"
limits:
  commands-per-second: 30
  file-lock-ttl-minutes: 5
  transfer-chunk-size: 65536
  max-transfer-bytes: 1073741824
monitor:
  log-buffer-lines: 500
  top-refresh-seconds: 2
audit:
  max-mb: 10
```

---

## `network`

| Key | Default | Meaning |
|---|---|---|
| `host` | `127.0.0.1` | Bind address. Keep loopback and use an SSH tunnel for remote access (see [getting-started](getting-started.md#step-6--reach-a-remote-server-ssh-tunnel)). Requires restart. |
| `port` | `45678` | TCP port. Requires restart. |
| `max-connections` | `16` | Hard cap on concurrent TCP connections; beyond this, new sockets get a single `E_SERVER_FULL` response and are closed. |
| `max-payload-bytes` | `10485760` (10 MiB) | Largest single protocol frame accepted (e.g. one transfer chunk, one `fs.cat` reply). Larger frames → `E_PAYLOAD_TOO_LARGE`. |

## `auth`

| Key | Default | Meaning |
|---|---|---|
| `session-timeout-minutes` | `15` | Session inactivity timeout. A token unused for this long becomes invalid (`E_SESSION_EXPIRED`). Activity resets the clock. |
| `login-limiter.max-attempts` | `3` | Failed login attempts allowed per IP per window before temporary lockout. |
| `login-limiter.window-minutes` | `5` | Window for `max-attempts`. |
| `fail2ban.max-failures` | `3` | Login failures that trigger a ban (counted within the fail2ban window; the login limiter must be passing for attempts to count). |
| `fail2ban.window-minutes` | `10` | Window in which failures accumulate. |
| `fail2ban.ban-minutes` | `10` | Ban duration after the threshold. Banned IPs get `E_BANNED` on login. |

## `sandbox`

| Key | Default | Meaning |
|---|---|---|
| `server-root` | `""` | Jail root for all file actions. Empty = the server directory (parent of `plugins/`). Any `Path` is fine, e.g. `/var/mc/data`. |

Sandbox semantics (all enforced by `PathSanitizer`):

- paths resolve **inside** the jail; `..` cannot escape (`E_PATH_ESCAPE`);
- absolute paths starting with `/` are relative to the jail root;
- symlinks are resolved and must stay inside the root;
- the root itself is the path `"/"` from the CLI's perspective.

## `exec`

| Key | Default | Meaning |
|---|---|---|
| `timeout-seconds` | `30` | Max time a console command may run before the client's `exec.run` errors out. |
| `allowlist` | `list`, `whitelist *`, `say *`, `restart` | Console commands the API may run, as patterns (space-separated tokens, `*` wildcards). See [security.md](security.md#console-command-execution). |

## `limits`

| Key | Default | Meaning |
|---|---|---|
| `commands-per-second` | `30` | Token-bucket rate for authenticated API actions **per session** (capacity = rate). Bursts above this get `E_RATE_LIMITED`. Interactive use is far below this; raise it for scripted automation. |
| `file-lock-ttl-minutes` | `5` | How long an `edit` lock survives if the client dies (auto-released after this). |
| `transfer-chunk-size` | `65536` (64 KiB) | Chunk size for push/pull streams. |
| `max-transfer-bytes` | `1073741824` (1 GiB) | Per-transfer size cap (server-side). |

## `monitor`

| Key | Default | Meaning |
|---|---|---|
| `log-buffer-lines` | `500` | Lines of console history buffered for `creepercli log` (sent on subscribe before live streaming). |
| `top-refresh-seconds` | `2` | Refresh interval for the `top` dashboard (override per-invocation with `--refresh`). |

## `audit`

| Key | Default | Meaning |
|---|---|---|
| `max-mb` | `10` | Size cap for `plugins/CreeperCLI/creepercli-audit.log` before it rotates. |

## Files the plugin manages

| Path (relative to `plugins/CreeperCLI/`) | Contents |
|---|---|
| `config.yml` | This document |
| `creepercli-users.yml` | Users: bcrypt hashes, TOTP secrets (keep the plugins dir private) |
| `creepercli-audit.log` | Append-only audit of every API action (rotates at `audit.max-mb`) |

## CLI-side configuration (`~/.creepercli/`)

| File | Contents |
|---|---|
| `config.json` | CLI defaults: `host`, `port`, `editor`, `topRefreshMs` |
| `creds` | Session token (mode `0600`, keyed to host+port) |
| `history` | REPL history |
| `tmp/` | Temp dir used by `edit` (removed after each edit) |
