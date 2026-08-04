# Advanced usage

Practical workflows beyond the basics. Assume you are logged in.

## Editing remote files with your own tools

`edit` downloads to a private temp dir, opens your `$EDITOR`, and pushes back on save:

```bash
creepercli edit server.properties                          # vi (default)
creepercli edit config/paper-global.yml --editor "code -w" # VS Code, wait on close
EDITOR="nano" creepercli edit whitelist.json
EDITOR="sed -i -e 's/motd=.*/motd=Our Server/'" creepercli edit server.properties
```

Notes:

- The editor can be any command including args; it runs through a shell.
- An exclusive lock prevents concurrent edits. A second `edit` on the same file reports `E_LOCKED` until the first finishes, hits the 5-minute TTL, or the session disconnects.
- Output shows a diff summary: `Pushed 1409 bytes to <path> (+1/-1 lines)`.
- No changes means nothing is uploaded.

## Backups with `cpush` / `cpull`

```bash
# snapshot world data to the server (idempotent)
creepercli cpush world/level.dat /backups/$(date +%F)/level.dat --force

# pull a crash report for inspection
creepercli cpull /crash-reports/crash-2026-08-01_12.34.56-server.txt ./crash.txt
```

Transfers stream in chunks, verify SHA-256 on both ends, clean up partial files on mismatch, cap at 1 GiB per transfer. `--force` overwrites.

## Two-way sync with `csync`

```bash
# local config templates <-> server config, remote dir created if missing
creepercli csync /server-config ./config-templates --yes

# weekly mirror, remote is authoritative
creepercli csync /backups/weekly ./weekly --yes
```

How it decides:

1. list remote, walk local;
2. identical size and mtime → skip;
3. equal size, unknown content → compare SHA-256 both sides, skip if equal;
4. remote newer → download; local newer → upload;
5. present on one side only → transfer to the other.

Without `--yes` you get a prompt. `-y` / `--yes` skips prompts for scripting. Remote path comes first.

## Console commands and output

```bash
creepercli exec list
creepercli exec whitelist add friend_name
creepercli say Maintenance in 10 minutes!
```

- Only allowlisted commands run (`exec.allowlist` in config.yml, `/creepercli reload` applies changes).
- Output is captured server-side from the real console dispatch. Multi-line output works, including colored or component messages rendered as plain text.
- `exec` exits 1 when the command reports failure.

## Monitoring workflows

```bash
# daily check
creepercli stats && creepercli tps

# watch the console for errors
creepercli log --grep 'ERROR|WARN'

# live dashboard, useful inside tmux
creepercli top --refresh 1
```

## Automation and scripting

One-shot mode fits cron and CI. The stored session is reused, so each command is one process:

```bash
# backup every night
0 4 * * * creepercli cpush /world /backups/daily/world.tgz --force >> /var/log/creeper-backup.log 2>&1

# alert when TPS drops
creepercli tps | grep -q "1m 1[0-9]" && echo "TPS low: $(creepercli tps)"
```

Piping works everywhere:

```bash
printf 'test\ntest12345\n' | creepercli ls / --host 127.0.0.1 --port 45678  # credentials via stdin
printf 'ls /\nstats\nexit\n' | creepercli repl                             # piped REPL
```

`--yes` silences `csync` prompts; `--host` / `--port` override the saved endpoint per run.

One-shot invocations count as login plus command against the session rate limit (default 30/s). For heavy loops, raise `limits.commands-per-second` and `/creepercli reload`.

## Multi-server management

The CLI keyed files (`~/.creepercli/creds`) are per host and port, so you can hold sessions to several servers at once:

```bash
creepercli login --host 127.0.0.1 --port 45678   # survival
creepercli login --host 127.0.0.1 --port 45679   # lobby, both via tunnels
creepercli --port 45679 say Restarting in 5 minutes!
```

## REPL tips

- History survives sessions in `~/.creepercli/history`; arrow keys search it.
- cwd persists server-side per session token. `cd` sticks until logout or expiry.
- `help` lists commands; `exit` / `quit` leave.