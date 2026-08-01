# Changelog

All notable changes are tracked here. Follows [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-08-01

Initial release.

### Plugin (Java)
- NDJSON TCP protocol (v1) with unique request IDs and typed error codes
- bcrypt authentication with `creepercli-users.yml`, RFC 6238 TOTP 2FA with QR setup, 15-minute session tokens
- Per-IP auth limiter (3 attempts / 5 min) and Fail2Ban (3 failures -> 10 min ban)
- `PathSanitizer` jail: real-path symlink defeat, traversal blocking, chroot-style absolute paths, JUnit suite
- Filesystem commands: pwd, ls -la, cd, tree, cat, head, tail, wc, touch, mkdir, rm -r, cp -r, mv, info
- Edit locking with 5-minute TTL and atomic pushes
- grep / find with depth, size and result limits
- Chunked transfers (64KB base64) with SHA-256 verification: cpush / cpull / csync
- `exec` with default-deny allowlist, main-thread scheduling, output capture, 30s timeout
- Live console streaming via `java.util.logging.Handler` on `Bukkit.getLogger()` with --grep filter
- stats / tps / top monitoring
- Token-bucket command limiter (30/s per session)
- Append-only audit log with 10MB rotation
- In-game admin commands: /creepercli user add|remove|list, status, reload

### CLI (Node.js)
- Interactive REPL with persistent history
- One-shot commands: `creepercli ls plugins`
- Local `$EDITOR` workflow with temp-file hygiene and change detection
- `top` live dashboard (configurable refresh), log follow with grep
- Progress indicators and checksum verification for transfers
- Session persistence in `~/.creepercli/creds` (chmod 600)
- `say` / `restart` aliases mapped to the server-side allowlist

### Docs / CI
- QUICKSTART, CONFIGURATION, SECURITY, COMMANDS docs
- GitHub Actions: plugin build on release tags; npm publish on release tags
- MIT license
