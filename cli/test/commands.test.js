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

const test = require('node:test');
const assert = require('node:assert/strict');
const { splitArgs, find, COMMANDS } = require('../src/commands/index');

test('splitArgs', async (t) => {
  await t.test('splits normal space-delimited arguments', () => {
    assert.deepEqual(splitArgs('ls -l -a /tmp'), ['ls', '-l', '-a', '/tmp']);
    assert.deepEqual(splitArgs('  cat   file1.txt   file2.txt  '), ['cat', 'file1.txt', 'file2.txt']);
    assert.deepEqual(splitArgs(''), []);
  });

  await t.test('handles single quotes', () => {
    assert.deepEqual(splitArgs("echo 'hello world'"), ['echo', 'hello world']);
    assert.deepEqual(splitArgs("grep 'foo bar' /path/to/file"), ['grep', 'foo bar', '/path/to/file']);
    assert.deepEqual(splitArgs("echo 'it\"s fine'"), ['echo', 'it"s fine']);
  });

  await t.test('handles double quotes', () => {
    assert.deepEqual(splitArgs('echo "hello world"'), ['echo', 'hello world']);
    assert.deepEqual(splitArgs('find --glob "*.jar"'), ['find', '--glob', '*.jar']);
    assert.deepEqual(splitArgs('echo "it\'s fine"'), ['echo', "it's fine"]);
  });

  await t.test('handles escaped quotes', () => {
    assert.deepEqual(splitArgs('echo "hello \\"world\\""'), ['echo', 'hello "world"']);
    assert.deepEqual(splitArgs("echo 'it\\'s fine'"), ['echo', "it's fine"]);
    assert.deepEqual(splitArgs('say \\"hello\\"'), ['say', '"hello"']);
  });
});

test('command lookup find(cmdName)', async (t) => {
  await t.test('returns command object for existing commands', () => {
    const pwdCmd = find('pwd');
    assert.ok(pwdCmd);
    assert.equal(pwdCmd.name, 'pwd');
    assert.equal(typeof pwdCmd.run, 'function');
    assert.ok(typeof pwdCmd.desc, 'string');

    const lsCmd = find('ls');
    assert.ok(lsCmd);
    assert.equal(lsCmd.name, 'ls');

    const csyncCmd = find('csync');
    assert.ok(csyncCmd);
    assert.equal(csyncCmd.name, 'csync');
  });

  await t.test('returns undefined for non-existent commands', () => {
    assert.equal(find('nonexistent'), undefined);
    assert.equal(find(''), undefined);
    assert.equal(find('PWD'), undefined);
  });

  await t.test('finds all registered commands in COMMANDS array', () => {
    for (const cmd of COMMANDS) {
      assert.equal(find(cmd.name), cmd);
    }
  });
});
