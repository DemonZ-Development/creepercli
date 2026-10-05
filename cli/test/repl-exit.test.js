'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { PassThrough } = require('node:stream');
const { startRepl } = require('../src/repl');

test('q closes the REPL and client', async () => {
  const input = new PassThrough();
  const output = new PassThrough();
  let closed = false;
  const ctx = {
    username: 'admin',
    cwd: '/',
    exit: false,
    cfg: { host: 'localhost', historyFile: 'missing-history-file' },
    client: {
      flush: async () => {},
      close: () => { closed = true; },
    },
  };

  const repl = startRepl(ctx, { input, output });
  input.write('q\n');
  await new Promise((resolve) => repl.once('close', resolve));
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(ctx.exit, true);
  assert.equal(closed, true);
});
