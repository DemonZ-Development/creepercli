 

'use strict';

const net = require('net');
const crypto = require('crypto');
const { EventEmitter } = require('events');
const { StringDecoder } = require('string_decoder');
const { ProtocolError, ERR, PROTOCOL_VERSION } = require('./protocol');

const MAX_INCOMING_BUFFER_BYTES = 16 * 1024 * 1024;

class CreeperClient extends EventEmitter {
  constructor({ host, port, timeoutMs = 300000 }) {
    super();
    this.host = host;
    this.port = port;
    this.timeoutMs = timeoutMs;
    this.socket = null;
    this.buffer = '';
    this._decoder = new StringDecoder('utf8');
    this.pending = new Map();
  }

  connect() {
    return new Promise((resolve, reject) => {
      const socket = net.connect({ host: this.host, port: this.port });
      this.socket = socket;
      socket.setNoDelay(true);
      socket.on('connect', () => resolve());
      socket.on('error', (err) => reject(err));
      socket.on('close', () => {
        if (this.socket === socket) this.socket = null;
        this._onClose();
      });
      socket.on('data', (chunk) => this._onData(chunk));
    });
  }

  _onData(chunk) {
    this.buffer += this._decoder.write(chunk);
    if (Buffer.byteLength(this.buffer, 'utf8') > MAX_INCOMING_BUFFER_BYTES) {
      this._failAll(new ProtocolError(ERR.PAYLOAD_TOO_LARGE, 'Server frame exceeded client buffer limit'));
      this.close();
      return;
    }
    let idx;
    while ((idx = this.buffer.indexOf('\n')) !== -1) {
      const line = this.buffer.slice(0, idx).trim();
      this.buffer = this.buffer.slice(idx + 1);
      if (!line) continue;
      this._handleLine(line);
    }
  }

  _handleLine(line) {
    let frame;
    try {
      frame = JSON.parse(line);
    } catch {
      return;
    }
    if (frame.v !== PROTOCOL_VERSION) {
      const entry = this.pending.get(frame.id);
      if (entry) {
        this.pending.delete(frame.id);
        clearTimeout(entry.timer);
        entry.reject(new ProtocolError(ERR.BAD_REQUEST,
          `Unsupported protocol version ${frame.v} (expected ${PROTOCOL_VERSION})`));
      }
      return;
    }
    if (frame.type === 'response') {
      const entry = this.pending.get(frame.id);
      if (!entry) return;
      this.pending.delete(frame.id);
      clearTimeout(entry.timer);
      if (frame.ok) {
        entry.resolve(frame.data || {});
      } else {
        entry.reject(new ProtocolError(frame.error && frame.error.code, frame.error && frame.error.message));
      }
    } else if (frame.type === 'event') {
      this.emit('event', frame.event, frame.data || {});
    }
  }

  async flush() {
    while (this.pending.size > 0) {
      await new Promise((r) => setTimeout(r, 25));
    }
  }

  request(action, params = {}, { timeoutMs } = {}) {
    if (!this.socket || this.socket.destroyed === true || this.socket.writable === false) {
      return Promise.reject(new ProtocolError(ERR.INTERNAL, 'Connection closed'));
    }
    const id = crypto.randomUUID();
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new ProtocolError(ERR.TIMEOUT, `Request timed out (${action})`));
      }, timeoutMs || this.timeoutMs);
      this.pending.set(id, { resolve, reject, timer });
      const frame = JSON.stringify({ v: PROTOCOL_VERSION, type: 'request', id, action, params });
      try {
        this.socket.write(frame + '\n');
      } catch (err) {
        clearTimeout(timer);
        this.pending.delete(id);
        reject(new ProtocolError(ERR.INTERNAL, `Connection closed (${err.code || err.message})`));
      }
    });
  }

  _onClose() {
    this._failAll(new ProtocolError(ERR.INTERNAL, 'Connection closed by server'));
  }

  _failAll(error) {
    for (const { reject, timer } of this.pending.values()) {
      clearTimeout(timer);
      reject(error);
    }
    this.pending.clear();
  }

  close() {
    if (this.socket) {
      this.socket.end();
      this.socket.destroy();
      this.socket = null;
    }
  }
}

module.exports = { CreeperClient };
