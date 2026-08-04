# Hangar listing

- URL: https://hangar.papermc.io/DemonZ-Development/CreeperCLI
- Platforms: Paper (and Spigot compatible), 1.21.x, Java 21
- License: Apache 2.0

## Description

CreeperCLI gives you a terminal into your Minecraft server. A plugin runs on the server, a CLI runs on your machine, they talk over TCP.

Edit files, push and pull with checksums, sync directories. Run console commands through an allowlist. Stream logs, check TPS, watch processes live.

Bcrypt auth, optional 2FA, session timeouts, IP binding, audit logging. Defaults to `0.0.0.0:45678` for hosting panels. Set `127.0.0.1` and tunnel with SSH for production.

```bash
npm install -g creepercli
/creepercli user add <name> <password>
creepercli login --host <ip> --port 45678
```

Anonymous usage metrics via bStats, disabled in `plugins/bStats/config.yml`.

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool.
