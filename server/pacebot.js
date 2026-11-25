'use strict';
// PaceTracker1.0: the Discord side of the pace feed. Posts each split as its own message, with a small icon,
// and pings the roles of players who picked a pace for that split from the dropdowns (like PaceMan).
//
// Needs a Discord application with a bot in the server (Fly secrets):
//   DISCORD_APP_ID, DISCORD_PUBLIC_KEY, DISCORD_BOT_TOKEN, PACE_GUILD (server id), PACE_CHANNEL (channel id)
// and its Interactions Endpoint URL set to <PUBLIC_URL>/api/discord. Without those it falls back to the
// PACE_WEBHOOK, with no role pings.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const API = (process.env.DISCORD_API || 'https://discord.com/api/v10').replace(/\/$/, '');
const APP_ID = process.env.DISCORD_APP_ID || '';
const PUBLIC_KEY = /^[0-9a-f]{64}$/i.test(process.env.DISCORD_PUBLIC_KEY || '') ? process.env.DISCORD_PUBLIC_KEY : '';
const BOT_TOKEN = process.env.DISCORD_BOT_TOKEN || '';
const GUILD = process.env.PACE_GUILD || '';
const CHANNEL = process.env.PACE_CHANNEL || '';
const WEBHOOK = process.env.PACE_WEBHOOK || '';
const BOT = !!(APP_ID && PUBLIC_KEY && BOT_TOKEN && GUILD && CHANNEL);
const NAME = 'PaceTracker1.0';

const SPLITS = ['nether', 'fortress', 'pit', 'end', 'finish'];
const NAMES = { nether: 'Nether', fortress: 'Fortress', pit: 'Pit start', end: 'End', finish: 'Finish' };
const ROLE_NAMES = { nether: 'Enter Nether', fortress: 'Fortress', pit: 'Pit Start', end: 'Enter End', finish: 'Finish' };
// the paces players can pick, in minutes (only for these splits; the others post without pings).
// PACE_TIERS=pit:11.5/12/12.5,end:25/26/999 replaces a split's list (999 = any)
const GREY = 0x949ba4; // role colour, so the pings show up grey
const ANY = 999; // a pace of 'any': pinged for every one
const TIERS = {
  pit: [11.5, 12, 12.5, 13, 13.5, 14],
  end: [25, 26, 27, 28, 29, 30, ANY],
};
for (const part of (process.env.PACE_TIERS || '').split(',')) {
  const [s, list] = part.split(':');
  const mins = (list || '').split('/').map(Number).filter(n => n > 0 && (n < 360 || n === ANY));
  if (TIERS[s] && mins.length) TIERS[s] = [...new Set(mins)].sort((a, b) => a - b).slice(0, 24);
}
const clock = (mins) => `${Math.floor(mins)}:${String(Math.round(mins * 60) % 60).padStart(2, '0')}`;
const roleName = (s, mins) => mins === ANY ? `Any ${ROLE_NAMES[s]}` : `Sub ${clock(mins)} ${ROLE_NAMES[s]}`;

let db, save, log = console.log;

