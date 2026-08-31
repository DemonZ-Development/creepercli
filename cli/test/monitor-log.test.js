'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

test('monitor module exports log function', () => {
  const mod = require('../src/commands/monitor');
  assert.equal(typeof mod.log, 'function');
});

test('log teardown registers on context', async () => {
  const { EventEmitter } = require('events');
  const fakeClient = new EventEmitter();
  fakeClient.request = async () => ({ grep: null, buffer: [], subscribers: 1 });

  const ctx = { client: fakeClient, cfg: {} };

  const origOnce = process.once;
  const origWrite = process.stdout.write.bind(process.stdout);
  process.once = () => {};
  process.stdout.write = () => true;

  const mod = require('../src/commands/monitor');
  let p;
  try {
    p = mod.log(ctx, []);
    for (let i = 0; i < 200 && typeof ctx.logTeardown !== 'function'; i++) {
      await new Promise((r) => setImmediate(r));
    }
    assert.equal(typeof ctx.logTeardown, 'function');
    if (ctx.logTeardown) ctx.logTeardown();
  } finally {
    process.stdout.write = origWrite;
    process.once = origOnce;
  }
  await p.catch(() => {});
});