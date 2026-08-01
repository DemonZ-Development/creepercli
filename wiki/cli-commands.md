# CLI commands — complete reference

Run the CLI with `node bin/creepercli.js` (alias it to `creepercli` for readability).

```
creepercli <command> [args...] [--host HOST] [--port PORT]   # one-shot mode
creepercli [repl]                                            # interactive shell (default)
```

One-shot mode: runs a single command and exits. No command given → drops into the REPL.

## Global flags

Available on every invocation (put them anywhere in the argument list):

| Flag | Meaning | Default |
|---|---|---|
| `--host <ip>` | Server address | `127.0.0.1` |
| `--port <port>` | Server port | `45678` |
| `--editor <cmd>` | Editor program used by `edit` (can include args) | `$EDITOR` or `vi` |
| `--refresh <ms>` | Refresh interval for `top` | 2000 ms |
| `--yes`, `-y` | Auto-confirm transfers (`csync` prompts) | off |
| `--help`, `-h` | Show usage and exit | — |

`--host`/`--port` are also remembered per-invocation via the login session file.

---

## Authentication

### `login`
Log in and store a session token in `~/.creepercli/creds`.

```
creepercli login [--host H] [--port P]
```

Prompts for username + password (hidden). If the account has TOTP enabled, prompts for the
6-digit code. Output: `Authenticated as <name>. Session valid for 15 minutes.`

### `logout`
Invalidate the session token **on the server** and delete the local `~/.creepercli/creds` file.

```
creepercli logout
```

### `whoami`
Show current session: username, IP, cwd, expiry, 2FA status.

```
creepercli whoami
# User test (IP 127.0.0.1), cwd /, session expires in 15m, 2FA disabled
```

### `passwd`
Change your password (prompts for current + new password). Must be ≥ 8 characters and not
identical to the username. All **other** sessions for this user are invalidated.

```
creepercli passwd
```

### `totp`
Manage two-factor authentication.

```
creepercli totp setup     # generates a secret + otpauth:// URL, valid 10 minutes
creepercli totp disable   # prompts password + current 6-digit code, disables 2FA
```

`totp setup` prints `secret` and `otpauthUrl` — scan the URL with any authenticator app, then
enter the code (must be done within 10 minutes):

```
creepercli totp verify    # after setup, confirm with the 6-digit code
```

Once enabled, every `login` requires the code.

### `ping`
Connectivity check: prints `pong` with server time and plugin version.

---

## Filesystem (sandboxed)

All paths are relative to the **sandbox root** (the server root directory by default).
Absolute paths starting with `/` are relative to the jail root — you cannot reach anything
outside it. `..` and symlinks that would escape are rejected (`E_PATH_ESCAPE`).

Your current directory (cwd) persists **per session token** on the server, so a `cd` in one
invocation carries over to the next as long as you use the same stored session.

### `pwd`
Print working directory. Also syncs your local notion of cwd.

### `ls [path]` — flags `-l`, `-a`
List a directory (default `.`).

```
creepercli ls /
# cache/  config/  logs/  plugins/  server.properties  ...

creepercli ls -l /        # long format: perms, size, mtime
creepercli ls -la plugins # combined flags; hidden files with -a
```

### `cd <path>`
Change directory (no output on success). `cd /` goes to the jail root.

### `tree [path]` — flag `--depth N` / `-d N`
Directory tree, default depth 3.

```
creepercli tree / --depth 2
```

### `cat <path>`
Print a file to stdout. Large files are truncated server-side with a `(truncated: file is X)`
note on stderr.

### `head <path>` / `tail <path>` — flag `-n N`
First/last N lines (default 10).

```
creepercli tail -n 50 logs/latest.log
creepercli tail -f logs/latest.log   # not supported — use: creepercli log
```

### `wc <path>`
Line / word / character / byte counts.

```
creepercli wc server.properties
# 46 137 1170 1406B server.properties
```

### `touch <path>`
Create an empty file (or update mtime). Prints `touched <path>`.

### `mkdir <path>` — flag `-p`
Create a directory; `-p` creates parents.

### `rm <path>` — flag `-r`
Remove a file or (with `-r`) a directory tree. **No confirmation prompt — be careful.**

### `cp <src> <dst>` — flag `-r`
Copy a file, or a directory with `-r`.

### `mv <src> <dst>`
Move/rename.

### `info <path>`
Metadata: type, perms, size, mtime (and symlink target if applicable).

### `edit <path>`
Pull a file to a local temp dir, open it in `$EDITOR` (or `--editor`), then push it back —
with an exclusive **lock** so no one else edits it concurrently.

```
creepercli edit server.properties --editor "code -w"
EDITOR="sed -i -e 's/motd=.*/motd=My Server/'" creepercli edit server.properties
```

Output: `Pushed 1409 bytes to <path> (+1/-1 lines)` or `No changes.` Lock is released
automatically (also on failure, or after the 5-minute lock TTL).

