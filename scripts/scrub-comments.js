
'use strict';

const fs = require('fs');
const path = require('path');
const acorn = require('acorn');

function stripJs(src) {
  const collectedComments = [];
  let ast;
  try {
    ast = acorn.parse(src, {
      ecmaVersion: 2022,
      sourceType: 'script',
      allowHashBang: true,
      locations: false,
      ranges: true,
      onComment: (block, text, start, end) => {
        collectedComments.push({ start, end, type: block ? 'Block' : 'Line' });
      },
    });
  } catch (e) {
    throw new Error('Parse failed: ' + e.message);
  }

  const commentRanges = collectedComments.map(c => [c.start, c.end, c.type]);
  commentRanges.sort((a, b) => a[0] - b[0]);

  const skipRanges = [];
  walk(ast, (node) => {
    if (node.type === 'RegExpLiteral') {
      skipRanges.push([node.start, node.end]);
    }
  });
  skipRanges.sort((a, b) => a[0] - b[0]);

  let out = '';
  let cursor = 0;
  for (const [start, end, type] of commentRanges) {
    if (isInside(start, skipRanges) || isInside(end - 1, skipRanges)) {
      continue;
    }
    while (cursor < start) {
      out += src[cursor];
      cursor++;
    }
    if (type === 'Line') {
      const between = src.substring(start, end);
      const newlineIdx = between.lastIndexOf('\n');
      if (newlineIdx >= 0) {
        out += between.substring(newlineIdx + 1);
      }
    } else {
      out += ' ';
    }
    cursor = end;
  }
  out += src.substring(cursor);
  return out;
}

function isInside(pos, ranges) {
  for (const [s, e] of ranges) {
    if (pos >= s && pos < e) return true;
  }
  return false;
}

function walk(node, fn) {
  if (!node || typeof node !== 'object') return;
  fn(node);
  for (const k of Object.keys(node)) {
    const v = node[k];
    if (Array.isArray(v)) {
      for (const x of v) walk(x, fn);
    } else if (v && typeof v === 'object' && typeof v.type === 'string') {
      walk(v, fn);
    }
  }
}

function stripJava(src) {
  let out = '';
  let i = 0;
  const n = src.length;
  while (i < n) {
    const ch = src[i];
    const nx = i + 1 < n ? src[i + 1] : '';
    if (ch === '/' && nx === '/') {
      while (i < n && src[i] !== '\n') i++;
      continue;
    }
    if (ch === '/' && nx === '*') {
      i += 2;
      while (i + 1 < n && !(src[i] === '*' && src[i + 1] === '/')) i++;
      i += 2;
      continue;
    }
    if (ch === '"') {
      out += ch; i++;
      while (i < n && src[i] !== '"') {
        if (src[i] === '\\' && i + 1 < n) { out += src[i] + src[i + 1]; i += 2; }
        else { out += src[i]; i++; }
      }
      if (i < n) { out += src[i]; i++; }
      continue;
    }
    if (ch === "'") {
      out += ch; i++;
      while (i < n && src[i] !== "'") {
        if (src[i] === '\\' && i + 1 < n) { out += src[i] + src[i + 1]; i += 2; }
        else { out += src[i]; i++; }
      }
      if (i < n) { out += src[i]; i++; }
      continue;
    }
    out += ch;
    i++;
  }
  return out;
}

function stripXml(src) {
  let out = '';
  let i = 0;
  const n = src.length;
  while (i < n) {
    const ch = src[i];
    const nx = i + 1 < n ? src[i + 1] : '';
    if (ch === '<' && nx === '!' && i + 2 < n && src[i + 2] === '-' && i + 3 < n && src[i + 3] === '-') {
      i += 4;
      while (i + 2 < n && !(src[i] === '-' && src[i + 1] === '-' && src[i + 2] === '>')) i++;
      i += 3;
      if (i < n) i++;
      continue;
    }
    if (ch === '"') {
      out += ch; i++;
      while (i < n && src[i] !== '"') { out += src[i]; i++; }
      if (i < n) { out += src[i]; i++; }
      continue;
    }
    if (ch === "'") {
      out += ch; i++;
      while (i < n && src[i] !== "'") { out += src[i]; i++; }
      if (i < n) { out += src[i]; i++; }
      continue;
    }
    out += ch;
    i++;
  }
  return out;
}

function stripYaml(src) {
  const lines = src.split('\n');
  const out = [];
  for (const line of lines) {
    if (/^\s*#/.test(line)) continue;
    let trimmed = line.replace(/\s+$/, '');
    let inString = false;
    let quote = '';
    let commentStart = -1;
    for (let i = 0; i < trimmed.length; i++) {
      const ch = trimmed[i];
      if (!inString) {
        if (ch === '"') { inString = true; quote = '"'; }
        else if (ch === "'") { inString = true; quote = "'"; }
        else if (ch === '#') { commentStart = i; break; }
      } else {
        if (ch === quote) inString = false;
      }
    }
    if (commentStart >= 0) trimmed = trimmed.substring(0, commentStart).replace(/\s+$/, '');
    if (trimmed.length > 0 || out.length === 0 || out[out.length - 1].length > 0) {
      out.push(trimmed);
    }
  }
  return out.join('\n') + (src.endsWith('\n') ? '\n' : '');
}

function stripJson(src) {
  let out = '';
  let i = 0;
  const n = src.length;
  let inString = false;
  let escape = false;
  while (i < n) {
    const ch = src[i];
    if (inString) {
      out += ch;
      if (escape) {
        escape = false;
      } else if (ch === '\\') {
        escape = true;
      } else if (ch === '"') {
        inString = false;
      }
      i++;
      continue;
    }
    if (ch === '"') {
      out += ch;
      inString = true;
      i++;
      continue;
    }
    out += ch;
    i++;
  }
  return out;
}

function stripMarkdown(src) {
  const lines = src.split('\n');
  const out = [];
  for (const line of lines) {
    const trim = line.trimStart();
    if (trim.startsWith('<!--')) {
      let j = out.length - 1;
      while (j >= 0) {
        const prev = out[j];
        if (/-->$/.test(prev.trimEnd())) break;
        if (prev.trimStart() === '') { j--; continue; }
        break;
      }
      while (j >= 0) { out.splice(j, 1); j--; }
      continue;
    }
    out.push(line);
  }
  return out.join('\n');
}

const ext = process.argv[2];
const file = process.argv[3];
const preserve = process.argv[4] === 'preserve';

let src = fs.readFileSync(file, 'utf8');
let out;
switch (ext) {
  case '.java': out = preserve ? src : stripJava(src); break;
  case '.js':   out = preserve ? src : stripJs(src);   break;
  case '.xml':  out = preserve ? src : stripXml(src);  break;
  case '.md':   out = stripMarkdown(src);                break;
  case '.yml':
  case '.yaml': out = preserve ? src : stripYaml(src);  break;
  case '.json': out = stripJson(src);                   break;
  default: out = src;
}
if (out !== src) {
  fs.writeFileSync(file, out, 'utf8');
  process.stdout.write('SCRUBBED ' + file + '\n');
}