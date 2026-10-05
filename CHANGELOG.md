# Changelog

All notable changes. Follows [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-10-05

### Added

- BungeeCord/Waterfall and Velocity support.
- Authentication deadlines and bounded outbound queues.
- Expanded regression coverage for authentication, transfers,
  sync paths, plugin loading, monitoring, and filesystem operations.

### Changed

- Plugin now requires Java 21 and targets Paper/Spigot 1.21+.
- New configurations bind to `127.0.0.1` by default.
- Removing or replacing accounts revokes their live sessions.
  Password changes revoke other sessions.
- Release workflows verify builds, package versions, tests,
  and CLI package contents before publishing.

### Fixed

- Incorrect Paper/Spigot plugin entry point.
- `csync` handling of `.`, `..`, `~`, and relative paths after `cd`.
- Missing local parent directories during downloads.
- Slow partial requests bypassing the authentication deadline.
- `exit`, `quit`, and `q` leaving the REPL running.
- CLI updater querying the wrong npm package.
- Upload temporary-file collisions, checksum validation,
  unauthorized transfer aborts, and abandoned transfers.
- Authentication limiter cleanup and successful-login throttling.
- Tick-time calculations and trailing-newline handling in `tail`.
- Invalid configuration recovery now backs up the original file.

## [1.0.0] - 2026-08-01

Initial release.

### Plugin (Java)

- NDJSON TCP protocol v1 with unique request IDs and typed error codes
- bcrypt (cost 12) auth with `creepercli-users.yml`, RFC 6238 TOTP 2FA with QR setup, 15-minute IP-bound session tokens
- Per-IP login limiter (3 attempts / 5 min) and fail2ban (3 failures, 10-minute ban)
- `PathSanitizer` jail: real-path symlink defeat, traversal blocking, chroot-style absolute paths, JUnit suite
- Filesystem commands: `pwd`, `ls -la`, `cd`, `tree`, `cat`, `head`, `tail`, `wc`, `touch`, `mkdir`, `rm -r`, `cp -r`, `mv`, `info`
- Edit locking with 5-minute TTL and atomic pushes
- `grep` / `find` with depth, size, and result limits
- Chunked transfers (64 KB base64) with SHA-256 verification: `cpush` / `cpull` / `csync`
- `exec` with default-deny allowlist, main-thread scheduling, output capture, 30-second timeout
- Live console streaming via a `java.util.logging.Handler` on `Bukkit.getLogger()` with `--grep` filtering
- `stats` / `tps` / `top` monitoring
- Token-bucket command limiter (30/s per session)
- Append-only audit log with 10 MB rotation
- In-game admin commands: `/creepercli user add|remove|list`, `status`, `reload`, `update`
- Third-party plugin API: `ActionRegistry` / `ActionHandler`
- Anonymous usage metrics via [bStats](https://bstats.org/plugin/bukkit/CreeperCLI/33129) (shaded and relocated; disable in `plugins/bStats/config.yml`)

### CLI (Node.js)

- Interactive REPL with persistent history and automatic prefix stripping
- One-shot mode: `creepercli ls /plugins`
- Local `$EDITOR` workflow with temp-file hygiene and change detection
- `top` live dashboard, log follow with `--grep`
- Progress indicators and SHA-256 verification for transfers
- Session persistence in `~/.creepercli/creds` (mode 0600)
- `say` / `restart` shortcuts mapped through the allowlist

### Docs and distribution

- Published to npm as `creepercli` 1.0.0
- Wiki: getting started, CLI and console references, configuration, security, protocol, plugin API, architecture, troubleshooting
- Marketplace copy for Modrinth, SpigotMC, Hangar, CurseForge, npm, and GitHub Releases
- GitHub Actions: plugin build on release tags, npm publish on release tags
- Apache License 2.0
