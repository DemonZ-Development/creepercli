'use strict';

const { question, readSecret } = require('../prompts');
const session = require('../session');

function saveCreds(cfg, res) {
  session.save({
    host: cfg.host,
    port: cfg.port,
    token: res.token,
    username: res.username,
    expiresAt: Date.now() + res.expiresInMinutes * 60000,
  });
}

async function doLogin(ctx) {
  const user = await question('Username: ');
  const pass = await readSecret('Password: ');
  let res;
  try {
    res = await ctx.client.request('auth.login', { username: user, password: pass }, { timeoutMs: 30000 });
  } catch (err) {
    if (err.code === 'E_TOTP_REQUIRED') {
      const code = await question('TOTP code: ');
      res = await ctx.client.request('auth.login', { username: user, password: pass, totp: code }, { timeoutMs: 30000 });
    } else {
      throw err;
    }
  }
  saveCreds(ctx.cfg, res);
  ctx.username = res.username;
  ctx.cwd = '/';
  console.log(`Authenticated as ${res.username}. Session valid for ${res.expiresInMinutes} minutes.`);
  return res;
}

async function login(ctx) {
  await doLogin(ctx);
}

async function logout(ctx) {
  const creds = session.load(ctx.cfg.host, ctx.cfg.port);
  await ctx.client.request('auth.logout', { token: creds ? creds.token : null }).catch(() => {});
  session.clear();
  ctx.username = null;
  console.log('Logged out. Local credentials cleared.');
}

async function whoami(ctx) {
  const res = await ctx.client.request('auth.whoami');
  console.log(`User ${res.username} (IP ${res.ip}), cwd ${res.cwd}, session expires in ${Math.floor(res.expiresInSeconds / 60)}m${res.totpEnabled ? ', 2FA enabled' : ', 2FA disabled'}`);
}

async function ping(ctx) {
  const res = await ctx.client.request('ping', {}, { timeoutMs: 15000 });
  console.log(`pong (protocol v${res.protocol}, plugin v${res.pluginVersion}, server time ${new Date(res.serverTime).toISOString()})`);
}

async function passwd(ctx) {
  const oldP = await readSecret('Current password: ');
  const newP = await readSecret('New password: ');
  if (newP.length < 8) throw new Error('New password must be at least 8 characters');
  await ctx.client.request('auth.passwd', { oldPassword: oldP, newPassword: newP });
  console.log('Password changed. Other sessions were invalidated.');
}

async function totp(ctx, args) {
  const sub = args[0];
  if (sub === 'setup') {
    const pass = await readSecret('Password: ');
    const res = await ctx.client.request('auth.totp.setup', { password: pass });
    console.log('Scan the QR below with your authenticator app, or enter the secret manually:');
    console.log(`Secret: ${res.secret}`);
    console.log(`(setup expires in ${Math.floor((res.expiresInSeconds || 600) / 60)} minutes)`);
    const qr = require('qrcode-terminal');
    qr.generate(res.otpauthUrl, { small: true });
    const code = await question('Enter the 6-digit code from your authenticator app: ');
    await ctx.client.request('auth.totp.verify', { code });
    console.log('TOTP enabled.');
  } else if (sub === 'disable') {
    const pass = await readSecret('Password: ');
    const code = await question('TOTP code: ');
    await ctx.client.request('auth.totp.disable', { password: pass, code });
    console.log('TOTP disabled.');
  } else {
    throw new Error('Usage: totp <setup|disable>');
  }
}

module.exports = { login, logout, whoami, ping, passwd, totp, doLogin };
