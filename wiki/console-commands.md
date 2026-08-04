# Console commands (in-game / server console)

The plugin registers one Bukkit command: `/creepercli`, permission `creepercli.admin` (default: operators only).

```
/creepercli <status|reload|user|update|help>
```

## `status`

Overview of the running service:

```text
CreeperCLI status:
  Config Version: 1
  Listener: 0.0.0.0:45678
  Connections: 2
  Sessions: 2
  Banned IPs: 0
  Edit locks: 0
  Log subscribers: 1
  Users: 1
  Debug Logging: false
  TPS (1m/5m/15m): 19.8 / 19.9 / 19.9
  Uptime: 1d 2h 3m
```

Useful to confirm the service is healthy, see active sessions, and check whether fail2ban or debug logging is running.

## `reload`

Reloads `config.yml` and the users file without restarting the server:

- re-reads all config values. Listener host and port are not rebound; change those and restart;
- runs the `config-version` auto-migration;
- rebuilds the exec allowlist, rate limiters, fail2ban, and sandbox root;
- reloads `creepercli-users.yml`.

## `update`

Triggers an asynchronous check against the Modrinth v2 API (project `fPKEvBZo`):

```text
Checking for updates on Modrinth...
Update available: v1.0.1 (https://modrinth.com/project/creepercli)
```

## `user add <name> <password>`

Creates or updates a user. Enforced immediately:

- password at least 8 characters;
- password must not equal the username.

The password is bcrypt-hashed (cost 12) and stored in `plugins/CreeperCLI/creepercli-users.yml`; plaintext never touches disk.

> Lost your authenticator? `user add` with the same name replaces the account and clears the TOTP secret.

## `user remove <name>`

Deletes the user and saves. Existing sessions for that user fail on their next request (`E_SESSION_EXPIRED`).

## `user list`

```text
  alice (totp)
  bob
```

## `help`

Displays slash command usage:

```text
=== CreeperCLI Slash Commands ===
  /creepercli status     - Show server listener & connection stats
  /creepercli reload     - Reload config.yml & user store
  /creepercli user       - Manage users: add <name> <pass> | remove <name> | list
  /creepercli update     - Check for updates on Modrinth
```

Tab completion covers `status`, `reload`, `user` (`add`, `remove`, `list`), `update`, and `help`.
