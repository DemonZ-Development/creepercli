# Modrinth listing

- URL: https://modrinth.com/project/creepercli
- Project ID: `fPKEvBZo` (already wired into the plugin's update checker)
- Loaders: Paper, Spigot
- Game versions: 1.21.x
- Categories: server utility, server management
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

Install:

1. Put the jar in `plugins/` and restart.
2. From the server console: `/creepercli user add <name> <password>`.
3. `npm install -g creepercli`
4. `creepercli login --host <ip> --port 45678`

### Security note

The plugin listens on `0.0.0.0:45678` by default so it works on hosting panels out of the box. On a machine you control, set `network.host: "127.0.0.1"` in `plugins/CreeperCLI/config.yml` and connect over an SSH tunnel: `ssh -N -L 45678:127.0.0.1:45678 user@server`. Run the server as a non-root OS user.

### Disclaimer

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool. Install it only on servers you own or are authorized to administer, secure the port as described, and stay within your hosting provider's terms of service.

## Screenshots to attach

1. REPL showing `stats` and `tps`.
2. The `top` live dashboard.
3. `edit` workflow with a diff summary.
4. `log --grep` streaming console output.

## Keywords

minecraft, paper, spigot, server administration, terminal, ssh, tunneling, remote access, console, tps, rcon alternative

## Submission checklist

- [ ] Register the charts `bind_host`, `debug_log`, `two_fa_users`, `user_count` in the bStats dashboard.
- [ ] Upload the shaded jar from `plugin/target/CreeperCLI-1.0.0.jar`.
- [ ] Attach screenshots.
- [ ] Mark the project as open-source and link the GitHub repo.
