 

'use strict';

const readline = require('readline');

let pipeBuffer = '';
let pipeWaiters = [];
let pipeStarted = false;
let pipeEnded = false;

function startPipeReader() {
  pipeStarted = true;
  process.stdin.on('data', (chunk) => {
    pipeBuffer += chunk.toString();
    drainPipe();
  });
  process.stdin.on('end', () => {
    pipeEnded = true;
    drainPipe();
  });
}

function drainPipe() {
  while (pipeWaiters.length > 0) {
    const idx = pipeBuffer.indexOf('\n');
    if (idx === -1) {
      if (pipeEnded && pipeBuffer.length > 0) {
        const cb = pipeWaiters.shift();
        const last = pipeBuffer;
        pipeBuffer = '';
        cb(last.trim());
        continue;
      }
      break;
    }
    const line = pipeBuffer.slice(0, idx);
    pipeBuffer = pipeBuffer.slice(idx + 1);
    const cb = pipeWaiters.shift();
    cb(line.replace(/\r$/, '').trim());
  }
  if (pipeEnded && pipeWaiters.length > 0 && pipeBuffer === '') {
    const cb = pipeWaiters.shift();
    cb('');
  }
}

function pipeQuestion(prompt) {
  if (!pipeStarted) startPipeReader();
  process.stdout.write(prompt);
  return new Promise((resolve) => {
    pipeWaiters.push(resolve);
    drainPipe();
  });
}

function question(prompt) {
  if (!process.stdin.isTTY) {
    return pipeQuestion(prompt);
  }
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    rl.question(prompt, (answer) => {
      rl.close();
      resolve(answer.trim());
    });
  });
}

function readSecret(prompt) {
  if (!process.stdin.isTTY) {
    return pipeQuestion(prompt);
  }
  return new Promise((resolve, reject) => {
    const input = process.stdin;
    const output = process.stdout;
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
