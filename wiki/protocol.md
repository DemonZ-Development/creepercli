# Wire protocol

Version 1. JSON lines over TCP: one JSON object per line, UTF-8, `\n`-delimited. No TLS yet. Tunnel with SSH or watch the [roadmap](security.md#roadmap-deferred).

## Frames

### Request (client → server)

```json
{"v":1,"type":"request","id":"<uuid>","action":"fs.ls","params":{...}}
```

| Field | Meaning |
|---|---|
| `v` | Protocol version (1) |
| `type` | `"request"` |
| `id` | Client-chosen correlation id, echoed in the response |
| `action` | One of the actions below |
| `params` | Per-action JSON object |

### Response (server → client)

```json
{"v":1,"type":"response","id":"<same-id>","ok":true,"data":{...}}
```

```json
{"v":1,"type":"response","id":"<same-id>","ok":false,"error":{"code":"E_NOT_FOUND","message":"No such file or directory: /x"}}
```

### Event (server → client, unsolicited)

```json
{"v":1,"type":"event","event":"log.line","data":{"message":"[Server] hi"}}
```

`log.line` is the only event, emitted to subscribers of `monitor.log.start` until `monitor.log.stop`.

## Connection lifecycle

1. Client connects and may send `ping`, `auth.login`, or `auth.resume`.
2. Any other action without a valid session gets `E_UNAUTHORIZED`.
3. The server enforces the connection cap (`E_SERVER_FULL`), payload cap (`E_PAYLOAD_TOO_LARGE`), and per-session rate limit (`E_RATE_LIMITED`).
4. On disconnect the server releases edit locks, aborts transfers, and unsubscribes log streams.

## Actions

### Auth

| Action | Params | Returns |
|---|---|---|
| `ping` | — | `pong`, `serverTime`, `pluginVersion` |
| `auth.login` | `username`, `password`, `totp?` | `token`, `username`, `expiresInMinutes`, `totpEnabled` |
| `auth.resume` | `token` | whoami payload, refreshes the session |
| `auth.logout` | `token?` | `ok`, invalidates token or current session |
| `auth.whoami` | — | `username`, `ip`, `cwd`, `expiresInMinutes`, `totpEnabled` |
| `auth.passwd` | `oldPassword`, `newPassword` | `ok`, invalidates other sessions |
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

All paths resolve inside the sandbox. See [security.md](security.md#file-sandbox).

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
| `xfer.list` | `path` | `entries[]`, `truncated` |

Transfers stream in `chunkSize` (64 KiB default) chunks. The server aborts on checksum mismatch (`E_CHECKSUM_MISMATCH`), unknown transfer (`E_NO_TRANSFER`), or size cap (`E_PAYLOAD_TOO_LARGE`).

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
| `monitor.top` | — | data series for the live dashboard |
| `monitor.log.start` | `grep?` | `grep`, `buffer[]` (historical lines, then live events) |
| `monitor.log.stop` | — | `ok` |

## Error codes

| Code | Meaning |
|---|---|
| `E_UNAUTHORIZED` | No valid session for this action |
| `E_AUTH_FAILED` | Bad credentials, or password mismatch in passwd/totp flows |
| `E_TOTP_REQUIRED` | TOTP code missing or invalid |
| `E_BANNED` | IP is fail2ban-banned; message includes remaining seconds |
| `E_RATE_LIMITED` | Login limiter or per-session command rate limit hit |
| `E_SESSION_EXPIRED` | Token expired or revoked |
| `E_INVALID_PARAMS` | Missing or invalid parameters |
| `E_BAD_REQUEST` | Malformed frame |
| `E_PATH_ESCAPE` | Path resolved outside the sandbox |
| `E_NOT_FOUND` | No such file or directory |
| `E_IS_DIRECTORY` | Expected a file, got a directory |
| `E_NOT_DIRECTORY` | Expected a directory |
| `E_ALREADY_EXISTS` | Target exists (push without `--force`, mkdir/cp collisions) |
| `E_FORBIDDEN` | Action not permitted for this session |
| `E_IO` | Filesystem error |
| `E_LOCKED` | File locked by another editor |
| `E_ALLOWLIST_DENIED` | Console command not in the exec allowlist |
| `E_TIMEOUT` | Client- or server-side timeout |
| `E_PAYLOAD_TOO_LARGE` | Frame or transfer above a size cap |
| `E_CHECKSUM_MISMATCH` | SHA-256 mismatch on push or pull |
| `E_NO_TRANSFER` | Unknown transferId |
| `E_INTERNAL` | Unexpected server error |
| `E_SERVER_FULL` | Connection cap reached |

The CLI renders errors as `message [hint] (CODE)`, for example `Error: Remote file exists (pass force to overwrite) (E_ALREADY_EXISTS)`.

## Notes for client implementers

- Requests pipeline; multiple can be in flight, and responses carry the request `id`.
- Frames must not exceed `network.max-payload-bytes`.
- `auth.resume` is the recommended first call when a token is cached.
- Unknown actions return `E_INTERNAL`; unknown frames are ignored.