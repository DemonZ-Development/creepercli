 

'use strict';

const fs = require('fs');
const path = require('path');
const { DIR, HISTORY_FILE } = require('./session');

const CONFIG_FILE = path.join(DIR, 'config.json');

const DEFAULTS = {
  host: '127.0.0.1',
  port: 45678,
  editor: process.env.EDITOR || 'vi',
  topRefreshMs: 2000,
};

function load() {
  let file = {};
  try {
    file = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8'));
  } catch {
  }
  const cfg = { ...DEFAULTS, ...file };
  cfg.historyFile = HISTORY_FILE;
  return cfg;
}

function save(newConfig) {
  try {
    fs.mkdirSync(DIR, { recursive: true, mode: 0o700 });
    const existing = load();
    const updated = { ...existing, ...newConfig };
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(updated, null, 2), { mode: 0o600 });
  } catch {
  }
}

function applyFlags(cfg, flags) {
  if (flags.host) cfg.host = flags.host;
  if (flags.port) cfg.port = flags.port;
  if (flags.editor) cfg.editor = flags.editor;
  if (flags.refresh) cfg.topRefreshMs = flags.refresh;
  return cfg;
}

module.exports = { load, save, applyFlags, CONFIG_FILE, DEFAULTS };
