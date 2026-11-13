// End-to-end test: two fake players queue, get matched, load, race and finish. Run: node test.js
'use strict';
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const assert = require('assert');

const PORT = 18000 + Math.floor(Math.random() * 1000);
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ranked-test-'));
const srv = spawn(process.execPath, [path.join(__dirname, 'server.js')], {
  env: Object.assign({}, process.env, { PORT, DATA_DIR: dir, MIN_RUN_MS: '0' }), stdio: 'inherit',
});
const base = `http://127.0.0.1:${PORT}`;
const sleep = ms => new Promise(r => setTimeout(r, ms));
async function post(p, body) {
  const r = await fetch(base + p, { method: 'POST', body: JSON.stringify(body) });
  return { status: r.status, body: await r.json() };
}
async function poll(c) {
  const r = await fetch(`${base}/api/poll?token=${c.token}&since=${c.since}`);
  const b = await r.json();
  for (const e of b.events) c.since = Math.max(c.since, e.seq);
  return b.events;
}
// events that came in one poll but weren't asked for yet are kept, so two events in one reply both count
async function until(c, type) {
  c.queue = c.queue || [];
  for (let i = 0; i < 10; i++) {
    const at = c.queue.findIndex(e => e.type === type);
    if (at >= 0) return c.queue.splice(at, 1)[0];
    c.queue.push(...await poll(c));
  }
  throw new Error('no ' + type);
}

