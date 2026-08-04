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
const os = require('os');
const path = require('path');

const DIR = path.join(os.homedir(), '.creepercli');
const CREDS_FILE = path.join(DIR, 'creds');
const HISTORY_FILE = path.join(DIR, 'history');

function ensureDir() {
  fs.mkdirSync(DIR, { recursive: true, mode: 0o700 });
}

function save(creds) {
  ensureDir();
  fs.writeFileSync(CREDS_FILE, JSON.stringify(creds, null, 2), { mode: 0o600 });
}

function load(host, port) {
  try {
    const raw = fs.readFileSync(CREDS_FILE, 'utf8');
    const creds = JSON.parse(raw);
    if (creds.host !== host || creds.port !== port) return null;
    if (creds.expiresAt && Date.now() > creds.expiresAt) {
      clear();
      return null;
    }
    return creds;
  } catch {
    return null;
  }
}

function clear() {
  try {
    fs.unlinkSync(CREDS_FILE);
  } catch {
  }
}

module.exports = { DIR, CREDS_FILE, HISTORY_FILE, save, load, clear };
