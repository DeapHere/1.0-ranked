'use strict';
// Oldschool Ranked match server: matchmaking, seeds, elo and match history for 1v1 Minecraft 1.0 races.
// Plain Node (18+), no dependencies. State lives in memory and is saved to DATA_DIR/db.json.

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = parseInt(process.env.PORT || '8080', 10);
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const PROTOCOL = 1;
const START_ELO = 1000;
const K_FACTOR = parseInt(process.env.K_FACTOR || '32', 10);
const PLACEMENT_K = 48; // first 5 ranked games move faster
const MIN_RUN_MS = parseInt(process.env.MIN_RUN_MS || '90000', 10); // a finish faster than this is rejected
const READY_TIMEOUT_MS = 120000; // both players must have their world loaded by then
const VOTE_MS = 30000; // how long a draw or new seed offer stays open
const COUNTDOWN_MS = 10000; // the load-in screen before a match (players can chat)
const SOLO_COUNTDOWN_MS = 5000;
const CHAT_MAX = 100, CHAT_GAP_MS = 600;
const AFK_MS = 60000; // no poll for this long while in a match = forfeit
const MAX_MATCH_MS = 3 * 60 * 60 * 1000;
const POLL_WAIT_MS = 20000;
const BLOCKED_MODS = (process.env.BLOCKED_MODS || 'endpractice').split(',').map(s => s.trim()).filter(Boolean);
const SPLITS = ['nether', 'fortress', 'blaze', 'eye', 'stronghold', 'end', 'finish'];

// ---------- storage ----------

fs.mkdirSync(DATA_DIR, { recursive: true });
const DB_FILE = path.join(DATA_DIR, 'db.json');
let db = { players: {}, matches: [], nextMatch: 1 };
try {
  db = Object.assign(db, JSON.parse(fs.readFileSync(DB_FILE, 'utf8')));
} catch (e) {
  if (e.code !== 'ENOENT') { console.error('could not read db.json:', e); process.exit(1); }
}
let saveTimer = null;
// matches in progress are saved too, so a restart (a deploy, a crash, Fly moving the machine) doesn't end them
function writeDb() {
  db.live = [...matches.values()];
  const tmp = DB_FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(db));
  fs.renameSync(tmp, DB_FILE);
}
function save() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try { writeDb(); } catch (e) { console.error('could not save db.json:', e); }
  }, 500);
}

