'use strict';

async function exec(ctx, args) {
  if (!args.length) throw new Error('Usage: exec <minecraft command>');
  const command = args.join(' ');
  const res = await ctx.client.request('exec.run', { command }, { timeoutMs: 150000 });
  if (res.lines && res.lines.length) console.log(res.lines.join('\n'));
  if (!res.success) console.error('Command reported failure');
  if (res.elapsedMs != null) console.error(`(completed in ${res.elapsedMs}ms)`);
  return res.success ? 0 : 1;
}

function say(ctx, args) {
  return exec(ctx, ['say', ...args]);
}

function restart(ctx, args) {
  return exec(ctx, ['restart', ...args]);
}

module.exports = { exec, say, restart };