(async () => {
  await sleep(500);
  const a = (await post('/api/hello', { protocol: 1, name: 'Alice', mods: [] })).body;
  const b = (await post('/api/hello', { protocol: 1, name: 'Bob', mods: [] })).body;
  assert.strictEqual(a.elo, 1000);
  assert.strictEqual((await post('/api/hello', { protocol: 1, name: 'alice' })).status, 403, 'name theft blocked');
  const bad = (await post('/api/hello', { protocol: 1, name: 'Cheater', mods: ['endpractice'] })).body;
  assert.strictEqual((await post('/api/queue', { token: bad.token })).status, 403, 'blocked mod cannot queue');
  const A = { token: a.token, since: 0 }, B = { token: b.token, since: 0 };
  await post('/api/queue', { token: A.token });
  await post('/api/queue', { token: B.token });
  const ma = await until(A, 'match'), mb = await until(B, 'match');
  assert.strictEqual(ma.seed, mb.seed);
  assert.strictEqual(ma.opponent, 'Bob');
  await post('/api/ready', { token: A.token, match: ma.match });
  await post('/api/ready', { token: B.token, match: ma.match });
  const st = await until(A, 'start');
  assert.ok(st.startAt > st.serverTime);
  assert.strictEqual((await post('/api/split', { token: A.token, match: ma.match, split: 'nether' })).status, 409, 'no splits before go');
  await sleep(st.startAt - st.serverTime + 100);
  assert.strictEqual((await post('/api/split', { token: A.token, match: ma.match, split: 'nether' })).status, 200);
  const os1 = await until(B, 'opponentSplit');
  assert.strictEqual(os1.split, 'nether');
  assert.strictEqual((await post('/api/finish', { token: A.token, match: ma.match, igt: 4 })).status, 400, 'no finish without End');
  await post('/api/split', { token: A.token, match: ma.match, split: 'end' });
  assert.strictEqual((await post('/api/finish', { token: A.token, match: ma.match, igt: 100000 })).status, 400, 'igt ahead of server clock rejected');
  assert.strictEqual((await post('/api/finish', { token: A.token, match: ma.match, igt: 4 })).status, 200);
  const ra = await until(A, 'result'), rb = await until(B, 'result');
  assert.ok(ra.won && !rb.won);
  assert.strictEqual(ra.eloChange, 24);
  assert.strictEqual(rb.eloChange, -24);
  // private room, unranked
  const room = (await post('/api/room/create', { token: A.token })).body;
  const j = await post('/api/room/join', { token: B.token, code: room.code });
  assert.strictEqual(j.status, 200);
  const rm = await until(A, 'match');
  assert.strictEqual(rm.ranked, false);
  await post('/api/forfeit', { token: B.token, match: rm.match });
  const rr = await until(A, 'result');
  assert.strictEqual(rr.eloChange, 0);
  // private room: solo, unranked, finish counts
  const pr = await post('/api/room/private', { token: A.token });
  assert.strictEqual(pr.status, 200);
  const pm = await until(A, 'match');
  assert.strictEqual(pm.solo, true);
  assert.strictEqual(pm.opponent, null);
  await post('/api/ready', { token: A.token, match: pm.match });
  const ps = await until(A, 'start');
  await sleep(ps.startAt - ps.serverTime + 100);
  await post('/api/split', { token: A.token, match: pm.match, split: 'end' });
  assert.strictEqual((await post('/api/finish', { token: A.token, match: pm.match, igt: 4 })).status, 200);
  const pres = await until(A, 'result');
  assert.ok(pres.won && pres.eloChange === 0 && pres.reason === 'finish');
  // draw vote: ranked match, A offers, B accepts: a draw with no elo for either
  await post('/api/queue', { token: A.token });
  await post('/api/queue', { token: B.token });
  const va = await until(A, 'match');
  await until(B, 'match');
  await post('/api/ready', { token: A.token, match: va.match });
  await post('/api/ready', { token: B.token, match: va.match });
  const vs = await until(A, 'start');
  await sleep(vs.startAt - vs.serverTime + 100);
  assert.ok((await post('/api/draw', { token: A.token, match: va.match })).body.pending, 'offer is pending');
  assert.ok((await post('/api/draw', { token: A.token, match: va.match })).body.pending, 'offering twice just waits');
  await until(B, 'drawOffer');
  assert.ok((await post('/api/draw', { token: B.token, match: va.match })).body.accepted, 'accepted');
  const vra = await until(A, 'result'), vrb = await until(B, 'result');
  assert.ok(vra.draw && vra.reason === 'draw' && vra.eloChange === 0 && vrb.eloChange === 0, 'a draw moves no elo');
  // new seed vote: B offers, A accepts: called off with no elo, then a new ranked match on a new seed
  await post('/api/queue', { token: A.token });
  await post('/api/queue', { token: B.token });
  const na = await until(A, 'match');
  await until(B, 'match');
  await post('/api/ready', { token: A.token, match: na.match });
  await post('/api/ready', { token: B.token, match: na.match });
  const ns = await until(A, 'start');
  await sleep(ns.startAt - ns.serverTime + 100);
  assert.ok((await post('/api/newseed', { token: B.token, match: na.match })).body.pending);
  await until(A, 'seedOffer');
  assert.ok((await post('/api/newseed', { token: A.token, match: na.match })).body.accepted);
  const nr = await until(A, 'result');
  assert.ok(nr.reason === 'new seed' && nr.eloChange === 0);
  const nn = await until(A, 'match');
  assert.ok(nn.ranked && nn.match !== na.match && nn.seed !== na.seed, 'new match on a new seed');
  await post('/api/ready', { token: A.token, match: nn.match });
  await post('/api/ready', { token: B.token, match: nn.match });
  const n2 = await until(A, 'start');
  await sleep(n2.startAt - n2.serverTime + 100);
  await post('/api/draw', { token: A.token, match: nn.match });
  await post('/api/draw', { token: B.token, match: nn.match }); // tidy up with a draw
  await until(A, 'result');
  const lb = await (await fetch(base + '/api/leaderboard')).json();
  assert.deepStrictEqual(lb.players.map(p => [p.name, p.elo]), [['Alice', 1024], ['Bob', 976]]);
  const html = await (await fetch(base + '/')).text();
  assert.ok(html.includes('Alice'));
  console.log('ALL TESTS PASSED');
  srv.kill();
  process.exit(0);
})().catch(e => { console.error(e); srv.kill(); process.exit(1); });
