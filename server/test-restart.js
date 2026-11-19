// A match in progress survives a server restart. Run: node test-restart.js
'use strict';
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const assert = require('assert');

const PORT = 19000 + Math.floor(Math.random() * 1000);
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ranked-restart-'));
const base = `http://127.0.0.1:${PORT}`;
const sleep = ms => new Promise(r => setTimeout(r, ms));
const start = () => spawn(process.execPath, [path.join(__dirname, 'server.js')], {
  env: Object.assign({}, process.env, { PORT, DATA_DIR: dir, MIN_RUN_MS: '0' }), stdio: 'inherit',
});
async function post(p, body) {
  const r = await fetch(base + p, { method: 'POST', body: JSON.stringify(body) });
  return { status: r.status, body: await r.json() };
}
async function poll(c) {
  const r = await fetch(`${base}/api/poll?token=${c.token}&since=${c.since}`);
  const b = await r.json();
  for (const e of b.events) c.since = Math.max(c.since, e.seq);
  return b;
}
async function until(c, type) {
  for (let i = 0; i < 10; i++) {
    const ev = (await poll(c)).events.find(e => e.type === type);
    if (ev) return ev;
  }
  throw new Error('no ' + type);
}

(async () => {
  let srv = start();
  await sleep(500);
  const ah = (await post('/api/hello', { protocol: 2, name: 'Alice', mods: [] })).body;
  const bh = (await post('/api/hello', { protocol: 2, name: 'Bob', mods: [] })).body;
  const a = { token: ah.token, since: ah.seq }, b = { token: bh.token, since: bh.seq };
  await post('/api/queue', { token: a.token });
  await post('/api/queue', { token: b.token });
  const m = await until(a, 'match');
  await until(b, 'match');
  await post('/api/ready', { token: a.token, match: m.match });
  await post('/api/ready', { token: b.token, match: m.match });
  await until(a, 'start');
  await until(b, 'start');
  await sleep(5500);
  await post('/api/split', { token: a.token, match: m.match, split: 'nether', igt: 100 });

  // restart, like a deploy
  srv.kill('SIGTERM');
  await new Promise(r => srv.on('exit', r));
  srv = start();
  await sleep(700);

  const s = await poll(a);
  assert.strictEqual(s.match, m.match, 'match still there after restart');
  // events after the restart still reach a client that kept its old position
  await post('/api/split', { token: b.token, match: m.match, split: 'nether', igt: 120 });
  const ev = await until(a, 'opponentSplit');
  assert.strictEqual(ev.split, 'nether');
  await post('/api/forfeit', { token: b.token, match: m.match });
  const res = await until(a, 'result');
  assert.strictEqual(res.won, true, 'result after restart');
  console.log('RESTART TEST PASSED');
  srv.kill();
  process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
