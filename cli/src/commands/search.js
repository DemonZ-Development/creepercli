 

'use strict';

const { formatMatches } = require('../formatters');

async function grep(ctx, args) {
  const opts = {
    caseInsensitive: false,
    recursive: true,
    lineNumbers: true,
    maxDepth: 6,
    maxResults: 1000,
  };
  const rest = [];
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a === '-i' || a === '--ignore-case') opts.caseInsensitive = true;
    else if (a === '-r' || a === '-R' || a === '--recursive') opts.recursive = true;
    else if (a === '-n' || a === '--line-number') opts.lineNumbers = true;
    else if (a === '--max-depth') opts.maxDepth = parseInt(args[++i], 10) || 6;
    else if (a === '--max-results') opts.maxResults = parseInt(args[++i], 10) || 1000;
    else rest.push(a);
  }
  const pattern = rest[0];
  const path = rest[1] || '.';
  if (!pattern) throw new Error('Usage: grep [options] <pattern> [path]');
  const res = await ctx.client.request('fs.grep', { path, pattern, ...opts });
  if (!res.matches.length) {
    console.log('No matches');
    return;
  }
  console.log(formatMatches(res.matches));
  if (res.truncated) console.error(`(truncated at ${res.matches.length} matches)`);
}

async function find(ctx, args) {
  let glob = '*';
  let path = '.';
  let maxDepth = 12;
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a === '--glob' || a === '-g') glob = args[++i] || '*';
    else if (a === '--max-depth') maxDepth = parseInt(args[++i], 10) || 12;
    else path = a;
  }
  const res = await ctx.client.request('fs.find', { path, glob, maxDepth });
  if (!res.results.length) {
    console.log('No matches');
    return;
  }
  console.log(res.results.join('\n'));
  if (res.truncated) console.error('(truncated)');
}

module.exports = { grep, find };
