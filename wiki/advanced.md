# Advanced usage

Practical workflows beyond the basics. Assume `creepercli` is aliased and you're logged in.

## Editing remote files with your own tools

`edit` downloads to a private temp dir, opens your `$EDITOR`, and pushes back on save:

```bash
creepercli edit server.properties                          # vi (default)
creepercli edit config/paper-global.yml --editor "code -w" # VS Code, wait on close
EDITOR="nano" creepercli edit whitelist.json
EDITOR="sed -i -e 's/motd=.*/motd=Our Server/'" creepercli edit server.properties
```

Notes:

- The editor can be any command line including args (it runs through a shell).
- An exclusive lock prevents concurrent edits; a second `edit` on the same file reports
  `E_LOCKED` until the first finishes (or 5 min TTL / disconnect).
- Output shows the diff summary: `Pushed 1409 bytes to <path> (+1/-1 lines)`.
- No changes → `No changes.` and nothing is uploaded.

## Backups with `cpush` / `cpull`

```bash
# snapshot world data to the server (idempotent)
creepercli cpush world/level.dat /backups/$(date +%F)/level.dat --force

# pull a crash report for inspection
creepercli cpull /crash-reports/crash-2026-08-01_12.34.56-server.txt ./crash.txt
```

Transfer features: chunked streaming, local + server-side SHA-256 verification, partial-file
cleanup on mismatch, `--force` for overwrite, 1 GiB cap per transfer.

## Two-way sync with `csync`

```bash
# local config templates <-> server config (remote dir created if missing)
creepercli csync /server-config ./config-templates --yes

# weekly backup mirror (local is a plain folder, remote is authoritative)
creepercli csync /backups/weekly ./weekly --yes
```

How it decides:

1. list remote, walk local;
2. identical size + mtime → skip;
3. equal size but unknown content → SHA-256 both sides, skip if equal;
4. remote newer → download, local newer → upload;
5. files present on only one side → transfer to the other.

Without `--yes` you get a prompt: `Upload 3 local file(s)? [y/N]`. `-y`/`--yes` skips all
prompts (scripting). **Remote path comes first.**

## Console commands and output

```bash
creepercli exec list
creepercli exec whitelist add friend_name
creepercli say Maintenance in 10 minutes!
```

- Only allowlisted commands run (`exec.allowlist` in config.yml — add entries and `/creepercli
  reload` to extend).
- Output is captured server-side from the actual console dispatch (works with multi-line
  output, including colored/component messages rendered as plain text).
- `exec` returns exit code 1 if the command reported failure.

## Monitoring workflows

```bash
# daily check
creepercli stats && creepercli tps

# watch the console for errors
creepercli log --grep 'ERROR|WARN'

# live dashboard (q to quit); useful in tmux
creepercli top --refresh 1
```

## Automation / scripting

One-shot mode is designed for cron and CI. Because the stored session is reused, each command
is just one process:

```bash
# backup every night
0 4 * * * creepercli cpush /world /backups/daily/world.tgz --force >> /var/log/creeper-backup.log 2>&1

# alert when TPS drops
creepercli tps | grep -q "1m 1[0-9]" && echo "TPS low: $(creepercli tps)"
```

Piping works everywhere:

```bash
printf 'test\ntest12345\n' | creepercli ls / --host 127.0.0.1 --port 45678   # credentials via stdin
printf 'ls /\nstats\nexit\n' | creepercli repl                              # piped REPL
```

`--yes` silences `csync` prompts; `--host`/`--port` override the saved endpoint per run.

> Note: each one-shot invocation counts as login + command against the session rate limit
> (default 30/s). For heavy loops, raise `limits.commands-per-second` via `/creepercli reload`.

## Multi-server management

The CLI keyed files (`~/.creepercli/creds`) are per host+port, so you can hold sessions to
several servers at once — just alternate `--host`/`--port`:

```bash
creepercli login --host 127.0.0.1 --port 45678                       # survival server
creepercli login --host 127.0.0.1 --port 45679                       # lobby (tunnel both)
creepercli --port 45679 say Restarting in 5 minutes!
```

## REPL tips

- History survives sessions (`~/.creepercli/history`), searchable with arrow keys.
- cwd persists server-side per session token — `cd` stays until you `logout` or the session
  expires.
- `help` lists all commands; `exit`/`quit` leave.