let seedPool = [];
try {
  seedPool = fs.readFileSync(path.join(__dirname, 'seeds.txt'), 'utf8').split(/\r?\n/)
    .map(s => s.replace(/#.*/, '').trim()).filter(s => /^-?\d+$/.test(s));
} catch (e) { /* no pool: random seeds */ }
// a seed from the pool that none of these players has had before (or had longest ago, once they've seen them all)
function pickSeed(keys) {
  if (!seedPool.length) return crypto.randomBytes(8).readBigInt64BE().toString();
  const seen = new Map();
  keys.forEach(k => (db.players[k].seeds || []).forEach((s, i) => seen.set(s, Math.max(seen.get(s) || 0, i + 1))));
  const fresh = seedPool.filter(s => !seen.has(s));
  if (fresh.length) return fresh[Math.floor(Math.random() * fresh.length)];
  return seedPool.slice().sort((a, b) => seen.get(a) - seen.get(b))[0];
}

function remember(key, seed) {
  const p = db.players[key];
  p.seeds = (p.seeds || []).filter(s => s !== seed);
  p.seeds.push(seed);
  if (p.seeds.length > 500) p.seeds.splice(0, p.seeds.length - 500);
}

// ---------- players and sessions ----------

const hash = s => crypto.createHash('sha256').update(s).digest('hex');
const byToken = new Map(); // token -> player key (lower-case name)
for (const [key, p] of Object.entries(db.players)) byToken.set(p.tokenHash, key);

// live, in-memory state per player key
const live = new Map();
// event numbers start from the clock, so after a restart they're always above any number a client has already seen
const SEQ_BASE = Date.now();
function liveOf(key) {
  let l = live.get(key);
  if (!l) {
    l = { events: [], seq: SEQ_BASE, waiters: [], lastSeen: 0, queuedAt: 0, room: null, match: null };
    live.set(key, l);
  }
  return l;
}

function push(key, ev) {
  const l = liveOf(key);
  ev.seq = ++l.seq;
  l.events.push(ev);
  if (l.events.length > 100) l.events.splice(0, l.events.length - 100);
  const waiters = l.waiters;
  l.waiters = [];
  for (const w of waiters) w();
}

function publicPlayer(p) {
  return { name: p.name, elo: p.elo, wins: p.wins, losses: p.losses, played: p.wins + p.losses, best: p.best || null };
}

function rankOf(p) {
  let r = 1;
  for (const o of Object.values(db.players)) if (o.wins + o.losses > 0 && o.elo > p.elo) r++;
  return r;
}

// ---------- matches ----------

const matches = new Map(); // id -> match (running only; finished ones go to db.matches)

// matches that were in progress when the server last stopped carry on, with a fresh minute before anyone counts as gone
function restoreMatches() {
  for (const m of db.live || []) {
    if (!m || m.state === 'done' || !Array.isArray(m.players) || !m.players.every(k => db.players[k])) continue;
    matches.set(m.id, m);
    for (const k of m.players) {
      const l = liveOf(k);
      l.match = m.id;
      l.lastSeen = Date.now();
    }
  }
  if (matches.size) console.log(`restored ${matches.size} match(es) in progress`);
}

// bKey null = a private room: one player racing the clock alone
function createMatch(aKey, bKey, ranked) {
  const id = String(db.nextMatch++);
  const players = bKey ? [aKey, bKey] : [aKey];
  const splits = {};
  for (const k of players) splits[k] = {};
  const m = {
    id, ranked: ranked && !!bKey, solo: !bKey, seed: pickSeed(players), created: Date.now(), state: 'loading', startAt: 0,
    players, ready: {}, splits,
  };
  matches.set(id, m);
  for (const k of m.players) {
    remember(k, m.seed);
    const l = liveOf(k);
    l.match = id;
    l.queuedAt = 0;
    l.room = null;
    const opp = m.solo ? null : db.players[opponentOf(m, k)];
    push(k, { type: 'match', match: id, seed: m.seed, ranked: m.ranked, solo: m.solo, opponent: opp ? opp.name : null, opponentElo: opp ? opp.elo : 0 });
  }
  save();
  console.log(`match ${id} ${m.solo ? 'private' : m.ranked ? 'ranked' : 'room'}: ${players.join(' vs ')}, seed ${m.seed}`);
  return m;
}

function expected(a, b) { return 1 / (1 + Math.pow(10, (b - a) / 400)); }

function endMatch(m, winnerKey, reason) {
  if (m.state === 'done') return;
  m.state = 'done';
  matches.delete(m.id);
  const [aKey, bKey] = m.players;
  const change = {};
  for (const k of m.players) change[k] = 0;
  const elapsed = m.startAt ? Math.max(0, Date.now() - m.startAt) : 0;
  if (m.ranked && winnerKey) {
    const loserKey = winnerKey === aKey ? bKey : aKey;
    const w = db.players[winnerKey], l = db.players[loserKey];
    const kw = (w.wins + w.losses) < 5 ? PLACEMENT_K : K_FACTOR;
    const kl = (l.wins + l.losses) < 5 ? PLACEMENT_K : K_FACTOR;
    const e = expected(w.elo, l.elo);
    change[winnerKey] = Math.max(1, Math.round(kw * (1 - e)));
    change[loserKey] = -Math.max(1, Math.round(kl * (1 - e)));
    w.elo += change[winnerKey];
    l.elo += change[loserKey];
    w.wins++;
    l.losses++;
    if (reason === 'finish' && elapsed > 0 && (!w.best || elapsed < w.best)) { w.best = elapsed; w.bestAt = Date.now(); }
  }
  const record = {
    id: m.id, ranked: m.ranked, seed: m.seed, at: m.created, players: m.players.map(k => db.players[k].name), solo: m.solo,
    winner: winnerKey ? db.players[winnerKey].name : null, reason, time: winnerKey && reason === 'finish' ? elapsed : null,
    change: m.players.map(k => change[k]), splits: m.players.map(k => m.splits[k]),
  };
  db.matches.push(record);
  if (db.matches.length > 5000) db.matches.splice(0, db.matches.length - 5000);
  for (const k of m.players) {
    const l = liveOf(k);
    if (l.match === m.id) l.match = null;
    const p = db.players[k];
    const opp = m.solo ? null : db.players[opponentOf(m, k)];
    push(k, {
      type: 'result', match: m.id, ranked: m.ranked, won: winnerKey === k, draw: !winnerKey, reason,
      time: record.time, winner: record.winner, eloChange: change[k], elo: p.elo,
      opponentElo: opp ? opp.elo : 0, opponentEloChange: opp ? change[opponentOf(m, k)] : 0,
    });
  }
  save();
  console.log(`match ${m.id} over: ${record.winner || 'no winner'} (${reason})`);
}

function vote(b, kind) {
  const key = auth(b);
  const m = ownMatch(key, b.match);
  if (m.solo && kind === 'draw') throw { status: 400, error: 'no draws in a private room' };
  if (m.state !== 'running') throw { status: 409, error: 'the match has not started yet' };
  const now = Date.now();
  m.offers = m.offers || {};
  const offer = m.offers[kind] && now - m.offers[kind].at < VOTE_MS ? m.offers[kind] : null;
  if (m.solo || (offer && offer.by !== key)) {
    if (kind === 'draw') {
      endMatch(m, null, 'draw');
    } else {
      const [a, bKey] = m.players;
      const ranked = m.ranked;
      endMatch(m, null, 'new seed');
      createMatch(a, bKey || null, ranked);
    }
    return { ok: true, accepted: true };
  }
  if (offer) return { ok: true, pending: true, expires: offer.at + VOTE_MS };
  m.lastOffer = m.lastOffer || {};
  const last = m.lastOffer[kind + key] || 0;
  if (now - last < VOTE_MS * 2) throw { status: 429, error: 'wait a minute before asking again' };
  m.offers[kind] = { by: key, at: now };
  m.lastOffer[kind + key] = now;
  push(opponentOf(m, key), { type: kind + 'Offer', match: m.id, expires: now + VOTE_MS });
  return { ok: true, pending: true, expires: now + VOTE_MS };
}

function opponentOf(m, key) { return m.players.length < 2 ? null : m.players[0] === key ? m.players[1] : m.players[0]; }

// ---------- matchmaking loop ----------

setInterval(() => { try { tick(); } catch (e) { console.error('tick failed:', e); } }, 1000);
// matches in progress change often (splits, ready): keep the saved copy fresh
setInterval(() => { if (matches.size) save(); }, 5000);

function tick() {
  const now = Date.now();
  const queued = [...live.entries()].filter(([, l]) => l.queuedAt && !l.match)
    .map(([k, l]) => ({ key: k, elo: db.players[k].elo, wait: now - l.queuedAt }))
    .sort((x, y) => x.elo - y.elo);
  // drop players who stopped polling
  for (const q of queued) if (now - liveOf(q.key).lastSeen > 30000) liveOf(q.key).queuedAt = 0;
  const used = new Set();
  for (let i = 0; i < queued.length; i++) {
    const a = queued[i];
    if (used.has(a.key) || !liveOf(a.key).queuedAt) continue;
    let best = null;
    for (let j = i + 1; j < queued.length; j++) {
      const b = queued[j];
      if (used.has(b.key) || !liveOf(b.key).queuedAt) continue;
      const range = Math.min(1000, 100 + 10 * Math.floor(Math.min(a.wait, b.wait) / 1000));
      if (Math.abs(a.elo - b.elo) <= range && (!best || Math.abs(a.elo - b.elo) < Math.abs(a.elo - best.elo))) best = b;
    }
    if (best) {
      used.add(a.key);
      used.add(best.key);
      createMatch(a.key, best.key, true);
    }
  }
  for (const m of [...matches.values()]) {
    if (m.state === 'loading' && now - m.created > READY_TIMEOUT_MS) {
      const ready = m.players.filter(k => m.ready[k]);
      // the one who loaded wins; nobody loaded = cancelled
      if (m.solo) endMatch(m, null, 'cancelled');
      else endMatch(m, ready.length === 1 ? ready[0] : null, ready.length === 1 ? 'opponent did not load' : 'cancelled');
      continue;
    }
    for (const kind of ['draw', 'seed']) {
      const o = (m.offers || {})[kind];
      if (m.state === 'running' && o && now - o.at >= VOTE_MS) {
        for (const k of m.players) push(k, { type: kind + 'Expired', match: m.id });
        delete m.offers[kind];
      }
    }
    if (m.state === 'running') {
      if (now - m.startAt > MAX_MATCH_MS) { endMatch(m, null, 'time limit'); continue; }
      const gone = m.players.filter(k => now - liveOf(k).lastSeen > AFK_MS);
      if (m.solo && gone.length) endMatch(m, null, 'disconnected');
      else if (gone.length === 2) endMatch(m, null, 'both disconnected');
      else if (gone.length === 1) endMatch(m, opponentOf(m, gone[0]), 'opponent disconnected');
    }
  }
  // stale rooms
  for (const [, l] of live) if (l.room && now - l.lastSeen > 30000) { rooms.delete(l.room); l.room = null; }
}

const rooms = new Map(); // code -> host key

// ---------- http ----------

function send(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'Access-Control-Allow-Origin': '*' });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', c => { data += c; if (data.length > 16384) { reject(new Error('too big')); req.destroy(); } });
    req.on('end', () => { try { resolve(data ? JSON.parse(data) : {}); } catch (e) { reject(e); } });
    req.on('error', reject);
  });
}

