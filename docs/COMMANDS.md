# Command Reference

## CLI commands

Everything works both in the REPL and as one-shot: `creepercli <command> [args...]`.
Shortcuts `say` and `restart` are aliases for `exec say ...` / `exec restart` and still enforce the server allowlist.

### Navigation

| Command | Description |
|---|---|
| `pwd` | Print the current jail path (server-relative). |
| `ls [path] [-l] [-a]` | List entries. `-l` shows permissions/size/date, `-a` includes dotfiles. |
| `cd <path>` | Change directory (`cd /` returns to the jail root). |
| `tree [path] [--depth N]` | Recursive tree (default depth 3, max 6). |

### Reading

| Command | Description |
|---|---|
| `cat <path>` | Print file contents (capped server-side at 1MB; use `cpull` for big/binary files). |
| `head <path> [-n N]` | First N lines (default 10, max 1000). |
| `tail <path> [-n N]` | Last N lines (default 10, max 1000). |
| `wc <path>` | Lines / words / chars / bytes. |
| `info <path>` | Permissions, size, mtime, symlink target. |

### File manipulation

| Command | Description |
|---|---|
| `touch <path>` | Create file or update mtime. |
| `mkdir <path> [-p]` | Create directory (parents with `-p`). |
| `rm <path> [-r]` | Remove; directories need `-r`. The jail root is protected. |
| `cp <src> <dst> [-r]` | Copy; directories need `-r`. |
| `mv <src> <dst>` | Move/rename. |

### Editing

| Command | Description |
|---|---|
| `edit <path>` | Lock the file server-side (5-min TTL), download to a private temp dir, open `$EDITOR`, detect changes (SHA-256), push back atomically, always clean up the temp file. |

If another user holds the lock you get `E_LOCKED` with the owner and remaining time.

### Search

| Command | Description |
|---|---|
| `grep [opts] <pattern> [path]` | Recursive regex search. `-i/--ignore-case`, `-n/--line-number`, `--recursive`, `--max-depth N`, `--max-results N`. Server skips files > 5MB and binary files. |
| `find <path> [--glob "**/*.jar"] [--max-depth N]` | Glob search (matches against file name and relative path). |

### Server control

| Command | Description |
|---|---|
| `exec <command...>` | Run a Minecraft console command (allowlist enforced, 30s timeout). |
| `say <text...>` | Alias → `exec say <text>`. |
| `restart` | Alias → `exec restart` (allowlist must contain it). |

### Monitoring

| Command | Description |
|---|---|
| `stats` | CPU load, process %, heap, disk usage, uptime. |
| `tps` | TPS 1m/5m/15m + tick time. |
| `log [--grep <regex>]` | Live console stream (Ctrl+C to stop). Replays the last 500 lines, then streams in real time from the Bukkit logger. |
| `top` | Live dashboard, refresh every `topRefreshMs` (default 2s), `q` to quit. |

### Transfers

| Command | Description |
|---|---|
| `cpush <local-file> <remote-path>` | Upload with progress; server verifies size + SHA-256. |
| `cpull <remote-path> <local-file>` | Download to `local-file.part`, verify SHA-256, then rename into place. |
| `csync <remote-dir> <local-dir> [--yes]` | Compare via size/mtime/SHA-256; newest wins; prompts before transferring unless `--yes`. |

### Session

| Command | Description |
|---|---|
| `login` | Authenticate; stores token in `~/.creepercli/creds` (mode 600). |
| `logout` | Invalidate token server-side and clear the local creds. |
| `totp setup` | Re-authenticate, print secret + QR, enable 2FA. |
| `totp disable` | Disable 2FA (needs password + code). |
| `passwd` | Change password; invalidates all other sessions of your user. |
| `whoami` | User, IP, cwd, session expiry. |
| `ping` | Round-trip protocol check. |

### Meta

| Command | Description |
|---|---|
| `help` | Command list. |
| `exit` / `quit` | Leave the REPL. |

## NDJSON protocol (v1)

One JSON object per line, max 10MB.

### Request (client → server)

```json
{"v":1,"type":"request","id":"<uuid>","action":"fs.ls","params":{"path":"plugins","long":true}}
```

Every request carries a unique `id` (UUID); the response echoes it so async results (e.g. `exec`) can be matched.

### Response

```json
{"v":1,"type":"response","id":"<uuid>","ok":true,"data":{...}}
```

### Error

```json
{"v":1,"type":"response","id":"<uuid>","ok":false,"error":{"code":"E_PATH_ESCAPE","message":"..."}}
```

### Event (server → client, async)

```json
{"v":1,"type":"event","event":"log.line","data":{"level":"INFO","message":"...","ts":1750000000000}}
```

### Error codes

`E_UNAUTHORIZED` `E_AUTH_FAILED` `E_TOTP_REQUIRED` `E_BANNED` `E_RATE_LIMITED` `E_SESSION_EXPIRED`
`E_INVALID_PARAMS` `E_BAD_REQUEST` `E_PATH_ESCAPE` `E_NOT_FOUND` `E_IS_DIRECTORY` `E_NOT_DIRECTORY`
`E_ALREADY_EXISTS` `E_FORBIDDEN` `E_IO` `E_LOCKED` `E_ALLOWLIST_DENIED` `E_TIMEOUT`
`E_PAYLOAD_TOO_LARGE` `E_CHECKSUM_MISMATCH` `E_NO_TRANSFER` `E_INTERNAL` `E_SERVER_FULL`

### Actions

| Action | Requires session | Purpose |
|---|---|---|
| `ping` | no | liveness + protocol version |
| `auth.login` | no | `{username, password, totp?}` → `{token, username, expiresInMinutes, totpEnabled}` |
| `auth.logout` | yes | invalidate token |
| `auth.whoami` | yes | session info + cwd |
| `auth.passwd` | yes | change password; invalidates other sessions |
| `auth.totp.setup` | yes | `{password}` → `{secret, otpauthUrl}` (10 min pending) |
| `auth.totp.verify` | yes | `{code}` → enables TOTP |
| `auth.totp.disable` | yes | `{password, code}` |
| `fs.pwd` / `fs.ls` / `fs.cd` / `fs.tree` | yes | navigation |
| `fs.cat` / `fs.head` / `fs.tail` / `fs.wc` / `fs.info` | yes | reading |
| `fs.touch` / `fs.mkdir` / `fs.rm` / `fs.cp` / `fs.mv` | yes | manipulation |
| `fs.edit.lock` / `fs.edit.push` / `fs.edit.unlock` | yes | locked editing |
| `fs.grep` / `fs.find` | yes | search |
| `xfer.push.start|chunk|finish` | yes | uploads (64KB base64 chunks) |
| `xfer.pull.start|chunk|finish` | yes | downloads |
| `xfer.abort` / `xfer.list` | yes | abort / recursive listing with SHA-256 |
| `exec.run` | yes | allowlisted console command, async, 30s timeout |
| `monitor.stats` / `monitor.tps` / `monitor.top` | yes | live metrics |
| `monitor.log.start|stop` | yes | console streaming (`--grep` filter) |

Transfer frames: `xfer.push.start` returns `{transferId, chunkSize}`; chunks are `{transferId, index, data(base64)}` and must arrive in order; `xfer.push.finish` verifies the full-file SHA-256. `xfer.pull.chunk` fetches chunk by index; `xfer.pull.finish` reports the client-computed SHA-256 for server-side comparison.
