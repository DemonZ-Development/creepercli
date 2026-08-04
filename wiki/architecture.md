# Architecture

CreeperCLI is two processes connected by a TCP socket speaking JSON lines.

```
Your machine                           Server (Paper 1.21+)
┌─────────────────────┐                ┌──────────────────────────────────┐
│ creepercli (Node)   │  TCP / JSON    │ CreeperCLI plugin                │
│ REPL, transfers,    │◄──────────────►│ TcpServer (0.0.0.0:45678)        │
│ dashboards          │  newline-      │ CommandRouter (auth + actions)   │
└─────────────────────┘  delimited     │ Sandbox (server-root jail)       │
                                       │ ExecAllowlist                    │
                                       │ AuditLogger (append-only)        │
                                       └──────────────────────────────────┘
```

## Components

- **`TcpServer`**: accepts sockets, parses NDJSON frames, enforces the connection cap, payload cap, and per-session rate limits. Binds `network.host:network.port`, default `0.0.0.0:45678`.
- **`CommandRouter`**: dispatches actions to handlers. Every action passes session verification and audit logging first.
- **`PathSanitizer`**: resolves every file path against the sandbox root. It normalizes separators, strips NUL bytes and drive letters, and resolves symlinks with `toRealPath()` so a link can never escape the jail.
- **`ExecAllowlist`**: pattern-matches console commands before dispatch.
- **`BukkitBridge`**: schedules allowlisted commands on the main thread via `Bukkit.getScheduler().runTask()` and returns a `CompletableFuture` to the network thread, so exec never blocks the server tick.
- **`LogStreamer`**: a `java.util.logging.Handler` attached to `Bukkit.getLogger()` broadcasts lines to `monitor.log.start` subscribers.
- **`SessionManager`**, **`AuthManager`** (bcrypt + TOTP), **`RateLimiter`** (token bucket), **`Fail2Ban`** (per-IP), **`AuditLogger`** (append-only, rotating).
- **`Metrics`**: bStats 3.x integration, shaded and relocated under `dev.demonzdevelopment.creepercli.metrics`. Reports `bind_host`, `debug_log`, `two_fa_users`, and `user_count` charts. See [configuration.md](configuration.md#usage-metrics-bstats).
- **`ActionRegistry`**: public API for third-party plugins to register custom actions.

## Threading model

- Socket reads and file I/O run off the main thread.
- Anything touching the Bukkit API (console commands, broadcasting) is scheduled onto the main thread.
- Log subscribers receive lines on the logging thread.

## Sandbox design

1. Normalize: backslashes to forward slashes, strip NUL bytes and drive letters.
2. Resolve the canonical parent with `toRealPath()`.
3. Verify the canonical path starts with the jail root.
4. For non-existent files, resolve the nearest existing parent, check it, then append the new path.

## Extension API

Third-party plugins register actions with `ActionRegistry`:

```java
CreeperCLIPlugin creeper = JavaPlugin.getPlugin(CreeperCLIPlugin.class);
creeper.actionRegistry().registerAction("custom.action", (conn, params) -> { ... });
```

Custom actions inherit session verification, rate limiting, and audit logging. See [plugin-api.md](plugin-api.md).