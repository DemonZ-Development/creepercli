# Wire protocol

Version 1. JSON lines over TCP — one JSON object per line, UTF-8, `\n`-delimited. No TLS yet
(see [security.md](security.md#on-the-roadmap)).

## Frames

### Request (client → server)

```json
{"v":1,"type":"request","id":"<uuid>","action":"fs.ls","params":{...}}
```

| Field | Meaning |
|---|---|
| `v` | protocol version (1) |
| `type` | `"request"` |
| `id` | client-chosen correlation id (any string; echoed in the response) |
| `action` | one of the actions below |
| `params` | per-action JSON object |

### Response (server → client)

```json
{"v":1,"type":"response","id":"<same-uuid>","ok":true,"data":{...}}
```

```json
{"v":1,"type":"response","id":"<same-uuid>","ok":false,"error":{"code":"E_NOT_FOUND","message":"No such file or directory: /x"}}
```

### Event (server → client, unsolicited)

```json
{"v":1,"type":"event","event":"log.line","data":{"message":"[Server] hi"}}
```

`log.line` is the only event: emitted to subscribers of `monitor.log.start` until
`monitor.log.stop`.

## Connection lifecycle

1. Client connects, may send `ping` / `auth.login` / `auth.resume`.
2. Any other action without a valid session → `E_UNAUTHORIZED`.
3. Server enforces: global connection cap (`E_SERVER_FULL`), payload cap
   (`E_PAYLOAD_TOO_LARGE`), per-session rate limit (`E_RATE_LIMITED`).
4. Disconnect: server cleans up edit locks, aborts transfers, unsubscribes log streams.

## Actions

### Auth

| Action | Params | Returns |
|---|---|---|
| `ping` | — | `pong`, `serverTime`, `pluginVersion` |
| `auth.login` | `username`, `password`, `totp?` | `token`, `username`, `expiresInMinutes`, `totpEnabled` |
| `auth.resume` | `token` | whoami payload (validates + refreshes the session) |
| `auth.logout` | `token?` | `ok` (invalidates token or current session) |
| `auth.whoami` | — | `username`, `ip`, `cwd`, `expiresInMinutes`, `totpEnabled` |
| `auth.passwd` | `oldPassword`, `newPassword` | `ok` (invalidates other sessions) |
| `auth.totp.setup` | `password` | `secret`, `otpauthUrl`, `expiresInSeconds` (600) |
| `auth.totp.verify` | `code` | `ok`, `totpEnabled` |
| `auth.totp.disable` | `password`, `code` | `ok`, `totpEnabled` |

### Filesystem (`fs.*`)

| Action | Params | Returns |
|---|---|---|
| `fs.pwd` | — | `cwd` |
| `fs.ls` | `path`, `long?`, `all?` | `cwd`, `entries[]` (`name`, `isDir`, `size`, `mtime`, `perms`, `link?`) |
| `fs.cd` | `path` | `cwd` |
| `fs.tree` | `path`, `depth` | `children[]` (nested) |
| `fs.cat` | `path` | `content`, `size`, `truncated` |
| `fs.head` | `path`, `lines` | `lines[]`, `truncated` |
| `fs.tail` | `path`, `lines` | `lines[]`, `truncated` |
| `fs.wc` | `path` | `lines`, `words`, `chars`, `bytes` |
| `fs.touch` | `path` | `path` |
| `fs.mkdir` | `path`, `parents?` | `ok` |
| `fs.rm` | `path`, `recursive?` | `ok` |
| `fs.cp` | `src`, `dst`, `recursive?` | `ok` |
| `fs.mv` | `src`, `dst` | `ok` |
| `fs.info` | `path` | `path`, `isDir`, `size`, `mtime`, `perms`, `link?` |
| `fs.grep` | `path`, `pattern`, `caseInsensitive`, `recursive`, `lineNumbers`, `maxDepth`, `maxResults` | `matches[]` (`path`, `line`, `text`), `truncated` |
| `fs.find` | `path`, `glob`, `maxDepth` | `results[]`, `truncated` |
| `fs.edit.lock` | `path` | `ok` |
| `fs.edit.push` | `path`, `content`, `sha256` | `bytes`, `path` |
| `fs.edit.unlock` | `path` | `ok` |

All paths resolve inside the sandbox (see [security.md](security.md#file-sandbox)).

### Transfers (`xfer.*`)

| Action | Params | Returns |
|---|---|---|
| `xfer.push.start` | `path`, `size`, `sha256`, `force?` | `transferId`, `chunkSize` |
| `xfer.push.chunk` | `transferId`, `index`, `data` (base64) | `ok` |
| `xfer.push.finish` | `transferId`, `sha256` | `target`, `size`, `sha256` |
| `xfer.pull.start` | `path` | `transferId`, `size`, `sha256`, `chunkSize` |
| `xfer.pull.chunk` | `transferId`, `index` | `data` (base64) |
| `xfer.pull.finish` | `transferId`, `sha256` | `sha256` |
| `xfer.abort` | `transferId` | `ok` |
| `xfer.list` | `path` | `entries[]` (see `fs.ls`), `truncated` |

Transfers stream in `chunkSize` (64 KiB default) chunks; the server aborts on checksum
mismatch (`E_CHECKSUM_MISMATCH`), missing transfer (`E_NO_TRANSFER`), or size cap
(`E_PAYLOAD_TOO_LARGE`).

### Console execution

| Action | Params | Returns |
|---|---|---|
| `exec.run` | `command` | `lines[]`, `success`, `elapsedMs` |

Gated by the allowlist (`E_ALLOWLIST_DENIED`).

### Monitoring

| Action | Params | Returns |
|---|---|---|
| `monitor.stats` | — | `uptimeMs`, `memory{}`, `cpu{}`, `disk{}`, `jvm{}` |
| `monitor.tps` | — | `tps1m`, `tps5m`, `tps15m`, `tickMs` |
| `monitor.top` | — | (data series for the live dashboard) |
| `monitor.log.start` | `grep?` | `grep`, `buffer[]` (historical lines, then live events) |
| `monitor.log.stop` | — | `ok` |

## Error codes

| Code | Meaning |
|---|---|
| `E_UNAUTHORIZED` | No valid session for this action |
| `E_AUTH_FAILED` | Bad credentials (or password mismatch in passwd/totp flows) |
| `E_TOTP_REQUIRED` | TOTP code missing/invalid (login or totp flows) |
| `E_BANNED` | IP is fail2ban-banned (message includes remaining seconds) |
| `E_RATE_LIMITED` | Login limiter or per-session command rate limit hit |
| `E_SESSION_EXPIRED` | Token expired or revoked |
| `E_INVALID_PARAMS` | Missing/invalid parameters |
| `E_BAD_REQUEST` | Malformed frame |
| `E_PATH_ESCAPE` | Path resolved outside the sandbox |
| `E_NOT_FOUND` | No such file/directory |
| `E_IS_DIRECTORY` | Expected a file, got a directory (e.g. `cat` on a dir, `rm` without `-r`) |
| `E_NOT_DIRECTORY` | Expected a directory |
| `E_ALREADY_EXISTS` | Target exists (push without `--force`, mkdir/cp collisions) |
| `E_FORBIDDEN` | Action not permitted for this session |
| `E_IO` | Filesystem error |
| `E_LOCKED` | File is locked by another editor |
| `E_ALLOWLIST_DENIED` | Console command not in the exec allowlist |
| `E_TIMEOUT` | Client- or server-side timeout |
| `E_PAYLOAD_TOO_LARGE` | Frame/transfer exceeds a size cap |
| `E_CHECKSUM_MISMATCH` | SHA-256 mismatch on push/pull |
| `E_NO_TRANSFER` | transferId unknown |
| `E_INTERNAL` | Unexpected server error |
| `E_SERVER_FULL` | Connection cap reached |

The CLI renders errors as `message [hint] (CODE)` — e.g.
`Error: Remote file exists (pass force to overwrite) (E_ALREADY_EXISTS)`.

## Notes for client implementers

- Requests are pipelined (multiple in flight); responses carry the request `id`.
- Frames must not exceed `network.max-payload-bytes`.
- `auth.resume` is the recommended first call after connect when a token is cached.
- Unknown actions → `E_INTERNAL`; unknown frames are ignored.
