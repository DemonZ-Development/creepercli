# Console commands (in-game / server console)

The plugin registers one Bukkit command: `/creepercli`, permission `creepercli.admin`
(default: operators only).

```
/creepercli <status|reload|user add <name> <password>|user remove <name>|user list>
```

## `status`

Overview of the running service:

```
CreeperCLI status:
  Listener: 127.0.0.1:45678
  Connections: 2
  Sessions: 2
  Banned IPs: 0
  Edit locks: 0
  Log subscribers: 1
  Users: 1
  TPS (1m/5m/15m): 19.8 / 19.9 / 19.9
  Uptime: 1d 2h 3m
```

Useful to verify the service is healthy, who has sessions, and whether fail2ban is active.

## `reload`

Reloads `config.yml` and the users file **without restarting the server**:

- re-reads all config values (listener host/port are **not** rebound — change those in
  `config.yml` + restart);
- re-creates the exec allowlist, rate limiters, fail2ban and sandbox root;
- reloads `creepercli-users.yml`.

Existing sessions keep running with their original limits.

## `user add <name> <password>`

Create or update a user. Enforced immediately:

- password ≥ 8 characters;
- password must not equal the username.

The password is bcrypt-hashed (cost 12) and stored in
`plugins/CreeperCLI/creepercli-users.yml` — the plaintext never touches disk.

> **TOTP users**: to reset 2FA for someone who lost their authenticator, `user add` them
> again with the same name — it replaces the account and **clears the TOTP secret**
> (because a fresh `User` record has none). The user can then re-enroll with `totp setup`.
> Invalidate their old sessions with `user remove` + re-add, or wait for the 15-minute
> session timeout.

## `user remove <name>`

Delete the user and save. Existing sessions for that user become invalid on their next
request (`E_SESSION_EXPIRED`).

## `user list`

```
  alice (totp)
  bob
```

`(totp)` marks accounts with 2FA enabled.

## Tab completion

`status`, `reload`, `user` → (`add`, `remove`, `list`) are tab-completed.

## Security notes for operators

- **Anyone with `creepercli.admin` can read/write the whole server directory.** Grant it
  sparingly (it is `op`-only by default).
- The console command itself only manages users/config — it cannot run Minecraft commands.
  Allowlisted console commands (via the API) are handled by the `exec` action, see
  [security.md](security.md#console-command-execution).
