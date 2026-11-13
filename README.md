# Oldschool Ranked 1.0.0

1v1 ranked races for Minecraft 1.0. Two players queue, get the same seed, start at the same moment, and the first to kill the dragon and jump into the exit portal wins and takes elo. You can see your opponent's splits live as they happen.

## Installing the mod

1. Drop `oldschoolranked-1.0.0.jar` into your 1.0 instance's `mods` folder (Fabric Loader 0.18.1 or newer, the same setup as OldschoolPrac).
2. On the title screen, click the **blaze rod button** to the right of Singleplayer.
3. That's it: the mod connects to the ranked server on its own.

Your Minecraft username is your ranked name. The first time you connect, the server gives your PC a private sign-in token (saved in `config/oldschoolranked.properties`), so nobody else can play under your name. Don't share that file.

## Playing

- **Find ranked match** puts you in the queue. The server pairs you with the closest elo it can find. The range widens the longer you wait.
- **Private room (solo run)** starts a match on your own, on a random seed, with the same countdown, timer and splits. It's handy for testing, or for practising full runs. No elo.
- **Create room** gives you a 4-digit code. Your friend types it next to **Join**. Room matches are unranked, so they're good for testing and for racing friends.

When a match is found:

1. Both games build the world from the same seed (structures on, survival). Seeds come from a filtered pool (see Seeds below), and you never get a seed you've already played until you've played them all.
2. The game is frozen behind a waiting screen until both of you have loaded in, then there's a 5 second countdown. Since the world doesn't tick while you wait, both worlds start on the same tick.
3. The timer, your height (Y) and the splits show in the top left. F3 is turned off in match worlds, and the seed is never shown or written to the game log. The splits are: Nether, Fortress (first step on nether brick), Blaze rod, First eye (first eye of ender thrown), Stronghold (first step on stone bricks, or near an end portal frame), End, and Finish. Your times are in blue and your opponent's in yellow. Chat tells you when your opponent hits a split and whether you're ahead.
4. The run ends when you jump into the exit portal after killing the dragon (the moment the credits would start), and the first one there wins. A Victory or Defeat screen pops up for both players with how it ended, both players' elo changes and both sets of splits. **Back to lobby** leaves the world, and **Keep looking around** closes it. In a match, the pause menu's **Save and quit** becomes **Forfeit and quit**. Leaving the world, closing the game, or losing connection for more than a minute also counts as a forfeit.

Elo starts at 1000. Each win or loss moves it by up to 32 (48 for your first 5 games), weighted by the elo gap.

### Fair play

- The server's clock decides who won, not the game's. A finish is rejected if it's faster than 90 seconds, if the in-game timer is ahead of the server's clock, or if you never entered the End.
- Both mods can be installed together. Ranked queue is blocked while OldschoolPrac is installed, though, since its /gamemode and /give commands would work in a ranked world. Rooms work with it. To play ranked, move the OldschoolPrac jar out of `mods` (or keep a separate instance for ranked).
- Pausing doesn't help: it's real-time, so a pause only loses you time.

### Match world rules

These apply only in match worlds, the same for both players:

