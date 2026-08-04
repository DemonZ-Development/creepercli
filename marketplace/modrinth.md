# Modrinth listing

- URL: https://modrinth.com/project/creepercli
- Project ID: `fPKEvBZo` (already wired into the plugin's update checker)
- Loaders: Paper, Spigot
- Game versions: 1.21.x
- Categories: server utility, server management
- License: Apache 2.0

## Description

CreeperCLI connects a Node.js command-line tool to a Paper or Spigot server through a TCP socket. The plugin runs on the server. The CLI runs on your machine. No web panel, no RCON.

Edit files on the server through your local editor. Push and pull files with SHA-256 verification. Sync entire directories. Run console commands through an allowlist you control. Stream the live console with grep filtering. Check TPS and server stats. Keep a live process monitor open.

Security is not optional. Bcrypt password hashing (cost 12). Optional TOTP two-factor authentication. Sessions expire after 15 minutes and lock to the connecting IP. Per-IP login throttling with temporary bans after failures. Symlink and `..` escape attempts get rejected. Every action writes to an append-only audit log.

The plugin binds `0.0.0.0:45678` by default for hosting panel compatibility. Set `network.host: "127.0.0.1"` in the config and connect through an SSH tunnel for production use.

Install the jar in `plugins/`, create a user from the server console, and connect from your terminal.

CreeperCLI grants remote, privileged control of a Minecraft server. The creator is not responsible for how you use this tool.
