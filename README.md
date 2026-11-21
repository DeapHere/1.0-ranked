# Oldschool Ranked

1v1 ranked races for Minecraft 1.0. Two players are matched, get the same seed and start at the same moment. The first to kill the dragon and jump into the exit portal wins and gains elo. Both players' splits show live during the race.

Current version: **1.0.7**

## Installing

1. Put `oldschoolranked-1.0.7.jar` (from [Releases](https://github.com/DeapHere/1.0-ranked/releases)) in your 1.0 instance's `mods` folder. Needs Fabric Loader 0.18.1 or newer.
2. Launch the game and click the **blaze rod button** next to Singleplayer on the title screen.

The mod connects to the ranked server by itself. Your Minecraft username is your ranked name. On first connect, the server gives your PC a private sign-in token, so nobody else can play under your name. It's saved in `.oldschoolranked` in your user folder and shared by every instance on that PC. Don't share it.

Only the latest version can connect. Older versions are told to update.

## Lobby

- **Find ranked match:** joins the queue. Players are paired by closest elo, and the range widens the longer the wait.
- **Private room:** a solo run on a random seed, with the same countdown, timer and splits. No elo.
- **Create room / Join:** a 4-digit code to race a friend. Unranked.
- **Leaderboard:** three boards, switched with the button at the bottom:
  - **Elo:** top 100 by elo.
  - **Fastest:** each player's best ranked win by finishing the run. Forfeits and draws don't count.
  - **Private:** each player's best finished private room run.

## A match

1. **Load-in.** Both games build the world from the same seed (structures on, survival). The game stays frozen on a load-in screen showing both players and their elo. Once both have loaded, a 10 second countdown starts (5 in a private room). Both worlds start on the same tick. Players can chat here: type and press Enter.
2. **The race.** The timer, your height (Y) and the splits show in the top left. Your times are blue and your opponent's are yellow, and chat says when your opponent hits a split and who's ahead. F3 is off, and the seed is never shown or written to the game log.
3. **Splits:** Nether, Fortress (first step on nether brick), Blaze rod, First eye (first eye of ender thrown), Stronghold (first step on stone bricks, or near an end portal frame), End, Finish.
4. **The finish.** The run ends on jumping into the exit portal after the dragon dies. First one there wins. Both players then get a Victory or Defeat screen with how it ended, both elo changes and both sets of splits.

**Chat:** press T in game to talk to your opponent. Only the two players in the match see it.

### Pause menu in a match

- **Reset world:** restarts your own world on the same seed (click twice to confirm). The clock keeps running, splits already hit stay, and drops carry on from where they were, so resetting can't reroll luck.
- **Vote new seed:** asks your opponent to switch seeds. If they accept within 30 seconds, the match is called off with no elo change and a new one starts on a new seed. In a private room the new seed is instant.
- **Offer draw:** if your opponent accepts within 30 seconds, the match ends as a draw with no elo change.
- **Forfeit and quit** replaces Save and quit. Leaving the world, closing the game or losing connection for over a minute also counts as a forfeit.

### Elo

Everyone starts at 1000. A win or loss moves elo by up to 32 (48 for the first 5 games), weighted by the elo gap.

## Fair play

- The server's clock decides the result, not the game's. A finish is rejected if it's under 90 seconds, if the in-game timer is ahead of the server's clock, or if the player never entered the End.
- The ranked queue is blocked while OldschoolPrac is installed, since its /gamemode and /give commands would work in a match world. Rooms and private rooms still work with it, but private runs with it installed don't go on the Private leaderboard. Keep a separate instance for ranked, or move the OldschoolPrac jar out of `mods`.
- Pausing doesn't help: the timer is real time.

## Match world rules

These apply only in match worlds, and are the same for both players:

- **Pearls and blaze rods** use the normal odds, but from a sequence seeded by the match. Each player's 1st, 2nd, 3rd... enderman drops the same as the other player's, and the same goes for blazes.
- **The dragon's** choices (when to charge, where to fly from) come from a sequence seeded by the match.
- **Eyes of ender** always fly to the closest of the 3 strongholds. In vanilla 1.0 they go to the closest one already generated, which can be a far one.
- **No Peaceful.** Easy, Normal and Hard are allowed. A player on Peaceful is put on Easy.
- **No rain or thunder.** Vanilla 1.0 weather is random, not from the seed.
- **No caves under the desert.** Chunks with any desert have no caves, ravines or mineshafts, so a desert tower can dig straight down safely.
- **Minimum drops:** chickens drop at least 1 feather, skeletons at least 1 arrow, pigs at least 1 porkchop and spiders at least 1 string (1 or 2 instead of 0 to 2).
- **A lava pool on the surface** within 100 blocks of spawn, always in plains or desert.
- **A pack of 4 chickens** within 24 blocks of spawn.
- **Gravel in the iron cave:** the cave near spawn always has gravel on its floor next to the iron.
- **No strongholds under mountains.** Strongholds are never placed in or next to Extreme Hills, so you never have to dig down from y 100.
- **Ocean portal rooms are open to the sea.** If a portal room ends up under the ocean, its ceiling and the sea floor above it are water, so you can swim straight down to it.
- **Endermen after a night death reset:** 3 endermen spawn over the next few seconds, if you'd gone at least 150 blocks from your last spawn. Dying on the spot again brings none. They spawn on dark, open ground 24 to 87 blocks away, and vanilla spawning carries on as normal.

The mod also fixes the vanilla 1.0 bug where the same seed can put the spawn or stronghold in a different place depending on what was generated earlier in the session. Without it, two players on the same seed could get different worlds.

## Seeds

Every match seed comes from a checked pool, and no one gets a seed they've already played until they've played them all. Every seed has:

- A plains spawn, with desert making up at least 8% of the area within 160 blocks
- A desert reachable from spawn in a straight line over plains, within 160 blocks
- A fortress within 8 chunks of where a portal built near spawn comes out
- No more than 15% water within 24 blocks of spawn
- Trees within 64 blocks
- A cave open to the sky within 64 blocks, with at least 6 visible iron ore
- At least 4 food animals (pigs, cows, chickens) within 64 blocks, and 2 sheep within 200

## Changes

- **1.0.7:** Every match world gets a surface lava pool in plains or desert within 100 blocks, a pack of chickens near spawn and gravel in the iron cave. No strongholds under mountains. Portal rooms under the ocean are open to the sea. Older versions can no longer connect.
- **1.0.6:** Peaceful can't be used in matches. Older versions can no longer connect.
- **1.0.5:** No rain or thunder in match worlds.
- **1.0.4:** Private room leaderboard.
- **1.0.3:** Match chat on the load-in screen and in game. Load-in screen with both players and a 10 second countdown. Fastest times leaderboard. Eyes of ender always go to the closest stronghold.
- **1.0.2:** Reset world, Vote new seed and Offer draw in the pause menu.
- **1.0.1:** A night death reset only brings endermen after going 150+ blocks from the last spawn.
- **1.0.0:** First release.
