# Hangar listing

- URL: https://hangar.papermc.io/DemonZ-Development/CreeperCLI
- Platforms: Paper (and Spigot compatible), 1.21.x, Java 21
- License: Apache 2.0

## Title

CreeperCLI: your Minecraft server, from your terminal

## Tagline

Sandboxed files, allowlisted commands, live console logs, and SHA-256 verified transfers. All over an SSH-tunnel-friendly TCP protocol.

## Description (paste-ready)

Stop fighting the web console. CreeperCLI puts a real terminal on your Minecraft server: browse and edit files, run console commands, watch logs live, and move files with integrity checks. A hardened Paper plugin does the heavy lifting; a tiny Node CLI runs anywhere Node runs.

Everything is built around safety:

- **Sandboxed file access.** Every path resolves inside the server root. `..` escapes, symlink swaps, and absolute-path tricks are blocked.
- **bcrypt + TOTP 2FA.** Passwords are hashed at cost 12, sessions expire after 15 idle minutes, and every token is bound to your IP.
- **Brute-force armor.** Per-IP login limits and fail2ban shut attackers down before they get anywhere.
- **Allowlisted commands.** Only console commands you approve can run, dispatched safely on the main thread.
- **Full accountability.** Every action lands in an append-only audit log with timestamp, IP, user, and parameters.

What it feels like:

```text
admin@server:/> stats
CPU load: 0.15 | Memory heap: 494.1M / 680.0M | Disk: 40.3G / 192.7G

admin@server:/> ls /plugins
banned-ips.json  bukkit.yml  config/  logs/  plugins/  server.properties

admin@server:/> log --grep 'ERROR|WARN'
[15:02:11] [Server thread/WARN]: Entity minecraft:zombie threw an exception
```

Edit remote configs in your local editor with `edit`, push plugin jars with `cpush`, pull crash reports with `cpull`, and mirror configs with `csync`. In-game admin commands (`/creepercli user add|remove|list`, `status`, `reload`, `update`) and anonymous usage metrics via bStats round it out (disable in `plugins/bStats/config.yml`).

### Install

1. Put the jar in `plugins/` and restart.
2. Run `/creepercli user add <name> <strong-password>` from the console.
3. Install the CLI: `npm install -g creepercli`.
4. Connect: `creepercli login --host <server-ip> --port 45678`.

### Security note

The plugin binds `0.0.0.0:45678` by default so hosting panels work out of the box. On a machine you control, bind loopback and tunnel: set `network.host: "127.0.0.1"` in `plugins/CreeperCLI/config.yml`, restart, then `ssh -N -L 45678:127.0.0.1:45678 user@server`. Run the server as a non-root OS user.

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
