'use strict';

const { formatLs, formatTree, humanSize, formatInfo } = require('../formatters');
const { editFile } = require('../editor');

async function pwd(ctx) {
  const res = await ctx.client.request('fs.pwd');
  ctx.cwd = res.cwd;
  console.log(res.cwd);
}

async function ls(ctx, args) {
  const flags = args.filter((a) => a.startsWith('-') && a !== '-');
  const longFormat = flags.some((f) => f.includes('l'));
  const all = flags.some((f) => f.includes('a'));
  const path = args.find((a) => !a.startsWith('-')) || '.';
  const res = await ctx.client.request('fs.ls', { path, long: longFormat, all });
  ctx.cwd = res.cwd;
  console.log(formatLs(res.entries, { longFormat }));
}

async function cd(ctx, args) {
  const path = args[0] || '/';
  const res = await ctx.client.request('fs.cd', { path });
  ctx.cwd = res.cwd;
}

async function tree(ctx, args) {
  let depth = 3;
  const pathArgs = [];
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '-d' || args[i] === '--depth') {
      depth = parseInt(args[++i], 10) || 3;
    } else {
      pathArgs.push(args[i]);
    }
  }
  const res = await ctx.client.request('fs.tree', { path: pathArgs[0] || '.', depth });
  console.log(formatTree(res.children || []));
  if (res.truncated) console.log('(tree truncated)');
}

async function cat(ctx, args) {
  if (!args[0]) throw new Error('Usage: cat <path>');
  const res = await ctx.client.request('fs.cat', { path: args[0] });
  process.stdout.write(res.content);
  if (!res.content.endsWith('\n')) process.stdout.write('\n');
  if (res.truncated) console.error(`(truncated: file is ${humanSize(res.size)})`);
}

async function head(ctx, args) {
  const { lines, path } = parseTailArgs(args, 10);
  const res = await ctx.client.request('fs.head', { path, lines });
  console.log(res.lines.join('\n'));
  if (res.truncated) console.error('(truncated)');
}

async function tail(ctx, args) {
  if (args.includes('-f') || args.includes('--follow')) {
    throw new Error('Live follow is available via the "log" command: creepercli log [--grep <regex>]');
  }
  const { lines, path } = parseTailArgs(args, 10);
  const res = await ctx.client.request('fs.tail', { path, lines });
  console.log(res.lines.join('\n'));
  if (res.truncated) console.error('(truncated)');
}

function parseTailArgs(args, def) {
  let n = def;
  let path = null;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '-n') n = parseInt(args[++i], 10) || def;
    else path = args[i];
  }
  if (!path) throw new Error('Usage: <command> [-n N] <path>');
  return { lines: n, path };
}

async function wc(ctx, args) {
  if (!args[0]) throw new Error('Usage: wc <path>');
  const res = await ctx.client.request('fs.wc', { path: args[0] });
  console.log(`${res.lines} ${res.words} ${res.chars} ${humanSize(res.bytes)} ${args[0]}`);
}

async function touch(ctx, args) {
  if (!args[0]) throw new Error('Usage: touch <path>');
  const res = await ctx.client.request('fs.touch', { path: args[0] });
  console.log(`touched ${res.path}`);
}

async function mkdir(ctx, args) {
  const parents = args.includes('-p') || args.includes('--parents');
  const path = args.find((a) => !a.startsWith('-'));
  if (!path) throw new Error('Usage: mkdir [-p] <path>');
  const res = await ctx.client.request('fs.mkdir', { path, parents });
  console.log(`created ${res.path}`);
}

async function rm(ctx, args) {
  const recursive = args.includes('-r') || args.includes('-R') || args.includes('--recursive');
  const path = args.find((a) => !a.startsWith('-'));
  if (!path) throw new Error('Usage: rm [-r] <path>');
  const res = await ctx.client.request('fs.rm', { path, recursive });
  console.log(`removed ${res.path}`);
}

async function cp(ctx, args) {
  const recursive = args.includes('-r') || args.includes('-R') || args.includes('--recursive');
  const rest = args.filter((a) => !a.startsWith('-'));
  if (rest.length < 2) throw new Error('Usage: cp [-r] <src> <dst>');
  const res = await ctx.client.request('fs.cp', { src: rest[0], dst: rest[1], recursive });
  console.log(`copied ${res.src} -> ${res.dst}`);
}

async function mv(ctx, args) {
  if (args.length < 2) throw new Error('Usage: mv <src> <dst>');
  const res = await ctx.client.request('fs.mv', { src: args[0], dst: args[1] });
  console.log(`moved ${res.src} -> ${res.dst}`);
}

async function info(ctx, args) {
  if (!args[0]) throw new Error('Usage: info <path>');
  const res = await ctx.client.request('fs.info', { path: args[0] });
  console.log(formatInfo(res));
}

async function edit(ctx, args) {
  if (!args[0]) throw new Error('Usage: edit <path>');
  return editFile(ctx, args[0]);
}

module.exports = { pwd, ls, cd, tree, cat, head, tail, wc, touch, mkdir, rm, cp, mv, info, edit };
