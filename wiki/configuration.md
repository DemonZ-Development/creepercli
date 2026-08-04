# Configuration reference

The plugin writes a default `plugins/CreeperCLI/config.yml` on first boot. Edit it and run `/creepercli reload`, or restart the server. Listener host and port require a restart: binding happens once at enable.

Full default file:

```yaml
config-version: 1

network:
  # Host IP interface to bind to ("0.0.0.0" for all interfaces, "127.0.0.1" for SSH tunnel only)
  host: "0.0.0.0"
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
  # Path jail root ("." or empty string defaults to the server working directory)
  server-root: "."

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
  # Set debug-log to true for detailed RPC payload logging in the server console
  debug-log: false

audit:
  max-mb: 10
```

## `config-version` and auto-migration

When you upgrade the plugin, missing keys are injected into `config.yml` automatically while your passwords, ports, and allowlists are preserved.

## `network`

| Key | Default | Meaning |
|---|---|---|
| `host` | `"0.0.0.0"` | Bind address. Binds all interfaces by default so hosted containers (Pterodactyl, Apex, Docker) work out of the box. Use `"127.0.0.1"` and an SSH tunnel on a machine you control. Requires restart. |
| `port` | `45678` | TCP port. Requires restart. |
| `max-connections` | `16` | Hard cap on concurrent TCP connections; extra sockets get one `E_SERVER_FULL` response and close. |
| `max-payload-bytes` | `10485760` (10 MiB) | Largest single protocol frame accepted, e.g. one transfer chunk or one `fs.cat` reply. Larger frames get `E_PAYLOAD_TOO_LARGE`. |

## `auth`

| Key | Default | Meaning |
|---|---|---|
| `session-timeout-minutes` | `15` | Session inactivity timeout. A token unused this long becomes invalid (`E_SESSION_EXPIRED`); activity resets the clock. |
| `login-limiter.max-attempts` | `3` | Failed logins per IP per window before temporary lockout. |
| `login-limiter.window-minutes` | `5` | Window for `max-attempts`. |
| `fail2ban.max-failures` | `3` | Login failures that trigger a ban, counted within the fail2ban window. |
| `fail2ban.window-minutes` | `10` | Window in which failures accumulate. |
| `fail2ban.ban-minutes` | `10` | Ban duration. Banned IPs get `E_BANNED` on login. |

## `sandbox`

| Key | Default | Meaning |
|---|---|---|
| `server-root` | `"."` | Jail root for all file actions. `"."` or empty string means the main server directory (parent of `plugins/`). Any absolute path works, e.g. `/var/mc/data`. `~` expands to the jail root. |

## `exec`

| Key | Default | Meaning |
|---|---|---|
| `timeout-seconds` | `30` | Max time a console command may run before the client sees an error. |
| `allowlist` | `list`, `whitelist *`, `say *`, `restart` | Console commands the API may run, as space-separated patterns with `*` wildcards. See [security.md](security.md#console-execution). |

## `limits`

| Key | Default | Meaning |
|---|---|---|
| `commands-per-second` | `30` | Token-bucket rate for authenticated actions, per session. Bursts get `E_RATE_LIMITED`. |
| `file-lock-ttl-minutes` | `5` | How long an `edit` lock survives if the client dies. |
| `transfer-chunk-size` | `65536` (64 KiB) | Chunk size for push and pull streams. |
| `max-transfer-bytes` | `1073741824` (1 GiB) | Per-transfer size cap, server-side. |

## `monitor`

| Key | Default | Meaning |
|---|---|---|
| `log-buffer-lines` | `500` | Lines of console history buffered for `creepercli log`. |
| `top-refresh-seconds` | `2` | Refresh interval for the `top` dashboard. |
| `debug-log` | `false` | Logs detailed RPC action dispatches, IPs, and payload events to the server console. |

## `audit`

| Key | Default | Meaning |
|---|---|---|
| `max-mb` | `10` | Size cap for `plugins/CreeperCLI/creepercli-audit.log` before rotation. |

## Update checker

The plugin checks Modrinth (project `fPKEvBZo`) silently every 4 hours. A new version prints a notice to the console and to OP players on join. Run `/creepercli update` to check manually.

## Usage metrics (bStats)

The plugin sends anonymous usage metrics to [bStats](https://bstats.org/plugin/bukkit/CreeperCLI/33129): plugin version, server software and version, Java version, OS, online player count, and online-mode. No usernames, paths, commands, or tokens are ever sent.

Beyond the standard data, CreeperCLI reports:

| Chart | Type | Values |
|---|---|---|
| `bind_host` | SimplePie | the configured `network.host` value |
| `debug_log` | SimplePie | `enabled` / `disabled` |
| `two_fa_users` | AdvancedPie | accounts with and without 2FA |
| `user_count` | SingleLineChart | number of accounts |

Server owners can disable metrics in the shared `plugins/bStats/config.yml` (`enabled: false`). The bStats library is shaded into the plugin jar and relocated, so it never conflicts with other plugins.
