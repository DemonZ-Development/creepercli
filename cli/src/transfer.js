 

'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { confirm } = require('./prompts');

function sha256File(file) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(file);
    stream.on('data', (d) => hash.update(d));
    stream.on('end', () => resolve(hash.digest('hex')));
    stream.on('error', reject);
  });
}

function showProgress(transferred, total) {
  if (!total) return;
  const pct = Math.floor((transferred / total) * 100);
  const kb = Math.floor(transferred / 1024);
  const totalKb = Math.floor(total / 1024);
  process.stdout.write(`\r${pct}% (${kb}KB / ${totalKb}KB)   `);
}

function clearProgress() {
  process.stdout.write('\r'.padEnd(70) + '\r');
}

async function cpush(ctx, args) {
  const rest = args.filter((a) => !a.startsWith('--'));
  let [local, remote] = rest;
  if (!local || !remote) throw new Error('Usage: cpush <local-file> <remote-path>');
  remote = remote.replace(/\\/g, '/');
  const stat = fs.statSync(local);
  if (!stat.isFile()) throw new Error('Local path must be a file');
  const sha = await sha256File(local);
  const start = await ctx.client.request('xfer.push.start', {
    path: remote,
    size: stat.size,
    sha256: sha,
    force: args.includes('--force'),
  }, { timeoutMs: 60000 });
  const { transferId, chunkSize } = start;
  const fd = fs.openSync(local, 'r');
  let transferred = 0;
  let index = 0;
  try {
    const buf = Buffer.alloc(chunkSize);
    while (true) {
      const n = fs.readSync(fd, buf, 0, chunkSize, transferred);
      if (n === 0) break;
      await ctx.client.request('xfer.push.chunk', {
        transferId,
        index,
        data: buf.subarray(0, n).toString('base64'),
      }, { timeoutMs: 120000 });
      transferred += n;
      index++;
      showProgress(transferred, stat.size);
    }
  } finally {
    fs.closeSync(fd);
  }
  clearProgress();
  const finish = await ctx.client.request('xfer.push.finish', { transferId, sha256: sha });
  console.log(`Pushed ${local} -> ${finish.target} (${finish.size} bytes, sha256 ${finish.sha256})`);
}

async function cpull(ctx, args) {
  let [remote, local] = args.filter((a) => !a.startsWith('--'));
  if (!remote || !local) throw new Error('Usage: cpull <remote-path> <local-file>');
  remote = remote.replace(/\\/g, '/');
  const start = await ctx.client.request('xfer.pull.start', { path: remote });
  const { transferId, size, sha256: expectedSha, chunkSize } = start;
  const part = local + '.creepercli-part';
  const hash = crypto.createHash('sha256');
  const fd = fs.openSync(part, 'w');
  let transferred = 0;
  let index = 0;
  try {
    while (true) {
      const res = await ctx.client.request('xfer.pull.chunk', { transferId, index }, { timeoutMs: 120000 });
      const data = Buffer.from(res.data || '', 'base64');
      if (data.length === 0) break;
      fs.writeSync(fd, data, 0, data.length);
      hash.update(data);
      transferred += data.length;
      index++;
      showProgress(transferred, size);
    }
  } finally {
    fs.closeSync(fd);
  }
  const actual = await ctx.client.request('xfer.pull.finish', { transferId, sha256: hash.digest('hex') });
  clearProgress();
  if (actual.sha256 !== expectedSha) {
    fs.unlinkSync(part);
    throw new Error('Checksum mismatch: partial file removed, transfer aborted');
  }
  fs.renameSync(part, local);
  console.log(`Pulled ${remote} -> ${local} (${transferred} bytes, sha256 ${actual.sha256})`);
}

async function csync(ctx, args) {
  let [remoteDir, localDir] = args.filter((a) => a !== '-y' && !a.startsWith('--'));
  if (!remoteDir || !localDir) throw new Error('Usage: csync <remote-dir> <local-dir> [--yes]');
  remoteDir = remoteDir.replace(/\\/g, '/');
  const yes = args.includes('--yes') || args.includes('-y') || !!(ctx.flags && ctx.flags.yes);
  await ctx.client.request('fs.mkdir', { path: remoteDir, parents: true }).catch(() => {});

  const remote = await ctx.client.request('xfer.list', { path: remoteDir }, { timeoutMs: 600000 });
  const remoteMap = new Map();
  for (const e of remote.entries) {
    if (!e.isDir) remoteMap.set(e.path.replace(/^\//, ''), e);
  }
  if (remote.truncated) console.error('(remote listing truncated)');

  const localMap = walkLocal(localDir);

  const toDownload = [];
  const toUpload = [];
  for (const [rel, e] of remoteMap) {
    const local = localMap.get(rel);
    if (!local) {
      toDownload.push(rel);
      continue;
    }
    if (local.size === e.size && local.mtime === e.mtime) continue;
    if (local.size === e.size) {
      let localSha;
      try {
        localSha = await sha256File(path.join(localDir, rel));
      } catch {
        toUpload.push(rel);
        continue;
      }
      if (localSha === e.sha256) continue;
    }
    if (e.mtime > local.mtime) toDownload.push(rel);
    else toUpload.push(rel);
  }
  for (const rel of localMap.keys()) {
    if (!remoteMap.has(rel)) toUpload.push(rel);
  }

  if (!toDownload.length && !toUpload.length) {
    console.log('Directories are in sync.');
    return;
  }
  console.log(`Download: ${toDownload.length} file(s), Upload: ${toUpload.length} file(s)`);
  if (!yes && toUpload.length) {
    const ok = await confirm(`Upload ${toUpload.length} local file(s)?`, false);
    if (!ok) toUpload.length = 0;
  }
  if (!yes && toDownload.length) {
    const ok = await confirm(`Download ${toDownload.length} remote file(s)?`, false);
    if (!ok) toDownload.length = 0;
  }
  for (const rel of toDownload) {
    await cpull(ctx, [joinRemote(remoteDir, rel), path.join(localDir, rel)]);
  }
  for (const rel of toUpload) {
    await cpush(ctx, [path.join(localDir, rel), joinRemote(remoteDir, rel)]);
  }
  console.log('Sync complete.');
}

function joinRemote(base, rel) {
  const cleanBase = base.replace(/\\/g, '/');
  const cleanRel = rel.replace(/\\/g, '/');
  return (cleanBase.endsWith('/') ? cleanBase : cleanBase + '/') + cleanRel;
}

function walkLocal(dir) {
  const map = new Map();
  function walk(d, prefix) {
    let names;
    try {
      names = fs.readdirSync(d);
    } catch {
      return;
    }
    for (const name of names) {
      const p = path.join(d, name);
      let st;
      try {
        st = fs.statSync(p);
      } catch {
        continue;
      }
      const rel = ((prefix ? prefix + '/' : '') + name).replace(/\\/g, '/');
      if (st.isDirectory()) walk(p, rel);
      else if (st.isFile()) map.set(rel, { size: st.size, mtime: Math.floor(st.mtimeMs) });
    }
  }
  walk(dir, '');
  return map;
}

module.exports = { cpush, cpull, csync, joinRemote, walkLocal };