async function api(method, route, body) {
  for (let tries = 0; ; tries++) {
    const res = await fetch(API + route, {
      method,
      headers: { Authorization: `Bot ${BOT_TOKEN}`, 'Content-Type': 'application/json', 'User-Agent': 'DiscordBot (pacetracker, 1.0)' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (res.status === 429 && tries < 3) {
      const j = await res.json().catch(() => ({}));
      await new Promise(r => setTimeout(r, Math.min(10000, (j.retry_after || 1) * 1000 + 100)));
      continue;
    }
    if (!res.ok) throw new Error(`${method} ${route.replace(/\/[^/]*$/, '/…')}: HTTP ${res.status}`);
    return res.status === 204 ? null : res.json().catch(() => null);
  }
}

// one-time setup on start: the pace roles, the split icons (as the app's own emojis) and the /pace-roles command
let ready = null;
function setup() {
  if (!BOT) return Promise.resolve();
  if (!ready) ready = (async () => {
    db.paceBot = db.paceBot || { roles: {}, emojis: {} };
    const pb = db.paceBot;
    const roles = await api('GET', `/guilds/${GUILD}/roles`);
    const byId = new Map(roles.map(r => [r.id, r]));
    for (const s of Object.keys(TIERS)) for (const m of TIERS[s]) {
      const k = `${s}:${m}`;
      const found = byId.get(pb.roles[k]) || roles.find(r => r.name === roleName(s, m));
      if (!found) pb.roles[k] = (await api('POST', `/guilds/${GUILD}/roles`, { name: roleName(s, m), mentionable: true, permissions: '0', color: GREY })).id;
      else {
        pb.roles[k] = found.id;
        if (found.color !== GREY) await api('PATCH', `/guilds/${GUILD}/roles/${found.id}`, { color: GREY });
      }
    }
    const have = ((await api('GET', `/applications/${APP_ID}/emojis`)) || {}).items || [];
    for (const s of SPLITS) {
      const name = `pt_${s}`;
      let e = have.find(x => x.name === name);
      if (!e) {
        const png = fs.readFileSync(path.join(__dirname, 'pace-icons', `${s}.png`));
        e = await api('POST', `/applications/${APP_ID}/emojis`, { name, image: `data:image/png;base64,${png.toString('base64')}` });
      }
      pb.emojis[s] = `<:${name}:${e.id}>`;
    }
    await api('PUT', `/applications/${APP_ID}/guilds/${GUILD}/commands`, [{
      name: 'pace-roles', type: 1,
      description: 'Post the pace role dropdowns in this channel',
      default_member_permissions: '268435456', // Manage Roles: only staff can post it
    }]);
    save();
    log('pace bot ready');
  })().catch(e => { ready = null; throw e; });
  return ready;
}

// posts one split; roles for every pace it beats get pinged
async function post(split, embed, igtMs) {
  if (BOT) {
    await setup();
    const pb = db.paceBot;
    // one ping: the fastest pace this split beats. Picking a pace gives you that role and every faster one, so
    // everyone whose pace was beaten has it.
    const tier = (TIERS[split] || []).find(m => igtMs <= m * 60000);
    const roles = tier && pb.roles[`${split}:${tier}`] ? [pb.roles[`${split}:${tier}`]] : [];
    if (pb.emojis[split]) embed.title = `${pb.emojis[split]} ${embed.title}`;
    await api('POST', `/channels/${CHANNEL}/messages`, {
      content: roles.length ? roles.map(r => `<@&${r}>`).join(' ') : undefined,
      embeds: [embed],
      allowed_mentions: { parse: [], roles },
    });
    return;
  }
  if (!WEBHOOK) return;
  const res = await fetch(WEBHOOK, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: NAME, embeds: [embed], allowed_mentions: { parse: [] } }),
  });
  if (!res.ok) throw new Error('webhook HTTP ' + res.status);
}

function verify(raw, sig, ts) {
  if (!PUBLIC_KEY || !/^[0-9a-f]{128}$/i.test(sig || '') || !/^\d{1,20}$/.test(ts || '')) return false;
  if (Math.abs(Date.now() / 1000 - Number(ts)) > 300) return false;
  try {
    const key = crypto.createPublicKey({ key: Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), Buffer.from(PUBLIC_KEY, 'hex')]), format: 'der', type: 'spki' });
    return crypto.verify(null, Buffer.concat([Buffer.from(ts), raw]), key, Buffer.from(sig, 'hex'));
  } catch (e) {
    return false;
  }
}

