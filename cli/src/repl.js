 

'use strict';

const fs = require('fs');
const readline = require('readline');
const { COMMANDS, runCommand } = require('./commands');

function prompt(ctx) {
  return `${ctx.username || '?'}@${ctx.cfg.host}:${ctx.cwd || '/'}> `;
}

function startRepl(ctx, io = {}) {
  const input = io.input || process.stdin;
  const output = io.output || process.stdout;
  const completions = COMMANDS.map((c) => c.name);

  function completer(line) {
    const trimmed = line.trimStart();
    const hits = completions.filter((c) => c.startsWith(trimmed));
    return [hits.length ? hits : completions, trimmed];
  }

  const rl = readline.createInterface({
    input,
    output,
    terminal: Boolean(output.isTTY),
    historySize: 1000,
    completer,
  });
  try {
    const history = fs.readFileSync(ctx.cfg.historyFile, 'utf8').split('\n').filter(Boolean).reverse();
    rl.history = history;
  } catch {
  }
  rl.on('history', (history) => {
    try {
      fs.writeFileSync(ctx.cfg.historyFile, history.filter(Boolean).join('\n') + '\n', { mode: 0o600 });
    } catch {
    }
  });

  const queue = [];
  let running = false;
  async function pump() {
    if (running) return;
    running = true;
    while (queue.length) {
      const line = queue.shift();
      await runCommand(ctx, line);
      if (ctx.exit) {
        queue.length = 0;
        rl.close();
        return;
      }
      rl.setPrompt(prompt(ctx));
      rl.prompt();
    }
    running = false;
  }

  rl.setPrompt(prompt(ctx));
  rl.prompt();

  rl.on('line', (line) => {
    queue.push(line);
    pump();
  });

  rl.on('SIGINT', () => {
    if (running) {
      if (ctx.interruptHandler) {
        console.log('\n(interrupted)');
        ctx.interruptHandler();
      } else {
        console.log('\n(interrupt)');
      }
      return;
    }
    rl.question('Really quit? [y/N] ', (answer) => {
      if (/^y/i.test(answer.trim())) rl.close();
      else rl.prompt();
    });
  });

  rl.on('close', async () => {
    await ctx.client.flush();
    ctx.client.close();
  });

  return rl;
}

module.exports = { startRepl, prompt };