---

## Search

### `grep [options] <pattern> [path]`
Case-sensitive recursive search, with line numbers, max depth 6, max 1000 results by default.

| Flag | Meaning |
|---|---|
| `-i`, `--ignore-case` | case-insensitive |
| `-r`, `-R`, `--recursive` | recurse into directories (default) |
| `-n`, `--line-number` | prefix lines with numbers (default) |
| `--max-depth N` | limit recursion depth (default 6) |
| `--max-results N` | cap results (default 1000) |

```
creepercli grep -i motd server.properties
creepercli grep --max-depth 3 'creepercli' plugins/
```

### `find [path]` — flag `--glob <pattern>`, `--max-depth N`
Find files matching a glob (default `*`, max depth 12).

```
creepercli find / --glob '**/*.jar'        # all jars under the jail root
creepercli find plugins --glob '**/config.yml' --max-depth 4
```

`**/` matches files at the root level too.

---

## Console command execution (allowlisted)

### `exec <minecraft command...>`
Runs a Minecraft console command **from the allowlist only** (`exec.allowlist` in config.yml,
default: `list`, `whitelist *`, `say *`, `restart`). Anything else → `E_ALLOWLIST_DENIED`.

Output is captured and returned. The command also appears on the console.

```
creepercli exec list
# There are 0 of a max of 20 players online:

creepercli exec whitelist add bob
```

### `say <message...>`
Shorthand for `exec say <message...>` — broadcasts to all players.

### `restart [reason...]`
Shorthand for `exec restart` (only works if `restart` is allowlisted).

---

## Monitoring

### `stats`
CPU load, process CPU %, memory heap, disk usage, Java version.

```
Uptime:       3d 2h 1m
CPU load:     0.05   process: 1.2%
Memory heap:  512M / 1G (max 4G)
Disk (root):  3.2G / 20G (usable 16.4G)
Java:         21.0.4 (8 cores)
```

### `tps`
TPS 1m/5m/15m with a bar, plus current tick time.

```
TPS: 1m 19.8 | 5m 19.9 | 15m 19.8  [████████████████████]  tick 51ms
```

### `log` — flag `--grep <regex>` / `--grep=<regex>`
Streams the server console in real time (Ctrl+C to stop). Sends the last buffered lines first,
then pushes `log.line` events.

```
creepercli log --grep 'player joined'
```

### `top` — flag `--refresh <ms>`
Live dashboard: TPS, tick, memory, players. Press `q` to quit. Works headless too (falls back
to periodic snapshots when stdin is not a TTY).

---

## File transfers

### `cpush <local-file> <remote-path>` — flag `--force`
Upload one file. Computes SHA-256 locally, streams it in chunks (64 KiB by default), verifies
the checksum server-side on finish. Refuses to overwrite unless `--force`.

```
creepercli cpush backup.tgz /backups/backup.tgz --force
# Pushed backup.tgz -> /backups/backup.tgz (12345 bytes, sha256 abc...)
```

### `cpull <remote-path> <local-file>`
Download one file to a `*.creepercli-part` temp file, checksum-verified, then renamed into
place. Partial files are removed on checksum mismatch.

```
creepercli cpull /world/data/level.dat ./level.dat
# Pulled /world/data/level.dat -> ./level.dat (20480 bytes, sha256 def...)
```

### `csync <remote-dir> <local-dir>` — flag `--yes` / `-y`
Two-way directory sync: compares remote and local trees by size/mtime, then SHA-256 when sizes
match. Prompts before uploading/downloading unless `--yes`. Creates the remote directory if
missing.

```
creepercli csync /backups/weekly ./weekly --yes
# Download: 2 file(s), Upload: 1 file(s)
# Pushed ./weekly/new.txt -> /backups/weekly/new.txt (...)
# Pulled /backups/weekly/old.txt -> ./weekly/old.txt (...)
# Sync complete.
```

**Argument order matters: remote first.** (This mirrors `rsync`'s `remote local` convention.)

---

## REPL

```
creepercli repl          # or just: creepercli
test@127.0.0.1:/> ls /
plugins/  logs/  ...
test@127.0.0.1:/> exit
```

- Prompts show `user@host:cwd>`.
- Full line editing + history persisted in `~/.creepercli/history` (last 1000 lines).
- `exit` / `quit` leave; Ctrl+C interrupts the current command (twice to quit).
- Works with piped stdin (commands are serialized): `printf 'ls /\nexit\n' | creepercli repl`.

### `help`
Lists every command with a one-line description. `creepercli help` in one-shot mode.

---

## Exit codes & errors

- `0` — success (`exec` uses 1 for a command that reported failure).
- `1` — failure; message printed to stderr, e.g.:
  ```
  Error: Remote file exists (pass force to overwrite) (E_ALREADY_EXISTS)
  ```
- Every error carries a machine-readable code — see the [error code table](protocol.md#error-codes).
