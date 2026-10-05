 

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
