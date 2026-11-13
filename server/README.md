# Hosting the Oldschool Ranked server

Notes for whoever runs the match server. Players don't need any of this: the mod connects to `https://ranked-1-0.fly.dev` on its own.

## Updating the server

Extract the new server zip over the old folder, then run `fly deploy` in the `server` folder. Players, elo, match history and matches in progress are kept, since they live on the volume. A match in progress just pauses for the few seconds the server is down.

## Hosting the server on Fly.io

The server is a single Node file with no dependencies. It keeps players, elo and match history in a small JSON file on a volume.

1. Install the Fly CLI (https://fly.io/docs/flyctl/install/) and run `fly auth login`.
2. In the `server` folder, edit `fly.toml` and change `app = "ranked-1-0"` to a unique name if you're hosting your own. Your address will be `https://<that name>.fly.dev`.
3. Run:
   ```
   fly launch --copy-config --no-deploy
   fly volumes create ranked_data --size 1
   fly deploy
   ```
4. Open `https://<name>.fly.dev` in a browser. You should see the leaderboard page.
5. Put the same address in `server=` in each player's `config/oldschoolranked.properties` (with the game closed), or build the mod with it as the default.

It runs on Fly's smallest machine (shared CPU, 256 MB). That costs about $2 a month plus a few cents for the 1 GB volume. The config keeps the machine always on and to a single machine (`fly status` should list exactly 1), since matches in progress are handled by that one server.

Settings, as environment variables (`fly secrets set NAME=value` or under `[env]` in `fly.toml`):

| Variable | Default | What it does |
|---|---|---|
| `MIN_RUN_MS` | `90000` | Finishes faster than this (in ms) are rejected |
| `K_FACTOR` | `32` | How much elo a game can move |
| `BLOCKED_MODS` | `endpractice` | Mod ids that can't queue ranked (comma separated) |
| `MOTD` | (none) | A line shown at the bottom of the lobby |

Matches use the seeds in `seeds.txt` (one per line, anything after `#` is ignored). Without that file, every match gets a random seed.

### Running it on your own PC instead

With Node 18 or newer: `node server.js` in the `server` folder. It listens on port 8080, so the mod's server address is `http://localhost:8080`, or `http://<your LAN IP>:8080` for others on your network. `node test.js` runs the server's tests.

## The server API

For a leaderboard site or bots later on: `GET /api/leaderboard` (top 100 and how many are online), `GET /api/matches` (last 50 matches with splits), and `GET /` (a simple leaderboard page).

## Adding seeds

Add lines from a seed finder's `ranked-seeds.txt` to `seeds.txt` and run `fly deploy`. Duplicates and notes are fine.
