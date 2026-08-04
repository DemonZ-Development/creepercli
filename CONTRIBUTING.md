# Contributing to CreeperCLI

## Reporting issues

Use GitHub Issues with a clear title and steps to reproduce. For plugin bugs, include the server version and the relevant `config.yml` sections. Never paste passwords, TOTP secrets, or session tokens.

## Security disclosures

CreeperCLI is a security tool. For vulnerabilities (path escapes, auth bypass, rate-limiter bypass, privilege issues), open a draft security advisory instead of a public issue. Keep it private until a fix ships.

## Development setup

- Plugin: JDK 21 and Maven. `mvn -B package` in `plugin/`; `mvn test` for the PathSanitizer suite.
- CLI: Node.js 18+. `npm install` in `cli/`; `npm test` for tests.

## Pull requests

1. Fork the repo and create a branch (`fix/...`, `feature/...`).
2. One concern per PR.
3. The plugin targets Java 21 and the Paper 1.21+ API. No new runtime dependencies unless strictly needed, and shade and relocate them if you add one.
4. The CLI stays dependency-light: `qrcode-terminal` is the only runtime dependency.
5. Run the tests before opening the PR (`mvn test`, `npm test`).
6. Update `wiki/cli-commands.md` and `CHANGELOG.md` when commands or the protocol change.

## Protocol changes

Changing the NDJSON protocol? Bump the version in `Protocol.java` and `cli/src/protocol.js`, then document the change in `wiki/protocol.md`.

## Docs

- User-facing docs live in `wiki/`. One source of truth, no mirror folders.
- Marketplace listings live in `marketplace/`. Keep the copy current when features ship.

## Release process (maintainers)

1. Bump versions in `plugin/pom.xml` and `cli/package.json`.
2. Update `CHANGELOG.md`.
3. Tag `vMAJOR.MINOR.PATCH`. The workflows build the plugin and publish the CLI to npm.
4. Publish the plugin jar to Modrinth, SpigotMC, Hangar, and CurseForge using the copy in `marketplace/`.

## License

By contributing you agree your contributions are licensed under the Apache License 2.0.
