# Contributing to CreeperCLI

## Reporting issues

Use GitHub Issues with a clear title and steps to reproduce. For plugin bugs, include the server version and the relevant `config.yml` sections. Never paste passwords, TOTP secrets, or session tokens.

## Security disclosures

CreeperCLI is a security tool. For vulnerabilities (path escapes, auth bypass, rate-limiter bypass, privilege issues), open a draft security advisory instead of a public issue. Keep it private until a fix ships.

## Development setup

- Plugin: JDK 21 and Maven. Run `mvn -B clean verify` in `plugin/`.
- CLI: Node.js 18+. Run `npm ci`, `npm test`, and `npm pack --dry-run` in `cli/`.

## Pull requests

1. Fork the repo and create a branch (`fix/...`, `feature/...`).
2. One concern per PR.
3. The plugin targets Java 21 and the Paper 1.21+ API. No new runtime dependencies unless strictly needed, and shade and relocate them if you add one.
4. The CLI stays dependency-light: `qrcode-terminal` is the only runtime dependency.
5. Run the release checks before opening the PR (`mvn -B clean verify`, `npm ci`, `npm test`, and `npm pack --dry-run`).
6. Update `wiki/cli-commands.md` and `CHANGELOG.md` when commands or the protocol change.

## Protocol changes

Changing the NDJSON protocol? Bump the version in `Protocol.java` and `cli/src/protocol.js`, then document the change in `wiki/protocol.md`.

## Docs

- User-facing docs live in `wiki/`. One source of truth, no mirror folders.
- Marketplace listings live in `marketplace/`. Keep the copy current when features ship.

## Release process (maintainers)

1. Bump versions in `plugin/pom.xml`, all three platform descriptors, `cli/package.json`, and `cli/package-lock.json`.
2. Update `CHANGELOG.md`, help/version output, and marketplace copy.
3. Run `mvn -B clean verify`, `npm ci`, `npm test`, and `npm pack --dry-run`.
4. Tag `vMAJOR.MINOR.PATCH`. The workflows build the plugin and publish the CLI to npm.
5. Publish the plugin jar to Modrinth, SpigotMC, Hangar, and CurseForge using the copy in `marketplace/`.

## License

By contributing you agree your contributions are licensed under the Apache License 2.0.
