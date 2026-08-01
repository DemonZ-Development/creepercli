'use strict';

const fs = require('fs');
const readline = require('readline');
const { runCommand } = require('./commands');

function prompt(ctx) {
  return `${ctx.username || '?'}@${ctx.cfg.host}:${ctx.cwd || '/'}> `;
}

function startRepl(ctx) {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: true,
    historySize: 1000,
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

  let busy = false;
  rl.setPrompt(prompt(ctx));
  rl.prompt();

  rl.on('line', async (line) => {
    if (busy) return;
    busy = true;
    rl.pause();
    await runCommand(ctx, line);
    if (ctx.exit) {
      rl.close();
      return;
    }
    rl.setPrompt(prompt(ctx));
    busy = false;
    rl.prompt();
  });

  rl.on('SIGINT', () => {
    if (busy) {
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

  rl.on('close', () => {
    ctx.client.close();
    process.exit(0);
  });
}

module.exports = { startRepl, prompt };
