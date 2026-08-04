# Modrinth listing

- URL: https://modrinth.com/project/creepercli
- Project ID: `fPKEvBZo` (already wired into the plugin's update checker)
- Loaders: Paper, Spigot
- Game versions: 1.21.x
- Categories: server utility, server management
- License: Apache 2.0

## Description

CreeperCLI gives you a terminal into your Minecraft server. A plugin runs on the server, a CLI runs on your machine, they talk over TCP.

Edit files, push and pull with checksums, sync directories. Run console commands through an allowlist. Stream logs, check TPS, watch processes live.

Bcrypt auth, optional 2FA, session timeouts, IP binding, audit logging. Defaults to `0.0.0.0:45678` for hosting panels. Set `127.0.0.1` and tunnel with SSH for production.

```bash
# install
npm install -g creepercli

# server console
/creepercli user add <name> <password>

# your machine
creepercli login --host <ip> --port 45678
```

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool.
