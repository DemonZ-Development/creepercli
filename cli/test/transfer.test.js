 

'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const os = require('os');
const { joinRemote, walkLocal } = require('../src/transfer');

test('joinRemote', async (t) => {
  await t.test('joins base path without trailing slash and relative path', () => {
    assert.equal(joinRemote('/var/minecraft', 'plugins/config.yml'), '/var/minecraft/plugins/config.yml');
    assert.equal(joinRemote('logs', 'latest.log'), 'logs/latest.log');
  });

  await t.test('joins base path with trailing slash and relative path', () => {
    assert.equal(joinRemote('/var/minecraft/', 'plugins/config.yml'), '/var/minecraft/plugins/config.yml');
    assert.equal(joinRemote('logs/', 'latest.log'), 'logs/latest.log');
  });

  await t.test('handles root or empty base paths', () => {
    assert.equal(joinRemote('/', 'server.properties'), '/server.properties');
  });
});

test('walkLocal', async (t) => {
  let tmpDir;

  t.beforeEach(() => {
    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'creeper-walk-test-'));
  });

  t.afterEach(() => {
    if (tmpDir && fs.existsSync(tmpDir)) {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    }
  });

  await t.test('returns a map of files with normalized relative paths using "/"', () => {
    const subDir = path.join(tmpDir, 'subdir');
    const nestedDir = path.join(subDir, 'nested');
    fs.mkdirSync(nestedDir, { recursive: true });

    fs.writeFileSync(path.join(tmpDir, 'file1.txt'), 'content 1');
    fs.writeFileSync(path.join(subDir, 'file2.txt'), 'content 2');
    fs.writeFileSync(path.join(nestedDir, 'file3.txt'), 'content 3');

    const result = walkLocal(tmpDir);

    assert.ok(result instanceof Map);
    assert.equal(result.size, 3);

    
    assert.ok(result.has('file1.txt'));
    assert.ok(result.has('subdir/file2.txt'));
    assert.ok(result.has('subdir/nested/file3.txt'));

    
    const file1Info = result.get('file1.txt');
    assert.equal(file1Info.size, 'content 1'.length);
    assert.equal(typeof file1Info.mtime, 'number');

    const file2Info = result.get('subdir/file2.txt');
    assert.equal(file2Info.size, 'content 2'.length);
    assert.equal(typeof file2Info.mtime, 'number');

    const file3Info = result.get('subdir/nested/file3.txt');
    assert.equal(file3Info.size, 'content 3'.length);
    assert.equal(typeof file3Info.mtime, 'number');
  });

  await t.test('returns empty map for empty directory', () => {
    const result = walkLocal(tmpDir);
    assert.ok(result instanceof Map);
    assert.equal(result.size, 0);
  });

  await t.test('returns empty map for non-existent directory', () => {
    const nonExistent = path.join(tmpDir, 'does-not-exist');
    const result = walkLocal(nonExistent);
    assert.ok(result instanceof Map);
    assert.equal(result.size, 0);
  });
});
