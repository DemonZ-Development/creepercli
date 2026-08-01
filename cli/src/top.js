'use strict';

const readline = require('readline');
const { formatStats, formatTps } = require('./formatters');

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function runTop(client, refreshMs) {
  readline.emitKeypressEvents(process.stdin);
  process.stdin.setRawMode(true);
  let stopped = false;
  const onKey = (str, key) => {
    if (key.name === 'q' || key.name === 'escape' || (key.ctrl && key.name === 'c')) stopped = true;
  };
  process.stdin.on('keypress', onKey);
  try {
    while (!stopped) {
      const res = await client.request('monitor.top', {});
      readline.cursorTo(process.stdout, 0, 0);
      readline.clearScreenDown(process.stdout);
      console.log('CreeperCLI top (q to quit)\n');
      console.log(formatStats(res.stats));
      console.log();
      console.log(formatTps(res.tps));
      await sleep(refreshMs);
    }
  } finally {
    process.stdin.removeListener('keypress', onKey);
    process.stdin.setRawMode(false);
  }
}

module.exports = { runTop };
