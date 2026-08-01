'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { spawn } = require('child_process');
const { DIR } = require('./session');

function sha256(content) {
  return crypto.createHash('sha256').update(content).digest('hex');
}

async function editFile(ctx, remotePath) {
  if (!remotePath) throw new Error('Usage: edit <path>');
  const tmpRoot = path.join(DIR, 'tmp');
  fs.mkdirSync(tmpRoot, { recursive: true, mode: 0o700 });
  const tmpDir = fs.mkdtempSync(path.join(tmpRoot, 'edit-'));
  const localName = remotePath.split('/').pop() || 'file';
  const tmpFile = path.join(tmpDir, localName);

  try {
    await ctx.client.request('fs.edit.lock', { path: remotePath });
  } catch (err) {
    if (err.code === 'E_LOCKED') {
      console.error(`Locked: ${err.message}`);
      return 1;
    }
    throw err;
  }

  try {
    const cat = await ctx.client.request('fs.cat', { path: remotePath });
    fs.writeFileSync(tmpFile, cat.content, { mode: 0o600 });

    const editor = ctx.cfg.editor;
    await new Promise((resolve, reject) => {
      const child = spawn(editor, [tmpFile], { stdio: 'inherit', shell: process.platform === 'win32' });
      child.on('error', (err) => reject(new Error(`Could not start editor "${editor}": ${err.message}`)));
      child.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`Editor exited with code ${code}`))));
    });

    const newContent = fs.readFileSync(tmpFile, 'utf8');
    if (newContent === cat.content) {
      console.log('No changes.');
      return 0;
    }
    const res = await ctx.client.request('fs.edit.push', {
      path: remotePath,
      content: newContent,
      sha256: sha256(newContent),
    });
    const diff = summarizeDiff(cat.content.split('\n'), newContent.split('\n'));
    console.log(`Pushed ${res.bytes} bytes to ${res.path} (+${diff.added}/-${diff.removed} lines)`);
    return 0;
  } catch (err) {
    console.error(`Edit failed: ${err.message}`);
    return 1;
  } finally {
    await ctx.client.request('fs.edit.unlock', { path: remotePath }).catch(() => {});
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
}

function summarizeDiff(oldLines, newLines) {
  const a = new Set(oldLines);
  const b = new Set(newLines);
  let added = 0;
  let removed = 0;
  for (const l of newLines) if (!a.has(l)) added++;
  for (const l of oldLines) if (!b.has(l)) removed++;
  return { added, removed };
}

module.exports = { editFile };
