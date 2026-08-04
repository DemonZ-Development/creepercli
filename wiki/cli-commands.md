# CLI commands, complete reference

Install the CLI with `npm install -g creepercli`.

```
creepercli <command> [args...] [--host HOST] [--port PORT]   # one-shot mode
creepercli [repl]                                            # interactive shell (default)
```

One-shot mode runs a single command and exits. Running `creepercli` or `creepercli login` enters the interactive REPL.

## Global flags

Available on every invocation, anywhere in the argument list:

| Flag | Meaning | Default |
|---|---|---|
| `--host <ip>` | Server address | saved server IP |
| `--port <port>` | Server port | `45678` (or saved port) |
| `--editor <cmd>` | Editor program for `edit`, args allowed | `$EDITOR` or `vi` |
| `--refresh <ms>` | Refresh interval for `top` | 2000 |
| `--yes`, `-y` | Auto-confirm `csync` prompts | off |
| `--help`, `-h` | Show usage and exit | — |

A successful `login` saves `--host` and `--port` to `~/.creepercli/config.json`, so you type them once.

## Authentication

### `login [--host H] [--port P]`
Authenticates, stores the session token in `~/.creepercli/creds`, and enters the REPL. Prompts for username and hidden password; prompts for the 6-digit code when the account has 2FA.

```text
Authenticated as admin. Session valid for 15 minutes.
Entering interactive REPL shell...

admin@89.106.64.70:/>
```

### `logout`
Invalidates the token on the server and deletes local credentials.

### `whoami`
Shows the session: username, IP, cwd, expiry, 2FA status.

### `passwd`
Changes your password (current + new, prompted). Other sessions for this user are invalidated.

### `totp setup` / `totp disable`
Turns 2FA on or off. `setup` prints a secret and `otpauth://` URL valid for 10 minutes; `disable` asks for your password and a valid code.

### `ping`
Connectivity check: prints `pong` with server time and plugin version.

## Filesystem (sandboxed)

All paths are relative to the sandbox root, the server root directory by default. Absolute paths start at the jail root; `~` expands to it. `..` and symlinks that escape are rejected (`E_PATH_ESCAPE`). Your cwd persists per session token on the server.

| Command | Flags | Description |
|---|---|---|
| `pwd` | — | Print working directory |
| `ls [path]` | `-l`, `-a` | List a directory |
| `cd <path>` | — | Change directory (`cd ~` / `cd /` go to the jail root) |
| `tree [path]` | `--depth N` / `-d N` | Directory tree, default depth 3 |
| `cat <path>` | — | Print a file |
| `head <path>` / `tail <path>` | `-n N` | First / last N lines, default 10 |
| `wc <path>` | — | Lines / words / chars / bytes |
| `touch <path>` | — | Create an empty file |
| `mkdir <path>` | `-p` | Create a directory, parents with `-p` |
| `rm <path>` | `-r` | Remove a file or tree |
| `cp <src> <dst>` | `-r` | Copy a file or directory |
| `mv <src> <dst>` | — | Move or rename |
| `info <path>` | — | Type, permissions, size, mtime |
| `edit <path>` | — | Pull, edit in `$EDITOR`, push back under an exclusive lock |

## Search

```
creepercli grep [options] <pattern> [path]
```

Case-sensitive recursive search. Flags: `-i`, `-r`, `-n`, `--max-depth N`, `--max-results N`.

```
creepercli find [path] [--glob <pattern>] [--max-depth N]
```

## Console commands (allowlisted)

| Command | Description |
|---|---|
| `exec <minecraft command...>` | Run an allowlisted console command |
| `say <message...>` | Shorthand for `exec say <message...>` |
| `restart [reason...]` | Shorthand for `exec restart` |

Only commands matching `exec.allowlist` in `config.yml` run; everything else gets `E_ALLOWLIST_DENIED`.

## Monitoring

| Command | Description |
|---|---|
| `stats` | CPU load, process CPU, memory heap, disk, Java version |
| `tps` | 1m / 5m / 15m TPS with a health bar |
| `log [--grep <regex>]` | Stream the server console live, Ctrl+C to stop |
| `top [--refresh <ms>]` | Live dashboard, `q` to quit |

## File transfers

| Command | Description |
|---|---|
| `cpush <local-file> <remote-path>` | Upload with SHA-256 verification; `--force` overwrites |
| `cpull <remote-path> <local-file>` | Download with SHA-256 verification |
| `csync <remote-dir> <local-dir>` | Two-way sync by size and mtime; `-y` / `--yes` skips prompts |

## REPL

```text
admin@89.106.64.70:/> ls /
plugins/  logs/  ...
admin@89.106.64.70:/> q
```

- Prompts show `user@host:cwd>`.
- Full line editing and history persist in `~/.creepercli/history`.
- Accidental `creepercli` prefixes inside the REPL are stripped (`creepercli top` runs `top`).
- `q`, `quit`, or `exit` leaves cleanly.