function auth(body) {
  const key = body && typeof body.token === 'string' ? byToken.get(hash(body.token)) : null;
  if (!key) throw { status: 401, error: 'not signed in, restart the game' };
  const l = liveOf(key);
  l.lastSeen = Date.now();
  return key;
}

function ownMatch(key, id) {
  const m = matches.get(String(id));
  if (!m || !m.players.includes(key)) throw { status: 404, error: 'no such match' };
  return m;
}

const routes = {
  'POST /api/hello': (b) => {
    if (b.protocol !== PROTOCOL) throw { status: 426, error: 'update the Oldschool Ranked mod' };
    const name = String(b.name || '');
    if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) throw { status: 400, error: 'bad name' };
    const mods = Array.isArray(b.mods) ? b.mods.map(String) : [];
    const blocked = mods.filter(m => BLOCKED_MODS.includes(m));
    const key = name.toLowerCase();
    let p = db.players[key];
    let token = typeof b.token === 'string' ? b.token : null;
    if (p) {
      if (!token || hash(token) !== p.tokenHash) throw { status: 403, error: 'that name is registered on another PC' };
      p.name = name;
    } else {
      token = crypto.randomBytes(24).toString('hex');
      p = db.players[key] = { name, tokenHash: hash(token), elo: START_ELO, wins: 0, losses: 0, joined: Date.now() };
      byToken.set(p.tokenHash, key);
      save();
      console.log(`new player ${name}`);
    }
    const l = liveOf(key);
    l.lastSeen = Date.now();
    l.blocked = blocked;
    return Object.assign(publicPlayer(p), {
      token, rank: rankOf(p), serverTime: Date.now(), blocked, seq: l.seq, motd: process.env.MOTD || '',
    });
  },

  'POST /api/queue': (b) => {
    const key = auth(b);
    const l = liveOf(key);
    if (l.blocked && l.blocked.length) throw { status: 403, error: 'remove ' + l.blocked.join(', ') + ' to play ranked' };
    if (l.match) throw { status: 409, error: 'already in a match' };
    l.queuedAt = Date.now();
    return { queued: true };
  },

  'POST /api/unqueue': (b) => {
    const l = liveOf(auth(b));
    l.queuedAt = 0;
    if (l.room) { rooms.delete(l.room); l.room = null; }
    return { queued: false };
  },

  'POST /api/room/create': (b) => {
    const key = auth(b);
    const l = liveOf(key);
    if (l.match) throw { status: 409, error: 'already in a match' };
    if (l.room) rooms.delete(l.room);
    let code;
    do { code = String(Math.floor(1000 + Math.random() * 9000)); } while (rooms.has(code));
    rooms.set(code, key);
    l.room = code;
    l.queuedAt = 0;
    return { code };
  },

  'POST /api/room/join': (b) => {
    const key = auth(b);
    const code = String(b.code || '').trim();
    const host = rooms.get(code);
    if (!host) throw { status: 404, error: 'no room with that code' };
    if (host === key) throw { status: 400, error: "that's your own room" };
    if (liveOf(key).match || liveOf(host).match) throw { status: 409, error: 'already in a match' };
    rooms.delete(code);
    const m = createMatch(host, key, false);
    return { match: m.id };
  },

  'POST /api/room/private': (b) => {
    const key = auth(b);
    const l = liveOf(key);
    if (l.match) throw { status: 409, error: 'already in a match' };
    if (l.room) { rooms.delete(l.room); l.room = null; }
    const m = createMatch(key, null, false);
    return { match: m.id };
  },

  'POST /api/ready': (b) => {
    const key = auth(b);
    const m = ownMatch(key, b.match);
    if (m.state !== 'loading') return { ok: true };
    m.ready[key] = true;
    if (m.players.every(k => m.ready[k])) {
      m.state = 'running';
      m.startAt = Date.now() + (m.solo ? SOLO_COUNTDOWN_MS : COUNTDOWN_MS);
      for (const k of m.players) push(k, { type: 'start', match: m.id, startAt: m.startAt, serverTime: Date.now() });
    } else {
      push(opponentOf(m, key), { type: 'opponentReady', match: m.id }); // two-player only: solo is always all ready
    }
    return { ok: true };
  },

  'POST /api/split': (b) => {
    const key = auth(b);
    const m = ownMatch(key, b.match);
    if (m.state !== 'running' || Date.now() < m.startAt) throw { status: 409, error: 'match not running' };
    const split = String(b.split);
    if (!SPLITS.includes(split) || split === 'finish') throw { status: 400, error: 'bad split' };
    if (m.splits[key][split]) return { ok: true };
    const t = Date.now() - m.startAt;
    m.splits[key][split] = t;
    if (!m.solo) push(opponentOf(m, key), { type: 'opponentSplit', match: m.id, split, time: t });
    return { ok: true, time: t };
  },

  'POST /api/finish': (b) => {
    const key = auth(b);
    const m = ownMatch(key, b.match);
    if (m.state !== 'running') throw { status: 409, error: 'match not running' };
    const t = Date.now() - m.startAt;
    const igtMs = Number(b.igt) * 50;
    // the server's clock decides the time; the game's own timer can't be ahead of it
    if (t < MIN_RUN_MS) throw { status: 400, error: 'finish rejected: faster than the minimum time' };
    if (!(igtMs > 0) || igtMs > t + 15000) throw { status: 400, error: 'finish rejected: game time does not match' };
    if (!m.splits[key].end) throw { status: 400, error: 'finish rejected: never entered the End' };
    m.splits[key].finish = t;
    endMatch(m, key, 'finish');
    return { ok: true, time: t };
  },

  'POST /api/forfeit': (b) => {
    const key = auth(b);
    const m = ownMatch(key, b.match);
    if (m.solo) endMatch(m, null, m.state === 'running' ? 'forfeit' : 'cancelled');
    else endMatch(m, m.state === 'running' || m.ranked ? opponentOf(m, key) : null, m.state === 'running' ? 'forfeit' : 'cancelled');
    return { ok: true };
  },

  // votes: either player offers, the other has VOTE_MS to accept.
  // draw: the match ends as a draw, no elo either way.
  // seed: the match is called off (no elo) and both get a new match on a new seed straight away. Solo runs reroll at once.
  // match chat: only the two players in the match see it, from the load-in screen to the end of the race
  'POST /api/chat': (b) => {
    const key = auth(b);
    const m = ownMatch(key, b.match);
    if (m.solo) throw { status: 409, error: 'nobody to chat with' };
    const text = String(b.text || '').replace(/[\u0000-\u001f\u007f\u00a7]/g, '').trim().slice(0, CHAT_MAX);
    if (!text) return { ok: true };
    const l = liveOf(key), now = Date.now();
    if (l.lastChat && now - l.lastChat < CHAT_GAP_MS) throw { status: 429, error: 'slow down' };
    l.lastChat = now;
    for (const k of m.players) push(k, { type: 'chat', match: m.id, from: db.players[key].name, text });
    return { ok: true };
  },

  'POST /api/draw': (b) => vote(b, 'draw'),
  'POST /api/newseed': (b) => vote(b, 'seed'),

  'GET /api/leaderboard': () => {
    const list = Object.values(db.players).filter(p => p.wins + p.losses > 0)
      .sort((a, b) => b.elo - a.elo).slice(0, 100).map(publicPlayer);
    return { players: list, online: [...live.values()].filter(l => Date.now() - l.lastSeen < 30000).length };
  },

  // fastest times: each player's best ranked win by finishing (forfeits and draws don't count)
  'GET /api/fastest': () => ({ players: fastest().map(p => ({ name: p.name, time: p.best, at: p.bestAt || null, elo: p.elo })) }),

  'GET /api/matches': () => ({ matches: db.matches.slice(-50).reverse() }),

  'GET /health': () => ({ ok: true, protocol: PROTOCOL }),
};

