 

'use strict';

const fs = require('fs');
const path = require('path');
const https = require('https');
const { DIR } = require('./session');
const pkg = require('../package.json');

const UPDATE_FILE = path.join(DIR, 'update.json');
const CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000; 

function checkUpdate() {
  try {
    const now = Date.now();
    let cache = {};
    try {
      cache = JSON.parse(fs.readFileSync(UPDATE_FILE, 'utf8'));
    } catch {
    }

    if (cache.lastCheck && now - cache.lastCheck < CHECK_INTERVAL_MS) {
      if (cache.latest && isNewerVersion(pkg.version, cache.latest)) {
        console.log(`[Notice] A new version of creepercli (v${cache.latest}) is available! Run "npm install -g creeper-cli" to update.\n`);
      }
      return;
    }

    const req = https.get('https://registry.npmjs.org/creeper-cli/latest', {
      timeout: 2500,
      headers: { 'User-Agent': `creeper-cli/${pkg.version}` }
    }, (res) => {
      if (res.statusCode !== 200) return;
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        try {
          const data = JSON.parse(body);
          if (data && data.version) {
            const latest = data.version;
            fs.mkdirSync(DIR, { recursive: true, mode: 0o700 });
            fs.writeFileSync(UPDATE_FILE, JSON.stringify({ lastCheck: now, latest }), { mode: 0o600 });
            if (isNewerVersion(pkg.version, latest)) {
              console.log(`[Notice] A new version of creepercli (v${latest}) is available! Run "npm install -g creeper-cli" to update.\n`);
            }
          }
        } catch {
        }
      });
    });

    req.on('error', () => {});
    req.end();
  } catch {
  }
}

function isNewerVersion(current, latest) {
  if (!current || !latest) return false;
  const c = current.split('-')[0].split('.').map((x) => parseInt(x, 10) || 0);
  const l = latest.split('-')[0].split('.').map((x) => parseInt(x, 10) || 0);
  const max = Math.max(c.length, l.length);
  for (let i = 0; i < max; i++) {
    const cv = c[i] || 0;
    const lv = l[i] || 0;
    if (lv > cv) return true;
    if (lv < cv) return false;
  }
  return false;
}

module.exports = { checkUpdate };
