'use strict';

function humanSize(bytes) {
  if (bytes == null || bytes < 0) return '-';
  const units = ['B', 'K', 'M', 'G', 'T'];
  let v = bytes;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return (i === 0 ? String(bytes) : v.toFixed(1)) + units[i];
}

function formatDate(ms) {
  if (!ms) return '-';
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function formatLs(entries, { longFormat }) {
  if (!entries.length) return '(empty)';
  if (!longFormat) {
    return wrap(entries.map((e) => e.name + (e.isDir ? '/' : '')));
  }
  return entries.map((e) => {
    const perms = e.perms || '----------';
    const size = e.isDir ? '-' : humanSize(e.size);
    return `${perms} ${String(size).padStart(8)} ${formatDate(e.mtime)} ${e.name}${e.isDir ? '/' : ''}${e.link ? ' -> ' + e.link : ''}`;
  }).join('\n');
}

function wrap(items, width = 100) {
  let line = '';
  const out = [];
  for (const it of items) {
    if (line && line.length + it.length + 2 > width) {
      out.push(line);
      line = '';
    }
    line = line ? line + '  ' + it : it;
  }
  if (line) out.push(line);
  return out.join('\n');
}

function formatTree(nodes, prefix = '') {
  const out = [];
  nodes.forEach((node, i) => {
    const last = i === nodes.length - 1;
    const branch = last ? '└── ' : '├── ';
    out.push(prefix + branch + node.name + (node.isDir ? '/' : ''));
    if (node.children && node.children.length) {
      out.push(...formatTree(node.children, prefix + (last ? '    ' : '│   ')));
    }
  });
  return out.join('\n');
}

function formatUptime(ms) {
  const total = Math.floor(ms / 1000);
  const d = Math.floor(total / 86400);
  const h = Math.floor((total % 86400) / 3600);
  const m = Math.floor((total % 3600) / 60);
  return `${d}d ${h}h ${m}m`;
}

function formatStats(s) {
  const mem = s.memory || {};
  const cpu = s.cpu || {};
  const disk = s.disk || {};
  const load = cpu.load >= 0 ? cpu.load.toFixed(2) : 'n/a';
  const proc = cpu.processCpuPercent >= 0 ? cpu.processCpuPercent.toFixed(1) + '%' : 'n/a';
  const rows = [
    `Uptime:       ${formatUptime(s.uptimeMs || 0)}`,
    `CPU load:     ${load}   process: ${proc}`,
    `Memory heap:  ${humanSize(mem.heapUsed)} / ${humanSize(mem.heapTotal)} (max ${humanSize(mem.heapMax)})`,
    `Disk (root):  ${humanSize(disk.used)} / ${humanSize(disk.total)} (usable ${humanSize(disk.usable)})`,
  ];
  if (s.jvm && s.jvm.javaVersion) rows.push(`Java:         ${s.jvm.javaVersion} (${s.jvm.availableProcessors} cores)`);
  return rows.join('\n');
}

function tpsBar(tps) {
  const filled = Math.max(0, Math.min(20, Math.round(tps)));
  return '█'.repeat(filled) + '░'.repeat(20 - filled);
}

function formatTps(t) {
  return `TPS: 1m ${t.tps1m.toFixed(1)} | 5m ${t.tps5m.toFixed(1)} | 15m ${t.tps15m.toFixed(1)}  [${tpsBar(t.tps1m)}]  tick ${t.tickMs}ms`;
}

function formatMatches(matches) {
  return matches.map((m) => `${m.path}${m.line ? ':' + m.line : ''}: ${m.text}`).join('\n');
}

function formatInfo(e) {
  return [
    `Path:     ${e.path}`,
    `Type:     ${e.isDir ? 'directory' : 'file'}${e.link ? ' (symlink -> ' + e.link + ')' : ''}`,
    `Perms:    ${e.perms || '-'}`,
    `Size:     ${e.isDir ? '-' : humanSize(e.size)}`,
    `Modified: ${formatDate(e.mtime)}`,
  ].join('\n');
}

module.exports = {
  humanSize,
  formatDate,
  formatLs,
  formatTree,
  formatStats,
  formatTps,
  formatMatches,
  formatInfo,
  wrap,
};
