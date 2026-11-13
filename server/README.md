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

## Finding more seeds

`oldschoolranked-seedfinder-1.0.0.jar` is the seed finder as a mod. Use it in a **spare** 1.0 instance only, because it takes over the game:

1. Make a copy of your 1.0 instance (in Prism: right-click, then Copy). Name it something like "Seed finder".
2. In that copy's `mods` folder, keep `oldschoolranked-1.0.0.jar`, add `oldschoolranked-seedfinder-1.0.0.jar`, and remove every other mod (except Queue Wall, see below).
3. Launch it. It immediately starts checking seeds. The window title shows how many it has checked and how many were good. Leave it running and minimise it.
4. Good seeds are saved to `ranked-seeds.txt` in the instance's `.minecraft` folder, one per line with notes on what was found.
5. Add the lines to `seeds.txt` here and run `fly deploy`. Duplicates and notes are fine.

**Use every core:** Minecraft builds worlds on one core, so on its own the finder uses about one core's worth of CPU. Put `queuewall-0.6.1.jar` in the finder instance's `mods` folder too, and the finder checks several worlds at once (one per core, leaving 2 free) using Queue Wall's thread-safe world generation. The window title shows how many threads it's using and how many worlds a minute it checks. Don't open the wall in that instance. Give the instance at least 3 GB of memory (each thread needs about 250 MB). `threads` in the settings file sets the number of threads yourself.

Without Queue Wall, running two or three copies at once does the same job. Close the window to stop it.

The rules are in `config/ranked-seedfinder.properties` (created on first launch, with a note on each setting). Change a value, save, and restart the finder. `caveRadius=0` turns the cave rule off, and `caveIron` sets how much iron the cave needs.

The finder builds worlds the way match worlds are built (no caves in desert chunks), so what it checks is what players get.

