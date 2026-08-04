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
      [ERR.AUTH_FAILED]: 'check your username/password or add user in server console: /creepercli user add <user> <pass>',
      [ERR.TOTP_REQUIRED]: 'provide your 6-digit TOTP code',
      [ERR.BANNED]: 'too many failures, this IP is temporarily banned by Fail2Ban',
      [ERR.RATE_LIMITED]: 'too many requests in a short window, please slow down',
      [ERR.SESSION_EXPIRED]: 'session expired, run "creepercli login"',
      [ERR.PATH_ESCAPE]: 'path blocked by server PathSanitizer sandbox',
      [ERR.ALLOWLIST_DENIED]: 'command restricted by server allowlist config',
      [ERR.LOCKED]: 'file currently locked by another active session',
      [ERR.CHECKSUM_MISMATCH]: 'data integrity check failed, partial file cleaned up',
      [ERR.SERVER_FULL]: 'server connection limit reached',
      [ERR.TIMEOUT]: 'request timed out, check server load',
      [ERR.NOT_FOUND]: 'file or directory not found on remote server',
      [ERR.IS_DIRECTORY]: 'target path is a directory, not a file',
      [ERR.NOT_DIRECTORY]: 'target path is a file, not a directory',
      [ERR.FORBIDDEN]: 'permission denied by server policy',
    };
    const hint = hints[err.code];
    return `${err.message}${hint ? ` [${hint}]` : ''} (${err.code})`;
  }
  return err && err.message ? err.message : String(err);
}

module.exports = { ERR, PROTOCOL_VERSION, EVENT_LOG_LINE, ProtocolError, friendly };
