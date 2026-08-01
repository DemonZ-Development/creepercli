'use strict';

const { CreeperClient } = require('./client');
const { load: loadConfig, applyFlags } = require('./config');
const session = require('./session');
const { startRepl } = require('./repl');
const { runCommand } = require('./commands');
const { doLogin } = require('./commands/auth');

async function main(argv) {
  const flags = parseFlags(argv);
  const cfg = applyFlags(loadConfig(), flags);
  if (flags.help) {
    printUsage();
    return 0;
  }
  const args = flags.args;
  const cmd = args[0];

  if (cmd === 'login') {
    const client = await connect(cfg);
    try {
      await doLogin({ client, cfg, username: null, cwd: '/' });
    } finally {
      client.close();
    }
    return 0;
  }

  if (cmd === 'logout') {
    const client = await connect(cfg);
    try {
      const creds = session.load(cfg.host, cfg.port);
      if (creds) {
        await runCommand({ client, cfg, username: creds.username, cwd: '/' }, 'logout');
      } else {
        console.log('No local session to clear.');
      }
    } finally {
      client.close();
    }
    return 0;
  }

  const client = await connect(cfg);
  const ctx = { client, cfg, username: null, cwd: '/', exit: false };
  try {
    let creds = session.load(cfg.host, cfg.port);
    if (creds) {
      ctx.username = creds.username;
      try {
        const resume = await client.request('auth.resume', { token: creds.token }, { timeoutMs: 15000 });
        ctx.username = resume.username;
        ctx.cwd = resume.cwd || '/';
      } catch (err) {
        if (err.code === 'E_SESSION_EXPIRED' || err.code === 'E_UNAUTHORIZED') {
          session.clear();
          creds = null;
          console.log('Session expired, please log in.');
        } else {
          throw err;
        }
      }
    }
    if (!creds) {
      if (!process.stdout.isTTY) {
        console.error('Not authenticated and stdin is not a TTY. Run "creepercli login" first.');
        return 1;
      }
      await doLogin(ctx);
    }
    if (!cmd || cmd === 'repl' || cmd === 'shell') {
      startRepl(ctx);
      return undefined;
    }
    return await runCommand(ctx, args.join(' '));
  } finally {
    client.close();
  }
}

function parseFlags(argv) {
  const flags = { args: [], host: null, port: null, editor: null, refresh: null, yes: false, help: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--host') flags.host = argv[++i];
    else if (a === '--port') flags.port = parseInt(argv[++i], 10);
    else if (a === '--editor') flags.editor = argv[++i];
    else if (a === '--refresh') flags.refresh = parseInt(argv[++i], 10);
    else if (a === '--yes' || a === '-y') flags.yes = true;
    else if (a === '--help' || a === '-h') flags.help = true;
    else flags.args.push(a);
  }
  return flags;
}

async function connect(cfg) {
  const client = new CreeperClient({ host: cfg.host, port: cfg.port });
  try {
    await client.connect();
    return client;
  } catch (err) {
    console.error(`Cannot connect to ${cfg.host}:${cfg.port} — ${err.code || err.message}`);
    console.error('Is the CreeperCLI plugin running and is the port reachable? (use an SSH tunnel)');
    process.exit(1);
  }
}

function printUsage() {
  console.log(`CreeperCLI v1.0.0 — remote Minecraft administration

Usage:
  creepercli login                       Authenticate with the server
  creepercli logout                      Clear the local session
  creepercli <command> [args...]         Run a single command
  creepercli repl                        Interactive shell (default)

Options:
  --host <ip>      server address (default 127.0.0.1)
  --port <port>    server port (default 45678)
  --editor <cmd>   editor for "edit" (default $EDITOR or vi)
  --refresh <ms>   dashboard refresh rate for "top"
  --yes / -y       auto-confirm transfers (csync)
  --help / -h      show this help

Run "help" inside the REPL for the full command reference.`);
}

module.exports = { main };
