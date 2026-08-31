 

'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { ProtocolError, friendly, ERR } = require('../src/protocol');
const { CreeperClient } = require('../src/client');

test('ProtocolError formatting', async (t) => {
  await t.test('creates ProtocolError with code and message', () => {
    const err = new ProtocolError(ERR.UNAUTHORIZED, 'Access denied');
    assert.equal(err.code, ERR.UNAUTHORIZED);
    assert.equal(err.message, 'Access denied');
    assert.ok(err instanceof Error);
    assert.ok(err instanceof ProtocolError);
  });

  await t.test('creates ProtocolError with default message when message is omitted', () => {
    const err = new ProtocolError(ERR.BANNED);
    assert.equal(err.code, ERR.BANNED);
    assert.equal(err.message, ERR.BANNED);
  });

  await t.test('friendly formats known ProtocolError codes with hints', () => {
    const err = new ProtocolError(ERR.UNAUTHORIZED, 'Authentication required');
    const formatted = friendly(err);
    assert.equal(formatted, 'Authentication required [run "creepercli login" first] (E_UNAUTHORIZED)');
  });

  await t.test('friendly formats unknown ProtocolError code without hint', () => {
    const err = new ProtocolError('E_CUSTOM', 'Custom error occurred');
    const formatted = friendly(err);
    assert.equal(formatted, 'Custom error occurred (E_CUSTOM)');
  });

  await t.test('friendly handles standard Error instance', () => {
    const err = new Error('Standard failure');
    assert.equal(friendly(err), 'Standard failure');
  });

  await t.test('friendly handles string or non-Error values', () => {
    assert.equal(friendly('Simple string error'), 'Simple string error');
    assert.equal(friendly(null), 'null');
    assert.equal(friendly(undefined), 'undefined');
  });
});

test('JSON frame request stringification and response parsing', async (t) => {
  await t.test('stringifies request frames correctly', async () => {
    const client = new CreeperClient({ host: 'localhost', port: 9999 });
    let writtenData = '';
    client.socket = {
      write(data) {
        writtenData += data;
      }
    };

    const reqPromise = client.request('fs.ls', { path: '/tmp' });
    assert.ok(writtenData.endsWith('\n'));

    const parsed = JSON.parse(writtenData.trim());
    assert.equal(parsed.v, 1);
    assert.equal(parsed.type, 'request');
    assert.equal(parsed.action, 'fs.ls');
    assert.deepEqual(parsed.params, { path: '/tmp' });
    assert.ok(parsed.id);

    client._handleLine(JSON.stringify({
      v: 1,
      type: 'response',
      id: parsed.id,
      ok: true,
      data: { files: [] }
    }));

    const res = await reqPromise;
    assert.deepEqual(res, { files: [] });
  });

  await t.test('parses successful response frame', async () => {
    const client = new CreeperClient({ host: 'localhost', port: 9999 });
    client.socket = { write() {} };

    const reqPromise = client.request('ping', {});
    const id = Array.from(client.pending.keys())[0];

    const responseFrame = JSON.stringify({
      v: 1,
      type: 'response',
      id,
      ok: true,
      data: { pong: true }
    });

    client._onData(Buffer.from(responseFrame + '\n'));
    const data = await reqPromise;
    assert.deepEqual(data, { pong: true });
  });

  await t.test('parses error response frame into ProtocolError rejection', async () => {
    const client = new CreeperClient({ host: 'localhost', port: 9999 });
    client.socket = { write() {} };

    const reqPromise = client.request('auth.login', { user: 'test' });
    const id = Array.from(client.pending.keys())[0];

    const responseFrame = JSON.stringify({
      v: 1,
      type: 'response',
      id,
      ok: false,
      error: { code: ERR.AUTH_FAILED, message: 'Invalid credentials' }
    });

    client._onData(Buffer.from(responseFrame + '\n'));

    await assert.rejects(async () => {
      await reqPromise;
    }, (err) => {
      assert.ok(err instanceof ProtocolError);
      assert.equal(err.code, ERR.AUTH_FAILED);
      assert.equal(err.message, 'Invalid credentials');
      return true;
    });
  });

  await t.test('handles event frames', () => {
    const client = new CreeperClient({ host: 'localhost', port: 9999 });
    let emittedEvent = null;
    let emittedData = null;

    client.on('event', (evt, data) => {
      emittedEvent = evt;
      emittedData = data;
    });

    const eventFrame = JSON.stringify({
      v: 1,
      type: 'event',
      event: 'log.line',
      data: { line: 'Server started' }
    });

    client._onData(Buffer.from(eventFrame + '\n'));
    assert.equal(emittedEvent, 'log.line');
    assert.deepEqual(emittedData, { line: 'Server started' });
  });

  await t.test('ignores invalid JSON frames gracefully', () => {
    const client = new CreeperClient({ host: 'localhost', port: 9999 });
    assert.doesNotThrow(() => {
      client._onData(Buffer.from('invalid json line\n'));
    });
  });
});
