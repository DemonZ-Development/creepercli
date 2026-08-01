'use strict';

const readline = require('readline');

function question(prompt) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    rl.question(prompt, (answer) => {
      rl.close();
      resolve(answer.trim());
    });
  });
}

function readSecret(prompt) {
  return new Promise((resolve, reject) => {
    const input = process.stdin;
    const output = process.stdout;
    if (!input.isTTY) {
      const rl = readline.createInterface({ input, output });
      rl.question(prompt, (answer) => {
        rl.close();
        resolve(answer.trim());
      });
      return;
    }
    input.setRawMode(true);
    input.resume();
    output.write(prompt);
    let value = '';
    const onData = (chunk) => {
      for (const ch of chunk.toString()) {
        if (ch === '\u0003') {
          cleanup();
          reject(new Error('Aborted'));
          return;
        }
        if (ch === '\r' || ch === '\n') {
          output.write('\n');
          cleanup();
          resolve(value);
          return;
        }
        if (ch === '\u007f' || ch === '\b') {
          if (value.length > 0) {
            value = value.slice(0, -1);
            output.write('\b \b');
          }
        } else {
          value += ch;
          output.write('*');
        }
      }
    };
    const cleanup = () => {
      input.removeListener('data', onData);
      input.setRawMode(false);
      input.pause();
    };
    input.on('data', onData);
  });
}

async function confirm(text, defYes = false) {
  const answer = await question(text + (defYes ? ' [Y/n] ' : ' [y/N] '));
  if (!answer) return defYes;
  return /^y(es)?$/i.test(answer.trim());
}

module.exports = { question, readSecret, confirm };
