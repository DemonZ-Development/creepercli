# Configuration

All configuration lives in `plugins/CreeperCLI/config.yml` (created on first start).

## Default config.yml

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
  commands-per-second: 10
  file-lock-ttl-minutes: 5
  transfer-chunk-size: 65536
  max-transfer-bytes: 1073741824
monitor:
  log-buffer-lines: 500
  top-refresh-seconds: 2
audit:
  max-mb: 10
```

## Section reference

### network

| Key | Default | Meaning |
|---|---|---|
| `host` | `127.0.0.1` | Bind address. Keep loopback unless you have a private VPN/Wireguard. |
| `port` | `45678` | TCP port for the CLI. Change it to reduce noise on default scanners. |
| `max-connections` | `16` | Simultaneous connections; extra attempts get `E_SERVER_FULL`. |
| `max-payload-bytes` | `10485760` | Max size of one NDJSON frame (10MB). Bigger frames are dropped and the connection closed. |

### auth

| Key | Default | Meaning |
|---|---|---|
| `session-timeout-minutes` | `15` | Sessions expire after this long without activity. Each request renews the timer. |
| `login-limiter.max-attempts` | `3` | Max login attempts per IP within the window. |
| `login-limiter.window-minutes` | `5` | Sliding window for the login limiter. |
| `fail2ban.max-failures` | `3` | Failures within `window-minutes` trigger a ban. |
| `fail2ban.window-minutes` | `10` | Sliding window for Fail2Ban counting. |
| `fail2ban.ban-minutes` | `10` | Ban duration after the threshold is hit. |

### sandbox

| Key | Default | Meaning |
|---|---|---|
| `server-root` | `""` | Jail root. Empty = the server directory (the folder containing `world/`, `plugins/`, `logs/`). This is the root `/` of CreeperCLI; nothing outside it is reachable. |

### exec

| Key | Default | Meaning |
|---|---|---|
| `timeout-seconds` | `30` | Max wall time for one `exec` before it is aborted with `E_TIMEOUT`. |
| `allowlist` | see above | **Default-deny** list. Entries may be exact commands (`restart`) or prefixes with a trailing `*` (`whitelist *`). The longest match wins. An empty list denies everything. |

Allowlist matching is case-insensitive and whitespace-normalized:
`whitelist *` allows `whitelist add Steve`, `WHITELIST remove steve`; it does **not** allow `op Steve`.

The CLI-side `say` and `restart` shortcuts are pure aliases for `exec say ...` / `exec restart` and are still subject to this allowlist.

### limits

| Key | Default | Meaning |
|---|---|---|
| `commands-per-second` | `10` | Token-bucket rate per session (capacity == rate). |
| `file-lock-ttl-minutes` | `5` | Edit lock TTL; renewed on every `fs.edit.push`. |
| `transfer-chunk-size` | `65536` | Base64 chunk size (64KB) for cpush/cpull. |
| `max-transfer-bytes` | `1073741824` | Hard cap for one file transfer (1GB). |

### monitor

| Key | Default | Meaning |
|---|---|---|
| `log-buffer-lines` | `500` | In-memory ring buffer replayed when a client starts `log`. |
| `top-refresh-seconds` | `2` | Default dashboard refresh (the CLI `--refresh` flag overrides). |

### audit

| Key | Default | Meaning |
|---|---|---|
| `max-mb` | `10` | Audit log size before rotating to `<file>.1` (previous rotation is overwritten). |

## Users file: `creepercli-users.yml`

Created next to `config.yml`:

```yaml
users:
  steve:
    password: "$2a$12$..."
    totp: "JBSWY3DPEHPK3PXP"
```

- `password` — bcrypt hash (12 rounds). Managed with `/creepercli user add|remove`.
- `totp` — base32 TOTP secret, `null`/absent when 2FA is off. Managed with `creepercli totp setup|disable`.

Editing the file by hand is possible but must be followed by `/creepercli reload` (or a restart). Back up the file before editing by hand.

## In-game commands (operator only, permission `creepercli.admin`)

```
/creepercli status                      # port, connections, sessions, bans, TPS, uptime
/creepercli reload                      # reload config.yml + users
/creepercli user add <name> <password>  # create/update a user (bcrypt hashed)
/creepercli user remove <name>
/creepercli user list
```

## CLI configuration: `~/.creepercli/config.json`

```json
{
  "host": "127.0.0.1",
  "port": 45678,
  "editor": "code --wait",
  "topRefreshMs": 2000
}
```

Defaults: `host` `127.0.0.1`, `port` `45678`, `editor` `$EDITOR || vi`, `topRefreshMs` `2000`.
Flags `--host`, `--port`, `--editor`, `--refresh` override the file for one invocation.