function poll(req, res, url) {
  let key;
  try { key = auth({ token: url.searchParams.get('token') }); } catch (e) { return send(res, e.status, { error: e.error }); }
  const l = liveOf(key);
  const since = parseInt(url.searchParams.get('since') || '0', 10);
  const reply = () => {
    l.lastSeen = Date.now();
    send(res, 200, {
      events: l.events.filter(e => e.seq > since), serverTime: Date.now(),
      queued: !!l.queuedAt, room: l.room, match: l.match,
    });
  };
  if (l.events.some(e => e.seq > since)) return reply();
  let done = false;
  const finish = () => { if (done) return; done = true; clearTimeout(timer); reply(); };
  const timer = setTimeout(finish, POLL_WAIT_MS);
  l.waiters.push(finish);
  req.on('close', () => { done = true; clearTimeout(timer); });
}

function fastest() {
  return Object.values(db.players).filter(p => p.best > 0).sort((a, b) => a.best - b.best).slice(0, 100);
}

function page() {
  const rows = Object.values(db.players).filter(p => p.wins + p.losses > 0).sort((a, b) => b.elo - a.elo).slice(0, 100)
    .map((p, i) => `<tr><td>${i + 1}</td><td>${p.name}</td><td>${p.elo}</td><td>${p.wins}-${p.losses}</td><td>${p.best ? fmt(p.best) : ''}</td></tr>`).join('');
  const fast = fastest().map((p, i) => `<tr><td>${i + 1}</td><td>${p.name}</td><td>${fmt(p.best)}</td><td>${p.bestAt ? new Date(p.bestAt).toISOString().slice(0, 10) : ''}</td></tr>`).join('');
  const recent = db.matches.filter(m => m.ranked).slice(-20).reverse()
    .map(m => `<tr><td>${m.players[0]} vs ${m.players[1]}</td><td>${m.winner || 'none'}</td><td>${m.reason}</td><td>${m.time ? fmt(m.time) : ''}</td></tr>`).join('');
  return `<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><title>Oldschool Ranked</title>
<style>body{font:15px system-ui;margin:24px auto;max-width:720px;padding:0 16px;background:#16161a;color:#ddd}table{width:100%;border-collapse:collapse;margin-bottom:32px}td,th{padding:6px 8px;border-bottom:1px solid #333;text-align:left}h1{font-size:22px}</style>
<h1>Oldschool Ranked: Minecraft 1.0</h1><h2>Leaderboard</h2><table><tr><th>#</th><th>Player</th><th>Elo</th><th>W-L</th><th>Best</th></tr>${rows || '<tr><td colspan=5>No ranked games yet</td></tr>'}</table>
<h2>Fastest times</h2><table><tr><th>#</th><th>Player</th><th>Time</th><th>Date</th></tr>${fast || '<tr><td colspan=4>No ranked wins by finishing yet</td></tr>'}</table>
<h2>Recent matches</h2><table><tr><th>Match</th><th>Winner</th><th>How</th><th>Time</th></tr>${recent || '<tr><td colspan=4>None yet</td></tr>'}</table>`;
}