- **Pearls and blaze rods** use the normal odds (0 or 1 each), but from a sequence seeded by the match. Your 1st, 2nd, 3rd... enderman drops exactly what your opponent's 1st, 2nd, 3rd... drops, and the same goes for blazes. Luck can't decide a match.
- **Chickens** always drop at least 1 feather (1 or 2 instead of vanilla's 0 to 2).
- **Skeletons** always drop at least 1 arrow, **pigs** at least 1 porkchop and **spiders** at least 1 string (1 or 2 instead of 0 to 2). Bones and spider eyes are unchanged.
- **The dragon** decides when to charge at you and where to fly from a sequence seeded by the match, so its 1st, 2nd, 3rd... choice is the same for both players.
- **No caves under the desert:** chunks with any desert in them have no caves, ravines or mineshafts, so you can dig straight down from a desert tower without falling into one.
- **Endermen at night:** each death reset at night guarantees 3 endermen over the next few seconds. They spawn on dark, open ground 24 to 87 blocks away, passing the game's own spawn checks, so they look like natural spawns. Vanilla spawning carries on as normal on top of that, so you'll often get more.

The mod also fixes the vanilla 1.0 bug where the same seed can put the spawn or the stronghold in a different place depending on what was generated earlier in that session. Without that fix, two players on the same seed could get different worlds.

## Seeds

Every match seed comes from a pool of seeds that passed the seed finder:

- Plains spawn, with desert making up at least 8% of the area within 160 blocks
- A desert you can walk to from spawn in a straight line over plains (no forest in the way), within 160 blocks
- A fortress starting within 8 chunks of where a portal built near spawn comes out (spawn / 8), from 1.0's own fortress placement maths
- No more than 15% water within 24 blocks of spawn
- A real surface lava pool (at least 6 lava blocks showing) within 128 blocks
- Trees within 64 blocks
- A cave opening (open to the sky) within 64 blocks, with at least 6 iron ore you can see from inside the cave (seeds added from 1.0.0 on; older pool seeds only needed a cave within 128)
- At least 4 food animals (pigs, cows, chickens) within 64 blocks and 2 sheep within 200

The finder builds worlds the way match worlds are built (no caves in desert chunks), so what it checks is what you play.

### Finding more seeds on your PC

`oldschoolranked-seedfinder-1.0.0.jar` is the seed finder as a mod. Use it in a **spare** 1.0 instance only, because it takes over the game:

1. Make a copy of your 1.0 instance (in Prism: right-click, then Copy). Name it something like "Seed finder".
2. In that copy's `mods` folder, keep `oldschoolranked-1.0.0.jar`, add `oldschoolranked-seedfinder-1.0.0.jar`, and remove every other mod (except Queue Wall, see below).
3. Launch it. It immediately starts checking seeds. The window title shows how many it has checked and how many were good. Leave it running and minimise it.
4. Good seeds are saved to `ranked-seeds.txt` in the instance's `.minecraft` folder, one per line with notes on what was found.
5. Send the good lines to DeapHere to get them added to the ranked seed pool.

**Use every core:** Minecraft builds worlds on one core, so on its own the finder uses about one core's worth of CPU. Put `queuewall-0.6.1.jar` in the finder instance's `mods` folder too, and the finder checks several worlds at once (one per core, leaving 2 free) using Queue Wall's thread-safe world generation. The window title shows how many threads it's using and how many worlds a minute it checks. Don't open the wall in that instance. Give the instance at least 3 GB of memory (each thread needs about 250 MB). `threads` in the settings file sets the number of threads yourself.

Without Queue Wall, running two or three copies at once does the same job. Close the window to stop it.

The rules are in `config/ranked-seedfinder.properties` (created on first launch, with a note on each setting). Change a value, save, and restart the finder. `caveRadius=0` turns the cave rule off, and `caveIron` sets how much iron the cave needs.

## Changes

- 1.0.0: New splits for Fortress, First eye and entering the Stronghold. The run now ends when you jump into the exit portal, not on the dragon kill. Victory and Defeat screens show both players' elo changes. Endermen: 3 guaranteed per night death reset (no all-night minimum any more). Leaderboard button in the Ranked lobby (top 100 by elo). Your sign-in is shared by every instance on your PC for the same name (saved in `.oldschoolranked` in your user folder), so a second instance no longer says your name is registered on another PC. F3 is off in matches, with a Y readout instead, and the seed is never shown (not even on the loading screen) or logged. The GO message shows your opponent's elo. The dragon's choices are standardised, spiders always drop string, and desert chunks have no caves, ravines or mineshafts. Seed finder: several worlds at once with Queue Wall, the whole checked area is generated properly, and new rules for caves (within 64 blocks, with at least 6 iron showing), an open path to the desert and real lava pools.
- 0.2.1: skeletons always drop at least 1 arrow and pigs at least 1 porkchop in match worlds. Same server as 0.2.0, so only the mod jar changes.
- 0.2.0: filtered seed pool (plains + desert, fortress, lava, animals) and no repeat seeds; shared pearl and blaze rod drop sequences; at least 1 feather per chicken; guaranteed endermen on night death resets; seed finder mod. Default server is now ranked-1-0.fly.dev. Needs the 0.2.0 server.
- 0.1.3: timer and splits moved to the top left, so achievement popups don't cover them. Same server as 0.1.2.
- 0.1.2: Private room (solo runs). The room code now shows when OldschoolPrac is installed (the OldschoolPrac message used to cover it). Defaults to ranked-1-0.fly.dev. Needs the 0.1.2 server for Private room.
- 0.1.1: works alongside OldschoolPrac (both mods fixed the same 1.0 world generation bug in a way that could clash on startup).

## Not in 1.0.0 yet

- Replays or run verification beyond the time checks
- Seasons and rank tiers
- Spectating
