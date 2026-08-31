 

'use strict';

const { CreeperClient } = require('./client');
const { load: loadConfig, applyFlags } = require('./config');
const session = require('./session');
const { startRepl } = require('./repl');
const { runCommand } = require('./commands');
const { doLogin } = require('./commands/auth');
const { friendly } = require('./protocol');
const { checkUpdate } = require('./update');

async function main(argv) {
  checkUpdate();
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
    const ctx = { client, cfg, username: null, cwd: '/', exit: false, flags };
    try {
      const res = await doLogin(ctx);
      if (res && res.token && process.stdout.isTTY) {
        console.log('Entering interactive REPL shell (type "help" for commands, "exit" or "q" to quit)...\n');
        startRepl(ctx);
        return 0;
      }
    } catch (err) {
      console.error(`\nError: ${friendly(err)}\n`);
      client.close();
      process.exit(1);
    } finally {
      if (!ctx.exit && !process.stdout.isTTY) {
        client.close();
      }
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
  const ctx = { client, cfg, username: null, cwd: '/', exit: false, flags };
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
      client.close();
      return 1;
    }
    await doLogin(ctx);
  }
  if (!cmd || cmd === 'repl' || cmd === 'shell') {
    startRepl(ctx);
    return 0;
  }
  try {
    return await runCommand(ctx, args.join(' '));
  } finally {
    client.close();
  }
}

function parseFlags(argv) {
  const flags = { args: [], host: null, port: null, editor: null, refresh: null, yes: false, help: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--host') {
      if (i + 1 < argv.length) flags.host = argv[++i];
    } else if (a === '--port') {
      if (i + 1 < argv.length) flags.port = parseInt(argv[++i], 10);
    } else if (a === '--editor') {
      if (i + 1 < argv.length) flags.editor = argv[++i];
    } else if (a === '--refresh') {
      if (i + 1 < argv.length) flags.refresh = parseInt(argv[++i], 10);
    } else if (a === '--yes' || a === '-y') {
      flags.yes = true;
    } else if (a === '--help' || a === '-h') {
      flags.help = true;
    } else {
      flags.args.push(a);
    }
  }
  return flags;
}

async function connect(cfg) {
  const client = new CreeperClient({ host: cfg.host, port: cfg.port });
  try {
    await client.connect();
    return client;
  } catch (err) {
    const codeStr = err.code || err.message;
    console.error(`\nError: Cannot connect to server at ${cfg.host}:${cfg.port} (${codeStr})\n`);
    console.error(`--------------------------------------------------------------------------------`);
    console.error(`CREEPER CLI SETUP & TROUBLESHOOTING GUIDE`);
    console.error(`--------------------------------------------------------------------------------\n`);
    console.error(`1. INSTALL THE PLUGIN ON YOUR MINECRAFT SERVER:`);
    console.error(`   * Place CreeperCLI-1.0.0.jar inside your server's 'plugins/' folder.`);
    console.error(`   * Start or restart your server (Paper / Spigot 1.21+).\n`);
    console.error(`2. CREATE YOUR ADMIN USER IN SERVER CONSOLE:`);
    console.error(`   * Open your server console (or run in-game as OP):`);
    console.error(`     /creepercli user add <username> <password>\n`);
    console.error(`3. VERIFY NETWORK & PORT FORWARDING:`);
    console.error(`   * CreeperCLI listens on TCP port ${cfg.port} by default.`);
    console.error(`   * If connecting to a remote server, specify host & port flags:`);
    console.error(`     creepercli login --host <your-server-ip> --port ${cfg.port}`);
    console.error(`   * Or set up an SSH tunnel:`);
    console.error(`     ssh -L ${cfg.port}:127.0.0.1:${cfg.port} user@<your-server-ip>\n`);
    console.error(`--------------------------------------------------------------------------------`);
    console.error(`Documentation: https://github.com/DemonZ-Development/creepercli\n`);
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
