'use strict';

const { formatStats, formatTps } = require('../formatters');
const { runTop } = require('../top');
const { EVENT_LOG_LINE } = require('../protocol');

async function stats(ctx) {
  const res = await ctx.client.request('monitor.stats');
  console.log(formatStats(res));
}

async function tps(ctx) {
  const res = await ctx.client.request('monitor.tps');
  console.log(formatTps(res));
}

async function top(ctx) {
  await runTop(ctx.client, ctx.cfg.topRefreshMs);
}

async function log(ctx, args) {
  let grep = null;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--grep') grep = args[++i];
    else if (args[i].startsWith('--grep=')) grep = args[i].slice(7);
  }
  const res = await ctx.client.request('monitor.log.start', { grep });
  console.log(`Streaming console log (Ctrl+C to stop)${res.grep ? ' filtered by "' + res.grep + '"' : ''}`);
  if (res.buffer && res.buffer.length) console.log(res.buffer.join('\n'));
  let stop = null;
  try {
    await new Promise((resolve) => {
      const onLine = (event, data) => {
        if (event === EVENT_LOG_LINE) console.log(data.message);
      };
      ctx.client.on('event', onLine);
      stop = () => {
        ctx.client.removeListener('event', onLine);
        ctx.client.request('monitor.log.stop').catch(() => {});
        resolve();
      };
      process.once('SIGINT', stop);
      ctx.interruptHandler = stop;
    });
  } finally {
    if (stop) {
      process.removeListener('SIGINT', stop);
      if (ctx.interruptHandler === stop) ctx.interruptHandler = null;
    }
  }
}

module.exports = { stats, tps, top, log };