function fmt(ms) {
  const s = Math.floor(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}.${String(Math.floor(ms % 1000 / 10)).padStart(2, '0')}`;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  try {
    if (req.method === 'GET' && url.pathname === '/') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(page());
    }
    if (req.method === 'GET' && url.pathname === '/api/poll') return poll(req, res, url);
    const route = routes[req.method + ' ' + url.pathname];
    if (!route) return send(res, 404, { error: 'not found' });
    const body = req.method === 'POST' ? await readBody(req) : {};
    send(res, 200, route(body));
  } catch (e) {
    if (e && e.status) return send(res, e.status, { error: e.error });
    console.error(e);
    send(res, 400, { error: 'bad request' });
  }
});

restoreMatches();
server.on('error', e => { console.error('server error:', e); process.exit(1); }); // e.g. the port is taken

// a bug in one request must never take the whole server (and every match in progress) down
process.on('uncaughtException', e => console.error('uncaught:', e));
process.on('unhandledRejection', e => console.error('unhandled:', e));

server.listen(PORT, () => console.log(`Oldschool Ranked server on :${PORT}, data in ${DATA_DIR}, ${seedPool.length || 'random'} seeds`));

function shutdown() {
  if (saveTimer) { clearTimeout(saveTimer); saveTimer = null; }
  try { writeDb(); } catch (e) { console.error('could not save db.json:', e); }
  process.exit(0);
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

module.exports = { expected };
