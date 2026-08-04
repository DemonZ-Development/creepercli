/*
 * Copyright 2026 DemonZDevelopment
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    const parts = (editor.match(/"[^"]+"|'[^']+'|\S+/g) || []).map((p) => {
      if ((p.startsWith('"') && p.endsWith('"')) || (p.startsWith("'") && p.endsWith("'"))) {
        return p.slice(1, -1);
      }
      return p;
    });
    const execName = parts[0] || editor;
    const args = parts.slice(1);
    await new Promise((resolve, reject) => {
      const child = spawn(execName, [...args, tmpFile], { stdio: 'inherit' });
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
