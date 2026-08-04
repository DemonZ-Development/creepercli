# npm listing

- URL: https://www.npmjs.com/package/creepercli
- Status: Live, v1.0.0, published 2026-08-04
- Requires: Node.js 18+, the CreeperCLI plugin on the server

## What is already live

The package ships the CLI with one runtime dependency (`qrcode-terminal`), the npm readme, and the Apache 2.0 license. The readme in `cli/README.md` is the npm-facing page; keep it in sync and re-publish when it changes.

## Refresh checklist (per release)

1. Bump `version` in `cli/package.json` and run `npm install` to sync the lockfile.
2. Rebuild the readme from `cli/README.md` (it is copied into the tarball).
3. Publish: `npm publish` (the workflow on `v*` tags does this automatically).
4. Verify the tarball: `npm pack` and check it contains `bin/creepercli.js`, `src/`, `README.md`, and `LICENSE`.

## Description used on npm

The npm readme (see `cli/README.md`) covers: install, login, the REPL, the command table, security notes, and the plugin requirement. It includes the security note about the default `0.0.0.0` bind.

## Disclaimer

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool. Install it only on servers you own or are authorized to administer, secure the port as described, and stay within your hosting provider's terms of service. A version of this disclaimer is included in the readme and on every marketplace listing.
