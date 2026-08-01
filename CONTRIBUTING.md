# Contributing to CreeperCLI

Thanks for helping out. Please keep the following in mind.

## Reporting issues

Use GitHub Issues with a clear title, steps to reproduce, and (for the plugin) server version + relevant `config.yml` sections. Never paste passwords, TOTP secrets, or tokens.

## Security disclosures

CreeperCLI is a security-focused tool. For vulnerabilities (path escapes, auth bypass, rate limiter bypass, privilege issues), open a **draft** security advisory instead of a public issue. Do not reveal the issue publicly until it is fixed and released.

## Development setup

- Plugin: JDK 21 + Maven. `cd plugin && mvn test` for the PathSanitizer suite.
- CLI: Node.js 18+. `cd cli && npm install`.

## Pull requests

1. Fork the repo and create a branch (`fix/...`, `feature/...`).
2. Keep changes focused; one concern per PR.
3. The plugin targets Java 21 and Paper API 1.21+. No new runtime dependencies unless strictly needed (shaded, relocated).
4. The CLI keeps zero-dependency philosophy: only `qrcode-terminal` is allowed at runtime.
5. Run the tests before opening the PR (`mvn test`).
6. Update `docs/COMMANDS.md` and `CHANGELOG.md` when commands/protocol change.

## Protocol changes

If you change the NDJSON protocol, bump the protocol version in `Protocol.java` and `cli/src/protocol.js` and document the change in `docs/COMMANDS.md`.

## Release process (maintainers)

- SemVer: `vMAJOR.MINOR.PATCH` tags.
- Pushing a tag triggers the plugin build workflow and the npm publish workflow.
- Update `CHANGELOG.md` per release.

## License

By contributing you agree your contributions are licensed under the MIT License.
