 

'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const {
  csync, joinRemote, relativeRemotePath, replaceFile, walkLocal, validateTransferStart,
} = require('../src/transfer');

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

test('relativeRemotePath', () => {
  assert.equal(relativeRemotePath('/plugins', '/plugins/config.yml'), 'config.yml');
  assert.equal(relativeRemotePath('/plugins', '/plugins/sub/a.yml'), 'sub/a.yml');
  assert.equal(relativeRemotePath('/', '/plugins/config.yml'), 'plugins/config.yml');
  assert.throws(() => relativeRemotePath('/plugins', '/world/config.yml'), /outside sync root/);
});

test('csync uses the server-resolved directory for downloads and uploads', async (t) => {
  const cases = [
    { requested: '.', cwd: '/', root: '/' },
    { requested: '.', cwd: '/plugins', root: '/plugins' },
    { requested: 'plugins', cwd: '/world', root: '/world/plugins' },
    { requested: '/plugins/../world', cwd: '/plugins', root: '/world' },
    { requested: '~/plugins', cwd: '/world', root: '/plugins' },
  ];
  for (const { requested, cwd, root } of cases) {
    await t.test(`${requested} from ${cwd}`, async () => {
      const localDir = fs.mkdtempSync(path.join(os.tmpdir(), 'creeper-sync-test-'));
      const download = Buffer.from('remote config');
      const upload = Buffer.from('local config');
      const sha256 = (data) => crypto.createHash('sha256').update(data).digest('hex');
      const downloadPath = joinRemote(root, 'sub/config.yml');
      const uploadPath = joinRemote(root, 'upload.txt');
      const received = [];
      const calls = [];
      const ctx = {
        cwd,
        client: {
          async request(action, params) {
            calls.push({ action, params });
            switch (action) {
              case 'fs.mkdir': throw new Error('Directory already exists');
              case 'fs.info': return { path: root, isDir: true };
              case 'xfer.list': return {
                entries: [
                  { path: root, isDir: true },
                  { path: downloadPath, isDir: false, size: download.length, mtime: 1, sha256: sha256(download) },
                ],
              };
              case 'xfer.pull.start': return {
                transferId: 'pull', size: download.length, sha256: sha256(download), chunkSize: 1024,
              };
              case 'xfer.pull.chunk': return { data: params.index === 0 ? download.toString('base64') : '' };
              case 'xfer.pull.finish':
                assert.equal(params.sha256, sha256(download));
                return { sha256: sha256(download) };
              case 'xfer.push.start': return { transferId: 'push', chunkSize: 1024 };
              case 'xfer.push.chunk':
                received.push(Buffer.from(params.data, 'base64'));
                return {};
              case 'xfer.push.finish': return { target: uploadPath, size: upload.length, sha256: sha256(upload) };
              default: throw new Error(`Unexpected request: ${action}`);
            }
          },
        },
      };
      try {
        fs.writeFileSync(path.join(localDir, 'upload.txt'), upload);
        await csync(ctx, [requested, localDir, '--yes']);
        assert.deepEqual(fs.readFileSync(path.join(localDir, 'sub', 'config.yml')), download);
        assert.deepEqual(Buffer.concat(received), upload);
        assert.equal(calls.find((call) => call.action === 'fs.info').params.path, requested);
        assert.equal(calls.find((call) => call.action === 'xfer.list').params.path, root);
        assert.equal(calls.find((call) => call.action === 'xfer.pull.start').params.path, downloadPath);
        assert.equal(calls.find((call) => call.action === 'xfer.push.start').params.path, uploadPath);
      } finally {
        fs.rmSync(localDir, { recursive: true, force: true });
      }
    });
  }
});

test('validateTransferStart rejects unsafe server metadata', () => {
  assert.doesNotThrow(() => validateTransferStart({ transferId: 'x', chunkSize: 65536 }));
  assert.throws(() => validateTransferStart({ transferId: 'x', chunkSize: 0 }), /chunk size/);
  assert.throws(() => validateTransferStart({ transferId: 'x', chunkSize: 2 * 1024 * 1024 }), /chunk size/);
  assert.throws(() => validateTransferStart({ transferId: 'x', chunkSize: 65536, size: -1, sha256: '0'.repeat(64) }, true), /file size/);
  assert.throws(() => validateTransferStart({ transferId: 'x', chunkSize: 65536, size: 1, sha256: 'bad' }, true), /checksum/);
});

test('replaceFile overwrites an existing destination', () => {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'creeper-replace-test-'));
  try {
    const destination = path.join(tmpDir, 'output.txt');
    const part = path.join(tmpDir, 'output.txt.part');
    fs.writeFileSync(destination, 'old');
    fs.writeFileSync(part, 'new');
    replaceFile(part, destination);
    assert.equal(fs.readFileSync(destination, 'utf8'), 'new');
    assert.equal(fs.existsSync(part), false);
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
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
