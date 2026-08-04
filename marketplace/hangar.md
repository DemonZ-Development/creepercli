# Hangar listing

- URL: https://hangar.papermc.io/DemonZ-Development/CreeperCLI
- Platforms: Paper (and Spigot compatible), 1.21.x, Java 21
- License: Apache 2.0

## Title

CreeperCLI — manage your Minecraft server from the terminal

## Tagline

Remote admin for Paper/Spigot: sandboxed file access, allowlisted console commands, live logs, and verified transfers from your terminal.

## Description (paste-ready)

CreeperCLI is a remote admin tool for Paper and Spigot servers. A small plugin runs on the server and speaks a JSON protocol over TCP; a Node CLI runs on your machine. No web panel involved.

With it you can:

- read and edit files on the server, with every path checked against a sandbox rooted at the server folder;
- run console commands through an allowlist, so only the ones you picked ever execute;
- stream the console live with `log --grep`, check `stats` and `tps`, or keep `top` open;
- push and pull files with `cpush` / `cpull`, and sync directories with `csync`, all with SHA-256 checks;
- edit a config in your local editor (`edit`) and have it pushed back under a lock.

Security is enforced server-side, not as an option:

- logins use bcrypt (cost 12) and optional TOTP 2FA;
- sessions expire after 15 minutes of inactivity and are bound to the IP they came from;
- per-IP login throttling, plus a temporary ban after repeated failures;
- `..` and symlink escapes from the sandbox are rejected;
- every action is written to an append-only audit log with timestamp, IP, user, and parameters.

In-game admin commands (`/creepercli user add|remove|list`, `status`, `reload`, `update`) round it out. Anonymous usage metrics are collected via bStats and can be disabled in `plugins/bStats/config.yml`.

Install:

1. Put the jar in `plugins/` and restart.
2. From the server console: `/creepercli user add <name> <password>`.
3. `npm install -g creepercli`
4. `creepercli login --host <ip> --port 45678`

### Security note

The plugin listens on `0.0.0.0:45678` by default so it works on hosting panels out of the box. On a machine you control, set `network.host: "127.0.0.1"` in `plugins/CreeperCLI/config.yml` and connect over an SSH tunnel: `ssh -N -L 45678:127.0.0.1:45678 user@server`. Run the server as a non-root OS user.

### Disclaimer

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool. Install it only on servers you own or are authorized to administer, secure the port as described, and stay within your hosting provider's terms of service.

## Submission notes

- Hangar requires a version matrix. Add an entry per game version (1.21.x) pointing at the release jar.
- Categorize under "Management" and add the `Administration` tag.
- Link the source repository for approval; Hangar flags closed source.

## Submission checklist

- [ ] Create the project on Hangar and verify the external (GitHub) link.
- [ ] Upload `CreeperCLI-1.0.0.jar` for each supported MC version.
- [ ] Paste the description, security note, and disclaimer.
- [ ] Add screenshots.
