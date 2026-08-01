# CreeperCLI

Remote server administration for Minecraft (Paper/Spigot) from your terminal — by **DemonZDevelopment**.

CreeperCLI ships two components:

| Component | Location | What it does |
|---|---|---|
| **CreeperCLI Plugin** (Java 21, Paper/Spigot) | `plugin/` | Runs on the Minecraft server. Owns the file sandbox, auth, exec allowlist, monitoring and log streaming. |
| **CreeperCLI CLI** (Node.js 18+) | `cli/` | Installed via `npm i -g creeper-cli`. Gives you an interactive REPL, editor workflow, transfers and live dashboards. |

## Features

- **Hardened sandbox** — `PathSanitizer` with `toRealPath()` symlink defeat, traversal-blocking normalization, chroot-style path jail.
- **Authentication** — bcrypt password hashes, RFC 6238 TOTP (2FA) with QR setup, session tokens with 15-minute inactivity expiry, per-IP auth limiter (3/5min) and Fail2Ban (3 failures → 10 min ban).
- **Filesystem** — `pwd`, `ls -la`, `cd`, `tree`, `cat`, `head`, `tail`, `wc`, `touch`, `mkdir`, `rm -r`, `cp -r`, `mv`, `info`.
- **Editing** — `edit` opens the remote file in your local `$EDITOR`; server-side locks with 5-minute TTL prevent conflicting edits.
- **Search** — `grep` (recursive, case-insensitive, line numbers) and `find` (glob).
- **Transfers** — `cpush`, `cpull`, `csync` with 64KB chunked NDJSON frames and SHA-256 verification.
- **Server control** — `exec` through a default-deny allowlist, scheduled on the Bukkit main thread with `CompletableFuture` (never blocks the network thread), `say`/`restart` shortcuts.
- **Monitoring** — `stats`, `tps`, `top` dashboard, live `log --grep` console streaming via a custom `java.util.logging.Handler` on `Bukkit.getLogger()`.
- **Security hardening** — per-session token bucket (30 commands/s default), append-only audit log with 10MB rotation, strict temp-file hygiene.

## Quickstart

1. Install the plugin: put `CreeperCLI-1.0.0.jar` into the server's `plugins/` folder and restart.
2. Add a user: `/creepercli user add steve <strong-password>` (in-game, as operator).
3. Install the CLI: `npm i -g creeper-cli`.
4. `creepercli login`, then run `creepercli repl` (or any one-shot command like `creepercli ls plugins`).

See `docs/QUICKSTART.md` for the full walkthrough.

## Security warnings (read these first)

- The server **must** run as a **non-root** OS user — CreeperCLI is a full admin channel.
- The TCP port **must only** be reachable over an **SSH tunnel** (default bind `127.0.0.1`). Do not expose it publicly.
- Every request is authenticated (bcrypt + optional TOTP), rate-limited, audited, and jail-bound to the server root.

See `docs/SECURITY.md` for the threat model and hardening checklist.

## Documentation

The full documentation lives in [`wiki/`](wiki/README.md):

- [`wiki/getting-started.md`](wiki/getting-started.md) — install, first user, first login, SSH tunnel
- [`wiki/cli-commands.md`](wiki/cli-commands.md) — every CLI command, flag and example
- [`wiki/console-commands.md`](wiki/console-commands.md) — `/creepercli` administration commands
- [`wiki/configuration.md`](wiki/configuration.md) — full `config.yml` reference
- [`wiki/security.md`](wiki/security.md) — threat model, auth, 2FA, sandbox, rate limits
- [`wiki/protocol.md`](wiki/protocol.md) — wire protocol, actions and error codes
- [`wiki/advanced.md`](wiki/advanced.md) — editing, syncing, monitoring, scripting
- [`wiki/troubleshooting.md`](wiki/troubleshooting.md) — common errors, FAQ, E2E harness

(`docs/` contains the original pre-wiki quickstart/commands/security notes.)

## Development

- Plugin: Java 21, Maven, Paper API 1.21+ — `mvn -B package` in `plugin/`
- CLI: Node.js 18+ — `npm install` in `cli/`
- Tests: `mvn test` in `plugin/` (PathSanitizer suite)

## Releases

SemVer, v1.0.0. GitHub Actions build the plugin on release tags (`v*`) and publish the CLI to npm. See `CONTRIBUTING.md`.

## License

MIT — see `LICENSE`.
