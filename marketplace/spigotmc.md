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

CreeperCLI is a remote admin plugin for Spigot and Paper with a companion CLI. The plugin handles authentication, file access, console command execution, and monitoring over TCP; the CLI gives you a shell into the server. No web panel, no RCON port.

What you can do:

- read and edit files on the server, with every path checked against a sandbox rooted at the server folder;
- run console commands through an allowlist, so only the ones you picked ever execute;
- stream the console live with `log --grep`, check `stats` and `tps`, or keep `top` open;
- push and pull files with `cpush` / `cpull` and sync directories with `csync`, all with SHA-256 checks;
- edit a config in your local editor (`edit`) and have it pushed back under a lock.

Security is enforced server-side, not as an option:

- logins use bcrypt (cost 12) and optional TOTP 2FA;
- sessions expire after 15 minutes of inactivity and are bound to the IP they came from;
- per-IP login throttling, plus a temporary ban after repeated failures;
- `..` and symlink escapes from the sandbox are rejected;
- every action is written to an append-only audit log with timestamp, IP, user, and parameters.

Install:

1. Put the jar in `plugins/` and restart.
2. From the server console: `/creepercli user add <name> <password>`.
3. `npm install -g creepercli`
4. `creepercli login --host <ip> --port 45678`

### Security note

The plugin listens on `0.0.0.0:45678` by default so it works on hosting panels out of the box. On a machine you control, set `network.host: "127.0.0.1"` in `plugins/CreeperCLI/config.yml` and connect over an SSH tunnel: `ssh -N -L 45678:127.0.0.1:45678 user@server`. Run the server as a non-root OS user.

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
