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

const readline = require('readline');
const { formatStats, formatTps } = require('./formatters');

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function runTop(client, refreshMs) {
  if (process.stdin.isTTY) {
    readline.emitKeypressEvents(process.stdin);
    process.stdin.setRawMode(true);
  }
  let stopped = false;
  const onKey = (str, key) => {
    if (key.name === 'q' || key.name === 'escape' || (key.ctrl && key.name === 'c')) stopped = true;
  };
  process.stdin.on('keypress', onKey);
  try {
    while (!stopped) {
      const res = await client.request('monitor.top', {});
      readline.cursorTo(process.stdout, 0, 0);
      readline.clearScreenDown(process.stdout);
      console.log('CreeperCLI top (q to quit)\n');
      console.log(formatStats(res.stats));
      console.log();
      console.log(formatTps(res.tps));
      await sleep(refreshMs);
    }
  } finally {
    process.stdin.removeListener('keypress', onKey);
    if (process.stdin.isTTY) process.stdin.setRawMode(false);
  }
}

module.exports = { runTop };
