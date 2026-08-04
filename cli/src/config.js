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
