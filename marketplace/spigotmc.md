# SpigotMC listing

- URL: https://www.spigotmc.org/resources/
- Supported: Spigot and Paper, 1.21.x, Java 21
- License: Apache 2.0
- Note: SpigotMC descriptions are limited to about 3,000 characters. The body below fits within it.

## Description

CreeperCLI connects a Node.js command-line tool to a Spigot or Paper server through a TCP socket. The plugin runs on the server. The CLI runs on your machine. No web panel, no RCON.

Edit files on the server through your local editor. Push and pull files with SHA-256 verification. Sync entire directories. Run console commands through an allowlist you control. Stream the live console with grep filtering. Check TPS and server stats. Keep a live process monitor open.

Security is not optional. Bcrypt password hashing (cost 12). Optional TOTP two-factor authentication. Sessions expire after 15 minutes and lock to the connecting IP. Per-IP login throttling with temporary bans after failures. Symlink and `..` escape attempts get rejected. Every action writes to an append-only audit log.

The plugin binds `0.0.0.0:45678` by default for hosting panel compatibility. Set `network.host: "127.0.0.1"` in the config and connect through an SSH tunnel for production use.

Install the jar in `plugins/` and restart the server. Create a user from the server console with `/creepercli user add <name> <password>`. On your machine, run `npm install -g creepercli` then `creepercli login --host <ip> --port 45678`.

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool.
