# creeper-cli

Remote administration CLI for Minecraft servers running the **CreeperCLI** plugin.

```
npm i -g creeper-cli
creepercli login
creepercli repl
```

Works against the plugin's hardened NDJSON TCP endpoint. Requires Node.js 18+ and the CreeperCLI plugin (see the repo root README).

## Commands

See `../docs/COMMANDS.md` for the full reference. Highlights:

```
creepercli ls plugins -l
creepercli cat server.properties
creepercli edit plugins/MyPlugin/config.yml   # opens in your local $EDITOR
creepercli exec whitelist add Steve
creepercli cpush ./MyPlugin.jar plugins/
creepercli cpull logs/latest.log ./latest.log
creepercli csync plugins/ ./backup-plugins/
creepercli log --grep ERROR
creepercli top
```

## Configuration

`~/.creepercli/config.json`:

```json
{ "host": "127.0.0.1", "port": 45678, "editor": "code --wait", "topRefreshMs": 2000 }
```

Flags override per invocation: `--host`, `--port`, `--editor`, `--refresh`, `--yes`.

Session tokens persist in `~/.creepercli/creds` with mode 600. `creepercli logout` clears them.
