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

async function exec(ctx, args) {
  if (!args.length) throw new Error('Usage: exec <minecraft command>');
  const command = args.join(' ');
  const res = await ctx.client.request('exec.run', { command }, { timeoutMs: 150000 });
  if (res.lines && res.lines.length) console.log(res.lines.join('\n'));
  if (!res.success) console.error('Command reported failure');
  if (res.elapsedMs != null) console.error(`(completed in ${res.elapsedMs}ms)`);
  return res.success ? 0 : 1;
}

function say(ctx, args) {
  return exec(ctx, ['say', ...args]);
}

function restart(ctx, args) {
  return exec(ctx, ['restart', ...args]);
}

module.exports = { exec, say, restart };
