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

const fsCmds = require('./fs');
const searchCmds = require('./search');
const execCmds = require('./exec');
const monitorCmds = require('./monitor');
const authCmds = require('./auth');
const { cpush, cpull, csync } = require('../transfer');
const { friendly } = require('../protocol');

const COMMANDS = [
  { name: 'pwd', run: fsCmds.pwd, desc: 'Print working directory' },
  { name: 'ls', run: fsCmds.ls, desc: 'List directory (flags: -l, -a)' },
  { name: 'cd', run: fsCmds.cd, desc: 'Change directory' },
  { name: 'tree', run: fsCmds.tree, desc: 'Show directory tree (flag: --depth N)' },
  { name: 'cat', run: fsCmds.cat, desc: 'Print file contents' },
  { name: 'head', run: fsCmds.head, desc: 'Print first lines of a file (-n N)' },
  { name: 'tail', run: fsCmds.tail, desc: 'Print last lines of a file (-n N)' },
  { name: 'wc', run: fsCmds.wc, desc: 'Count lines/words/chars of a file' },
  { name: 'touch', run: fsCmds.touch, desc: 'Create or update a file' },
  { name: 'mkdir', run: fsCmds.mkdir, desc: 'Create directory (flag: -p)' },
  { name: 'rm', run: fsCmds.rm, desc: 'Remove file/directory (flag: -r)' },
  { name: 'cp', run: fsCmds.cp, desc: 'Copy file/directory (flag: -r)' },
  { name: 'mv', run: fsCmds.mv, desc: 'Move/rename a file' },
  { name: 'info', run: fsCmds.info, desc: 'Show file info (perms, size, mtime)' },
  { name: 'edit', run: fsCmds.edit, desc: 'Edit a remote file in your local $EDITOR' },
  { name: 'grep', run: searchCmds.grep, desc: 'Search file contents (flags: -i, --max-depth N)' },
  { name: 'find', run: searchCmds.find, desc: 'Find files by glob (flag: --glob "**/*.jar")' },
  { name: 'exec', run: execCmds.exec, desc: 'Run a Minecraft console command (allowlisted)' },
  { name: 'say', run: execCmds.say, desc: 'Shortcut for "exec say ..."' },
  { name: 'restart', run: execCmds.restart, desc: 'Shortcut for "exec restart"' },
  { name: 'stats', run: monitorCmds.stats, desc: 'Server CPU/RAM/disk stats' },
  { name: 'tps', run: monitorCmds.tps, desc: 'Server TPS (1m/5m/15m)' },
  { name: 'log', run: monitorCmds.log, desc: 'Follow the server console (flag: --grep <regex>)' },
  { name: 'top', run: monitorCmds.top, desc: 'Live dashboard (q to quit)' },
  { name: 'cpush', run: cpush, desc: 'Upload a local file to the server' },
  { name: 'cpull', run: cpull, desc: 'Download a server file locally' },
  { name: 'csync', run: csync, desc: 'Synchronize local/remote directories (flag: --yes)' },
  { name: 'login', run: authCmds.login, desc: 'Authenticate and store a session token' },
  { name: 'logout', run: authCmds.logout, desc: 'Invalidate session and clear local credentials' },
  { name: 'passwd', run: authCmds.passwd, desc: 'Change your password' },
  { name: 'totp', run: authCmds.totp, desc: 'Manage 2FA: totp setup | totp disable' },
  { name: 'whoami', run: authCmds.whoami, desc: 'Show current session info' },
  { name: 'ping', run: authCmds.ping, desc: 'Check server connectivity' },
  { name: 'help', run: help, desc: 'Show this help' },
  { name: 'exit', run: exitCmd, desc: 'Leave the REPL' },
  { name: 'quit', run: exitCmd, desc: 'Leave the REPL' },
  { name: 'q', run: exitCmd, desc: 'Leave the REPL' },
];

function find(name) {
  return COMMANDS.find((c) => c.name === name);
}

function splitArgs(input) {
  const args = [];
  let cur = '';
  let quote = null;
  let escaped = false;
  for (const ch of input) {
    if (escaped) {
      cur += ch;
      escaped = false;
    } else if (ch === '\\') {
      escaped = true;
    } else if (quote) {
      if (ch === quote) quote = null;
      else cur += ch;
    } else if (ch === '"' || ch === "'") {
      quote = ch;
    } else if (/\s/.test(ch)) {
      if (cur) {
        args.push(cur);
        cur = '';
      }
    } else {
      cur += ch;
    }
  }
  if (escaped) cur += '\\';
  if (cur) args.push(cur);
  return args;
}

async function runCommand(ctx, input) {
  let trimmed = input.trim();
  if (!trimmed || trimmed.startsWith('#')) return 0;
  
  // Automatically strip redundant "creepercli" or "creepercli.exe" prefix if typed inside the REPL
  if (trimmed.startsWith('creepercli.exe ')) {
    trimmed = trimmed.slice(15).trim();
  } else if (trimmed.startsWith('creepercli ')) {
    trimmed = trimmed.slice(11).trim();
  }

  const args = splitArgs(trimmed);
  const cmd = find(args[0]);
  if (!cmd) {
    console.error(`Unknown command: ${args[0]} (try "help")`);
    return 1;
  }
  try {
    const rc = await cmd.run(ctx, args.slice(1));
    return rc == null ? 0 : rc;
  } catch (err) {
    console.error(`Error: ${friendly(err)}`);
    return 1;
  }
}

function help() {
  const width = Math.max(...COMMANDS.map((c) => c.name.length)) + 2;
  for (const c of COMMANDS) {
    console.log(c.name.padEnd(width) + c.desc);
  }
}

function exitCmd(ctx) {
  ctx.exit = true;
  return 0;
}

module.exports = { COMMANDS, find, splitArgs, runCommand, help };
