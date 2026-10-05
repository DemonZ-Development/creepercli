 

'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { confirm } = require('./prompts');

const MAX_TRANSFER_CHUNK_BYTES = 1024 * 1024;

function validateTransferStart(start, expectFileMetadata = false) {
  if (!start || typeof start.transferId !== 'string' || !start.transferId) {
    throw new Error('Server returned an invalid transfer id');
  }
  if (!Number.isInteger(start.chunkSize) || start.chunkSize < 1024
      || start.chunkSize > MAX_TRANSFER_CHUNK_BYTES) {
    throw new Error('Server returned an invalid transfer chunk size');
  }
  if (expectFileMetadata) {
    if (!Number.isSafeInteger(start.size) || start.size < 0) {
      throw new Error('Server returned an invalid file size');
    }
    if (typeof start.sha256 !== 'string' || !/^[0-9a-f]{64}$/i.test(start.sha256)) {
      throw new Error('Server returned an invalid file checksum');
    }
  }
}

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
  let fd = null;
  let transferred = 0;
  let index = 0;
  try {
    validateTransferStart(start);
    fd = fs.openSync(local, 'r');
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
    const finish = await ctx.client.request('xfer.push.finish', { transferId, sha256: sha });
    if (!finish || typeof finish.sha256 !== 'string' || finish.sha256.toLowerCase() !== sha) {
      throw new Error('Server reported an unexpected upload checksum');
    }
    clearProgress();
    console.log(`Pushed ${local} -> ${finish.target} (${finish.size} bytes, sha256 ${finish.sha256})`);
  } catch (err) {
    await ctx.client.request('xfer.abort', { transferId }).catch(() => {});
    clearProgress();
    throw err;
  } finally {
    if (fd !== null) fs.closeSync(fd);
  }
}

async function cpull(ctx, args) {
  let [remote, local] = args.filter((a) => !a.startsWith('--'));
  if (!remote || !local) throw new Error('Usage: cpull <remote-path> <local-file>');
  remote = remote.replace(/\\/g, '/');
  const start = await ctx.client.request('xfer.pull.start', { path: remote });
  const { transferId, size, sha256: expectedSha, chunkSize } = start;
  const part = `${local}.creepercli-part-${process.pid}-${Date.now()}`;
  const hash = crypto.createHash('sha256');
  let fd = null;
  let transferred = 0;
  let index = 0;
  try {
    validateTransferStart(start, true);
    const parent = path.dirname(path.resolve(local));
    fs.mkdirSync(parent, { recursive: true, mode: 0o700 });
    fd = fs.openSync(part, 'w');
    while (true) {
      const res = await ctx.client.request('xfer.pull.chunk', { transferId, index }, { timeoutMs: 120000 });
      const data = Buffer.from(res.data || '', 'base64');
      if (data.length === 0) break;
      if (data.length > chunkSize || transferred + data.length > size) {
        throw new Error('Server sent an invalid transfer chunk');
      }
      fs.writeSync(fd, data, 0, data.length);
      hash.update(data);
      transferred += data.length;
      index++;
      showProgress(transferred, size);
    }
    fs.closeSync(fd);
    fd = null;
    if (transferred !== size) {
      throw new Error(`Transfer ended early: expected ${size} bytes, received ${transferred}`);
    }
    const actual = await ctx.client.request('xfer.pull.finish', { transferId, sha256: hash.digest('hex') });
    clearProgress();
    if (!actual || typeof actual.sha256 !== 'string'
        || actual.sha256.toLowerCase() !== expectedSha.toLowerCase()) {
      throw new Error('Checksum mismatch: transfer aborted');
    }
    replaceFile(part, local);
    console.log(`Pulled ${remote} -> ${local} (${transferred} bytes, sha256 ${actual.sha256})`);
  } catch (err) {
    if (fd !== null) {
      fs.closeSync(fd);
      fd = null;
    }
    await ctx.client.request('xfer.abort', { transferId }).catch(() => {});
    try { fs.unlinkSync(part); } catch {}
    clearProgress();
    throw err;
  } finally {
    if (fd !== null) fs.closeSync(fd);
  }
}

async function csync(ctx, args) {
  let [remoteDir, localDir] = args.filter((a) => a !== '-y' && !a.startsWith('--'));
  if (!remoteDir || !localDir) throw new Error('Usage: csync <remote-dir> <local-dir> [--yes]');
  remoteDir = remoteDir.replace(/\\/g, '/');
  const yes = args.includes('--yes') || args.includes('-y') || !!(ctx.flags && ctx.flags.yes);
  await ctx.client.request('fs.mkdir', { path: remoteDir, parents: true }).catch(() => {});

  const remoteInfo = await ctx.client.request('fs.info', { path: remoteDir });
  if (!remoteInfo.isDir || typeof remoteInfo.path !== 'string' || !remoteInfo.path.startsWith('/')) {
    throw new Error('Server returned an invalid sync directory');
  }
  remoteDir = remoteInfo.path;

  const remote = await ctx.client.request('xfer.list', { path: remoteDir }, { timeoutMs: 600000 });
  const remoteMap = new Map();
  for (const e of remote.entries) {
    if (!e.isDir) {
      const rel = relativeRemotePath(remoteDir, e.path);
      if (rel) remoteMap.set(rel, e);
    }
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

function relativeRemotePath(base, absolutePath) {
  const cleanBase = ('/' + base.replace(/\\/g, '/').replace(/^\/+|\/+$/g, '')).replace(/^\/$/, '');
  const cleanPath = '/' + absolutePath.replace(/\\/g, '/').replace(/^\/+/, '');
  if (!cleanBase) return cleanPath.replace(/^\//, '');
  if (cleanPath === cleanBase) return '';
  if (!cleanPath.startsWith(cleanBase + '/')) {
    throw new Error(`Server returned path outside sync root: ${absolutePath}`);
  }
  return cleanPath.slice(cleanBase.length + 1);
}

function replaceFile(part, destination) {
  if (!fs.existsSync(destination)) {
    fs.renameSync(part, destination);
    return;
  }
  const backup = `${destination}.creepercli-backup-${process.pid}-${Date.now()}`;
  fs.renameSync(destination, backup);
  try {
    fs.renameSync(part, destination);
  } catch (err) {
    try { fs.renameSync(backup, destination); } catch {}
    throw err;
  }
  try { fs.rmSync(backup, { force: true }); } catch {}
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
        st = fs.lstatSync(p);
      } catch {
        continue;
      }
      const rel = ((prefix ? prefix + '/' : '') + name).replace(/\\/g, '/');
      if (st.isSymbolicLink()) continue;
      if (st.isDirectory()) walk(p, rel);
      else if (st.isFile()) map.set(rel, { size: st.size, mtime: Math.floor(st.mtimeMs) });
    }
  }
  walk(dir, '');
  return map;
}

module.exports = {
  cpush, cpull, csync, joinRemote, relativeRemotePath, replaceFile, walkLocal, validateTransferStart,
};
