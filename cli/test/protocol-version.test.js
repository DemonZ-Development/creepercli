

'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { ProtocolError, friendly, ERR, PROTOCOL_VERSION } = require('../src/protocol');

test('PROTOCOL_VERSION exported', () => {
  assert.equal(typeof PROTOCOL_VERSION, 'number');
  assert.ok(PROTOCOL_VERSION >= 1);
});

test('redaction: protocol module exposes the version for ping checks', () => {
  
  const exported = Object.keys(require('../src/protocol'));
  assert.ok(exported.includes('PROTOCOL_VERSION'));
  assert.ok(exported.includes('ERR'));
});

test('protocol error codes cover all server-emitted cases', () => {
  
  
  const required = [
    'UNAUTHORIZED', 'AUTH_FAILED', 'TOTP_REQUIRED', 'BANNED',
    'RATE_LIMITED', 'SESSION_EXPIRED', 'INVALID_PARAMS', 'BAD_REQUEST',
    'PATH_ESCAPE', 'NOT_FOUND', 'IS_DIRECTORY', 'NOT_DIRECTORY',
    'ALREADY_EXISTS', 'FORBIDDEN', 'IO', 'LOCKED', 'ALLOWLIST_DENIED',
    'TIMEOUT', 'PAYLOAD_TOO_LARGE', 'CHECKSUM_MISMATCH', 'NO_TRANSFER',
    'INTERNAL', 'SERVER_FULL',
  ];
  for (const k of required) {
    assert.ok(ERR[k], `ERR.${k} missing`);
  }
});

test('friendly formats E_CHECKSUM_MISMATCH with cleanup hint', () => {
  const err = new ProtocolError(ERR.CHECKSUM_MISMATCH, 'oops');
  const out = friendly(err);
  assert.match(out, /partial file cleaned up/);
});

test('friendly formats E_PAYLOAD_TOO_LARGE', () => {
  const err = new ProtocolError(ERR.PAYLOAD_TOO_LARGE, 'too big');
  const out = friendly(err);
  assert.equal(out, 'too big (E_PAYLOAD_TOO_LARGE)');
});