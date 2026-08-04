# Getting started

## Prerequisites

| Component | Requirement |
|---|---|
| Server | Paper 1.21.x (Java Edition) |
| Java | 21 or newer, the same JVM Paper runs on |
| Your machine | Node.js 20+ |
| Network | Your machine can reach the server, directly or through an SSH tunnel |

## 1. Install the plugin

Download `CreeperCLI-1.0.0.jar` from Modrinth, or build it:

```bash
cd plugin
mvn -B package
# plugin/target/CreeperCLI-1.0.0.jar
```

Copy the jar into the server's `plugins/` folder and restart. First boot creates `plugins/CreeperCLI/config.yml` and starts the TCP listener on `0.0.0.0:45678` by default. See [configuration.md](configuration.md) for every key.

## 2. Create a user

From the server console, or in-game as operator:

```
/creepercli user add alice 'a-strong-password-123'
```

The password must be at least 8 characters and must not equal the username. Users are stored as bcrypt hashes in `plugins/CreeperCLI/creepercli-users.yml`, never as plaintext.

## 3. Install the CLI

```bash
npm install -g creepercli
creepercli --help
```

## 4. Log in

```bash
creepercli login --host <server-ip> --port 45678
```

Enter your username and password. If the account has 2FA, enter the TOTP code. The CLI stores the session token in `~/.creepercli/creds`, saves the host and port for next time, and drops you into the interactive shell:

```text
Authenticated as alice. Session valid for 15 minutes.
Entering interactive REPL shell...

alice@<server-ip>:/>
```

## 5. First commands

Inside the REPL, no `creepercli` prefix:

```text
ping              # pong, server time, plugin version
ls /              # server root
cat server.properties
stats             # CPU / RAM / disk
tps               # ticks per second
q                 # exit
```

All file paths live in a sandbox rooted at the server directory. `~` and `/` both mean the sandbox root. Paths with `..` that would escape are blocked.

## 6. Reach the port safely

The plugin binds `0.0.0.0` by default so hosted panels (Pterodactyl, Apex, Bisect, Docker) work without config changes. On a machine you control, lock it down:

1. Set `network.host: "127.0.0.1"` in `plugins/CreeperCLI/config.yml` and restart the server.
2. Tunnel: `ssh -N -L 45678:127.0.0.1:45678 user@server`
3. Connect to `127.0.0.1:45678` as usual.

Run the server as a non-root OS user. See [security.md](security.md) for the full threat model.

## What's next

- [cli-commands.md](cli-commands.md) for every command and flag
- [configuration.md](configuration.md) for every config key
- [troubleshooting.md](troubleshooting.md) when something fails