'use strict';

const ERR = {
  UNAUTHORIZED: 'E_UNAUTHORIZED',
  AUTH_FAILED: 'E_AUTH_FAILED',
  TOTP_REQUIRED: 'E_TOTP_REQUIRED',
  BANNED: 'E_BANNED',
  RATE_LIMITED: 'E_RATE_LIMITED',
  SESSION_EXPIRED: 'E_SESSION_EXPIRED',
  INVALID_PARAMS: 'E_INVALID_PARAMS',
  BAD_REQUEST: 'E_BAD_REQUEST',
  PATH_ESCAPE: 'E_PATH_ESCAPE',
  NOT_FOUND: 'E_NOT_FOUND',
  IS_DIRECTORY: 'E_IS_DIRECTORY',
  NOT_DIRECTORY: 'E_NOT_DIRECTORY',
  ALREADY_EXISTS: 'E_ALREADY_EXISTS',
  FORBIDDEN: 'E_FORBIDDEN',
  IO: 'E_IO',
  LOCKED: 'E_LOCKED',
  ALLOWLIST_DENIED: 'E_ALLOWLIST_DENIED',
  TIMEOUT: 'E_TIMEOUT',
  PAYLOAD_TOO_LARGE: 'E_PAYLOAD_TOO_LARGE',
  CHECKSUM_MISMATCH: 'E_CHECKSUM_MISMATCH',
  NO_TRANSFER: 'E_NO_TRANSFER',
  INTERNAL: 'E_INTERNAL',
  SERVER_FULL: 'E_SERVER_FULL',
};

const PROTOCOL_VERSION = 1;

const EVENT_LOG_LINE = 'log.line';

class ProtocolError extends Error {
  constructor(code, message) {
    super(message || code);
    this.code = code;
  }
}

function friendly(err) {
  if (err instanceof ProtocolError) {
    const hints = {
      [ERR.UNAUTHORIZED]: 'run "creepercli login" first',
      [ERR.TOTP_REQUIRED]: 'provide your 6-digit TOTP code',
      [ERR.BANNED]: 'too many failures, this IP is banned for a while',
      [ERR.RATE_LIMITED]: 'slow down',
      [ERR.SESSION_EXPIRED]: 'session expired, run "creepercli login"',
      [ERR.PATH_ESCAPE]: 'path blocked by the server sandbox',
      [ERR.ALLOWLIST_DENIED]: 'the exec allowlist rejected this command',
      [ERR.LOCKED]: 'someone else is editing this file',
      [ERR.CHECKSUM_MISMATCH]: 'data integrity check failed',
      [ERR.SERVER_FULL]: 'server connection limit reached',
    };
    const hint = hints[err.code];
    return `${err.message}${hint ? ` [${hint}]` : ''} (${err.code})`;
  }
  return err && err.message ? err.message : String(err);
}

module.exports = { ERR, PROTOCOL_VERSION, EVENT_LOG_LINE, ProtocolError, friendly };
