# SpigotMC listing

- URL: https://www.spigotmc.org/resources/
- Supported: Spigot and Paper, 1.21.x, Java 21
- License: Apache 2.0
- Note: SpigotMC descriptions are limited to about 3,000 characters. The body below fits within it.

## Title

CreeperCLI — Remote administration from the terminal

## Tagline

Sandboxed files, allowlisted commands, live console, and verified transfers. Your server, from your shell.

## Description (paste-ready)

CreeperCLI is a server administration plugin with a companion CLI. It puts a real terminal on your Minecraft server: browse and edit files inside a sandbox, run console commands through an allowlist, stream logs with grep filtering, and transfer files with SHA-256 verification. No web panel, no RCON port, no clunky UI. Just your shell.

Security is the point:

- Sandboxed file access: every path is jailed inside the server root; `..` and symlink escapes are blocked.
- bcrypt (cost 12) logins with optional TOTP 2FA; sessions expire after 15 idle minutes and bind to your IP.
- Per-IP login limiting and fail2ban against brute force.
- Only allowlisted console commands run, scheduled safely on the main thread.
- Every action lands in an append-only audit log.

Also included: `edit` opens remote files in your local editor and pushes changes back under an exclusive lock; `stats`, `tps`, and a `top` dashboard; `cpush`/`cpull`/`csync` transfers with integrity checks; in-game `/creepercli` admin commands.

### Install

1. Put the jar in `plugins/` and restart.
2. Run `/creepercli user add <name> <strong-password>` from the console.
3. Install the CLI: `npm install -g creepercli`.
4. Connect: `creepercli login --host <server-ip> --port 45678`.

### Security note

The plugin binds `0.0.0.0:45678` by default for hosting panel compatibility. On a machine you control, set `network.host: "127.0.0.1"` in `plugins/CreeperCLI/config.yml` and reach the server through an SSH tunnel: `ssh -N -L 45678:127.0.0.1:45678 user@server`. Run the server as a non-root OS user.

### Disclaimer

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool. Install it only on servers you own or are authorized to administer, secure the port as described, and stay within your hosting provider's terms of service.

## Resource information

- Resource type: Plugin
- Supported versions: 1.21, 1.21.1, 1.21.2, 1.21.3
- Premium or free: Free, open source
- Source code link: https://github.com/DemonZ-Development/creepercli
- Donation link: optional

## Submission checklist

- [ ] Buy/verify SpigotMC membership (free resources require an account).
- [ ] Upload the shaded jar.
- [ ] Add 3 to 5 screenshots (REPL, `top`, `edit`, `log --grep`).
- [ ] Paste the description, security note, and disclaimer.
- [ ] Add the permission node `creepercli.admin` to the permissions field.
