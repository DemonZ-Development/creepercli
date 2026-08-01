# Quickstart

Get CreeperCLI running in about 10 minutes.

## 1. Install the plugin

1. Copy `CreeperCLI-1.0.0.jar` from the release (or build with `mvn -B package` in `plugin/`) into the server's `plugins/` folder.
2. Start/restart the server. You should see:
   ```
   [CreeperCLI] TCP server listening on 127.0.0.1:45678
   ```
3. Verify status in-game (as operator):
   ```
   /creepercli status
   ```

## 2. Add a user

Run in the Minecraft console or in-game as operator:

```
/creepercli user add steve <very-strong-password>
```

Passwords must be at least 8 characters. They are stored bcrypt-hashed in `plugins/CreeperCLI/creepercli-users.yml`.

> Warning: never hand this password to a player. CreeperCLI gives full jail access to the server filesystem.

## 3. Install the CLI

Requires Node.js 18+:

```
npm i -g creeper-cli
```

## 4. Tunnel (strongly recommended)

The plugin binds to `127.0.0.1` by default. If the server is remote, SSH-tunnel to it — never expose the port:

```
ssh -L 45678:127.0.0.1:45678 minecraft@your-server
```

Then use the CLI on your machine as if the server were local.

## 5. Log in

```
creepercli login
```

- Enter username and password (password input is masked).
- If the user has TOTP enabled, you will be prompted for the 6-digit code.
- On success the session token is stored in `~/.creepercli/creds` (mode 600).

Optional 2FA (recommended):

```
creepercli totp setup
```

Scan the QR with any authenticator app (Google Authenticator, Aegis, 1Password...), then enter the code it shows to confirm.

## 6. Use it

Interactive shell:

```
creepercli repl
steve@server:/plugins> ls -l
```

One-shot commands:

```
creepercli ls plugins
creepercli cat server.properties
creepercli exec whitelist add Steve
creepercli cpull logs/latest.log ./latest.log
creepercli top
```

## 7. Server-side hardening checklist

- [ ] Server OS user is non-root (`adduser minecraft`, run the server as that user).
- [ ] `network.host` is `127.0.0.1` (default) or your wireguard/LAN IP only.
- [ ] Firewall blocks port `45678` from the internet.
- [ ] TOTP enabled for all CLI users.
- [ ] `exec.allowlist` trimmed to what you actually need (default: `list`, `whitelist *`, `say *`, `restart`).
- [ ] Audit log checked occasionally: `plugins/CreeperCLI/creepercli-audit.log`.

Next: `docs/CONFIGURATION.md`, `docs/COMMANDS.md`, `docs/SECURITY.md`.