function pickerMessage() {
  const pb = db.paceBot || { emojis: {} };
  return {
    content: 'Select roles based on the splits and paces you wish to follow.',
    components: Object.keys(TIERS).map(s => ({
      type: 1,
      components: [{
        type: 3, custom_id: `pace:${s}`, placeholder: `Choose a${s === 'end' || s === 'nether' ? 'n' : ''} ${ROLE_NAMES[s]} Role...`,
        min_values: 1, max_values: 1,
        options: [{ label: 'No pings', value: 'none', description: `Stop ${ROLE_NAMES[s]} pings` }]
          .concat(TIERS[s].map(m => {
            const o = { label: roleName(s, m), value: String(m) };
            const e = /^<:(\w+):(\d+)>$/.exec(pb.emojis[s] || '');
            if (e) o.emoji = { name: e[1], id: e[2] };
            return o;
          })),
      }],
    })).concat([{ type: 1, components: [{ type: 2, style: 1, custom_id: 'pace:clear', label: 'Remove ALL pace roles' }] }]),
  };
}

// a dropdown choice: swap the member's role for that split, then answer privately
async function choose(i) {
  const [, s] = /^pace:(\w+)$/.exec(i.data.custom_id) || [];
  const clear = s === 'clear';
  const userId = i.member && i.member.user && i.member.user.id;
  const pick = (i.data.values || [])[0];
  const reply = (content) => api('PATCH', `/webhooks/${APP_ID}/${i.token}/messages/@original`, { content });
  try {
    if (!(TIERS[s] || clear) || !/^\d+$/.test(userId || '') || i.guild_id !== GUILD) return reply('This dropdown is not from this server.');
    await setup();
    const pb = db.paceBot;
    const mine = new Set(i.member.roles || []);
    if (clear) {
      for (const r of Object.values(pb.roles)) if (mine.has(r)) await api('DELETE', `/guilds/${GUILD}/members/${userId}/roles/${r}`);
      return reply("You won't get any pace pings now.");
    }
    // your pace and every faster one (a post pings only the fastest pace it beats)
    const want = pick === 'none' || !TIERS[s].includes(Number(pick)) ? null : Number(pick);
    for (const m of TIERS[s]) {
      const r = pb.roles[`${s}:${m}`];
      if (!r) continue;
      const keep = want !== null && m <= want;
      if (keep && !mine.has(r)) await api('PUT', `/guilds/${GUILD}/members/${userId}/roles/${r}`);
      if (!keep && mine.has(r)) await api('DELETE', `/guilds/${GUILD}/members/${userId}/roles/${r}`);
    }
    await reply(want ? `You'll be pinged for ${Number(pick) === ANY ? 'every ' + NAMES[s] : NAMES[s] + ' under ' + clock(Number(pick))}.` : `You won't be pinged for ${NAMES[s]} any more.`);
  } catch (e) {
    log('pace role:', e.message || e);
    await reply('Something went wrong, try again in a minute.').catch(() => {});
  }
}

// POST /api/discord: Discord sends every slash command and dropdown choice here, signed with the app's key
function interaction(raw, headers) {
  if (!BOT || !verify(raw, headers['x-signature-ed25519'], headers['x-signature-timestamp'])) return { status: 401, body: { error: 'bad signature' } };
  let i;
  try { i = JSON.parse(raw.toString('utf8')); } catch (e) { return { status: 400, body: { error: 'bad request' } }; }
  if (i.type === 1) return { status: 200, body: { type: 1 } }; // Discord checking the endpoint
  if (i.type === 2 && i.data && i.data.name === 'pace-roles') {
    if (i.guild_id !== GUILD) return { status: 200, body: { type: 4, data: { content: 'Only works in the Oldschool server.', flags: 64 } } };
    return { status: 200, body: { type: 4, data: pickerMessage() } };
  }
  if (i.type === 3 && i.data && /^pace:/.test(i.data.custom_id || '')) {
    setImmediate(() => choose(i));
    return { status: 200, body: { type: 5, data: { flags: 64 } } }; // "thinking…", then the answer above
  }
  return { status: 400, body: { error: 'unknown interaction' } };
}

function init(opts) {
  db = opts.db; save = opts.save; if (opts.log) log = opts.log;
  if (BOT) setup().catch(e => log('pace bot setup failed (will retry):', e.message || e));
}

module.exports = { init, post, interaction, enabled: () => BOT || !!WEBHOOK, SPLITS, NAMES, TIERS, verify };
