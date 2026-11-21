package oldschoolranked;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.entity.mob.monster.boss.EnderDragonEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings;
import oldschoolranked.mixin.IntCacheAccessor;

/**
 * The ranked client: signs in to the match server, and runs a match from the server's "match" event (build the world
 * from the shared seed, wait frozen until both players have loaded, start together) to the dragon dying.
 */
public final class Ranked {
    public static final String VERSION = FabricLoader.getInstance().getModContainer("oldschoolranked")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    public static final String FOLDER = "ranked-match";
    /** Splits in race order, as the server names them, and how they read on screen. */
    public static final String[] SPLITS = {"nether", "fortress", "blaze", "eye", "stronghold", "end", "finish"};
    public static final String[] SPLIT_NAMES = {"Nether", "Fortress", "Blaze rod", "First eye", "Stronghold", "End", "Finish"};

    public enum State { IDLE, QUEUED, ROOM, LOADING, COUNTDOWN, RUNNING, LEAVING, DONE }

    public static final class Config {
        public String server = "https://ranked-1-0.fly.dev";
        public final Properties tokens = new Properties();
    }

    public static Config cfg;
    public static State state = State.IDLE;
    public static Minecraft mc;

    // signed-in player, from the hello reply
    public static String name;
    public static long elo, wins, losses, rank;
    public static boolean blocked;
    public static String signInError;
    public static String motd = "";

    // lobby
    public static String roomCode;
    public static long queuedSince;
    public static String lastError;

    // current or last match
    public static String matchId;
    /** The last few match chat lines, for the load-in screen. */
    public static final java.util.List<String> matchChat = new java.util.ArrayList<>();
    public static long seed;
    public static boolean rankedMatch;
    public static String opponent;
    public static long opponentElo;
    /** A private room: racing the clock alone. */
    public static boolean solo;
    public static boolean opponentReady;
    public static long startAtLocal;
    public static long ticks;
    public static final Map<String, Long> mySplits = new LinkedHashMap<>();
    /** The last match result, for the result screen. */
    public static boolean lastWon, lastDraw;
    public static String lastReason = "";
    public static long lastChange, lastOppElo, lastOppChange;
    /** Draw offers in this match, as local clock times they run out (0 = none): theirs to us, and ours to them. */
    public static long theirDrawUntil, myDrawUntil;
    /** The same for a new seed vote. */
    public static long theirSeedUntil, mySeedUntil;
    public static final Map<String, Long> oppSplits = new LinkedHashMap<>();
    public static final List<String> resultLines = new ArrayList<>();
    private static boolean worldPending;
    private static boolean finishSent;

    private Ranked() {
    }

    public static void log(String s) {
        System.out.println("[OldschoolRanked] " + s);
    }

    // ---------- config ----------

    private static File configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("oldschoolranked.properties").toFile();
    }

    /**
     * Sign-in tokens are also kept here, outside every instance, one per server and player name, so all of this PC's
     * instances (ranked, practice, a copy for the seed finder...) sign in as the same player. Before 1.0.0 they were only
     * in the instance's config.
     */
    private static File sharedTokensFile() {
        return new File(System.getProperty("user.home"), ".oldschoolranked" + File.separator + "tokens.properties");
    }

    private static Properties readSharedTokens() {
        Properties t = new Properties();
        File f = sharedTokensFile();
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                t.load(in);
            } catch (Exception e) {
                log("couldn't read shared tokens: " + e);
            }
        }
        return t;
    }

    private static String sharedKey(String player) {
        return serverKey() + "." + player.toLowerCase(Locale.ROOT);
    }

    private static void saveSharedToken(String player, String token) {
        Properties t = readSharedTokens();
        if (token.equals(t.getProperty(sharedKey(player)))) return;
        t.setProperty(sharedKey(player), token);
        File f = sharedTokensFile();
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            t.store(out, "Oldschool Ranked sign-in tokens for every instance on this PC. Keep this file private.");
        } catch (Exception e) {
            log("couldn't save shared tokens: " + e);
        }
    }

    public static void init() {
        if (cfg != null) return;
        cfg = new Config();
        File f = configFile();
        if (f.exists()) {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
                cfg.server = p.getProperty("server", cfg.server).trim();
                // 0.1.0/0.1.1 default, which was never a real server
                if (cfg.server.equals("https://oldschool-ranked.fly.dev")) cfg.server = new Config().server;
                for (String k : p.stringPropertyNames()) {
                    if (k.startsWith("token.")) cfg.tokens.setProperty(k.substring(6), p.getProperty(k));
                }
            } catch (Exception e) {
                log("couldn't read config: " + e);
            }
        }
        saveConfig();
    }

    public static void saveConfig() {
        Properties p = new Properties();
        p.setProperty("server", cfg.server);
        for (String k : cfg.tokens.stringPropertyNames()) p.setProperty("token." + k, cfg.tokens.getProperty(k));
        File f = configFile();
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            p.store(out, "Oldschool Ranked. server: the match server address. token.*: your sign-in for each server, keep it private.");
        } catch (Exception e) {
            log("couldn't save config: " + e);
        }
    }

    /** The token is per server, so switching servers never sends one server's token to another. */
    private static String serverKey() {
        return cfg.server.replaceAll("^https?://", "").replaceAll("/+$", "").toLowerCase(Locale.ROOT).replace(':', '_');
    }

    public static void setServer(String url) {
        url = url.trim();
        if (url.isEmpty() || url.equals(cfg.server)) return;
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        cfg.server = url;
        saveConfig();
        Net.token = null;
        name = null;
        signInError = null;
        signIn();
    }

    // ---------- signing in ----------

    private static boolean signingIn;

    public static void signIn() {
        if (signingIn || mc == null) return;
        signingIn = true;
        signInError = null;
        List<Object> mods = new ArrayList<>();
        for (ModContainer m : FabricLoader.getInstance().getAllMods()) mods.add(m.getMetadata().getId());
        // this PC's token for this name first (from any instance), then this instance's own
        String saved = readSharedTokens().getProperty(sharedKey(mc.session.username), cfg.tokens.getProperty(serverKey()));
        Net.post("/api/hello", Net.map("protocol", Net.PROTOCOL, "name", mc.session.username, "token", saved, "mods", mods), (r, err) -> {
            signingIn = false;
            if (err != null) {
                signInError = err;
                return;
            }
            String token = Json.str(r, "token", null);
            cfg.tokens.setProperty(serverKey(), token);
            saveConfig();
            saveSharedToken(mc.session.username, token);
            Net.token = token;
            name = Json.str(r, "name", mc.session.username);
            readStats(r);
            rank = Json.num(r, "rank", 0);
            Object b = r.get("blocked");
            blocked = b instanceof List && !((List<?>) b).isEmpty();
            motd = Json.str(r, "motd", "");
            Net.startPolling(Json.num(r, "seq", 0));
        });
    }

    private static void readStats(Map<String, Object> r) {
        elo = Json.num(r, "elo", elo);
        wins = Json.num(r, "wins", wins);
        losses = Json.num(r, "losses", losses);
    }

    public static boolean signedIn() {
        return Net.token != null && name != null;
    }

    // ---------- lobby actions ----------

    public static void queue() {
        lastError = null;
        Net.post("/api/queue", null, (r, err) -> {
            if (err != null) {
                lastError = err;
                return;
            }
            if (state == State.IDLE || state == State.DONE || state == State.ROOM) {
                state = State.QUEUED;
                queuedSince = System.currentTimeMillis();
                roomCode = null;
            }
        });
    }

    public static void cancel() {
        Net.post("/api/unqueue", null, (r, err) -> {
            if (state == State.QUEUED || state == State.ROOM) state = State.IDLE;
            roomCode = null;
        });
    }

    public static void createRoom() {
        lastError = null;
        Net.post("/api/room/create", null, (r, err) -> {
            if (err != null) {
                lastError = err;
                return;
            }
            roomCode = Json.str(r, "code", "?");
            state = State.ROOM;
        });
    }

    /** A solo match on a random seed: same load, countdown, timer and splits, no opponent, no elo. */
    public static void privateRoom() {
        lastError = null;
        Net.post("/api/room/private", null, (r, err) -> {
            if (err != null) lastError = err;
        });
    }

    public static void joinRoom(String code) {
        lastError = null;
        Net.post("/api/room/join", Net.map("code", code.trim()), (r, err) -> {
            if (err != null) lastError = err;
        });
    }

    /** True while the loaded world is a match world (racing, or finished but not left yet). */
    public static boolean inMatchWorld() {
        return state == State.COUNTDOWN || state == State.RUNNING || state == State.DONE && matchWorldLoaded;
    }

    private static boolean matchWorldLoaded;

    public static boolean inMatch() {
        return state == State.LOADING || state == State.COUNTDOWN || state == State.RUNNING;
    }

    /** Pause menu: offer a draw, or accept the one on the table. */
    public static void draw() {
        if (state != State.RUNNING || solo || matchId == null) return;
        final boolean accepting = drawPending();
        Net.post("/api/draw", Net.map("match", matchId), (r, err) -> {
            if (err != null) {
                chat("§c" + err);
            } else if (r != null && Json.bool(r, "pending") && !accepting) {
                myDrawUntil = Json.num(r, "expires", 0) - Net.clockOffset;
                chat("§7You offered a draw. " + opponent + " has 30s to accept.");
            }
        });
    }

    /** Pause menu: ask for a new seed, or accept their ask. A private room gets one at once. */
    /** Match chat works from the load-in screen until the race is over, in two-player matches. */
    public static boolean canChat() {
        return matchId != null && !solo && (state == State.LOADING || state == State.COUNTDOWN || state == State.RUNNING);
    }

    public static void sendChat(String text) {
        if (!canChat() || text == null || text.trim().isEmpty()) return;
        Net.post("/api/chat", Net.map("match", matchId, "text", text.trim()), (r, err) -> {
            if (err != null) chat("§c" + err);
        });
    }

    public static void newSeed() {
        if (state != State.RUNNING || matchId == null) return;
        final boolean accepting = seedPending();
        Net.post("/api/newseed", Net.map("match", matchId), (r, err) -> {
            if (err != null) {
                chat("§c" + err);
            } else if (r != null && Json.bool(r, "pending") && !accepting) {
                mySeedUntil = Json.num(r, "expires", 0) - Net.clockOffset;
                chat("§7You asked for a new seed. " + opponent + " has 30s to accept.");
            }
        });
    }

    /** They've asked for a new seed and it hasn't run out. */
    public static boolean seedPending() {
        return theirSeedUntil > System.currentTimeMillis();
    }

    /** They've offered a draw and it hasn't run out. */
    public static boolean drawPending() {
        return theirDrawUntil > System.currentTimeMillis();
    }

    /** Pause menu: start your own world again on the same seed. The clock keeps running and splits you've hit stay. */
    public static void resetWorld() {
        if (state != State.RUNNING) return;
        try {
            mc.setWorld(null);
            mc.getWorldStorageSource().delete(FOLDER);
            mc.interactionManager = new net.minecraft.client.SurvivalInteractionManager(mc);
            resetBiomeArrayPool();
            Standard.noDesertCaves = true;
            Standard.worldReset();
            mc.startGame(FOLDER, "Ranked match", newWorldSettings(seed));
            MatchSetup.apply(mc.world, seed);
            mc.openScreen(null);
            lastDim = 0;
            chat("§7World reset. The clock is still running.");
        } catch (RuntimeException e) {
            log("couldn't reset the world: " + e);
            chat("§cCouldn't reset the world: " + e);
            forfeit();
        }
    }

    /** Pause menu "Forfeit and quit", or leaving the world any other way. */
    public static void forfeit() {
        if (!inMatch() || matchId == null) return;
        state = State.LEAVING;
        Net.post("/api/forfeit", Net.map("match", matchId), (r, err) -> {
            if (err != null && state == State.LEAVING) state = State.IDLE;
        });
    }

    // ---------- server events (game thread) ----------

    public static void onPollState(Map<String, Object> s) {
        if (state == State.QUEUED && !Json.bool(s, "queued") && s.get("match") == null) state = State.IDLE;
        // the server lost the match (restarted): don't keep playing a race nobody is tracking
        if ((state == State.LOADING || state == State.COUNTDOWN || state == State.RUNNING) && s.get("match") == null && matchId != null) {
            chat("§cThe server ended this match without a result (it may have restarted).");
            state = State.DONE;
            matchId = null;
        }
    }

    public static void onEvent(Map<String, Object> ev) {
        String type = Json.str(ev, "type", "");
        switch (type) {
            case "match":
                matchId = Json.str(ev, "match", null);
                seed = Long.parseLong(Json.str(ev, "seed", "0"));
                rankedMatch = Json.bool(ev, "ranked");
                solo = Json.bool(ev, "solo");
                opponent = solo ? null : Json.str(ev, "opponent", "?");
                opponentElo = Json.num(ev, "opponentElo", 0);
                opponentReady = false;
                mySplits.clear();
                oppSplits.clear();
                resultLines.clear();
                finishSent = false;
                lastDim = 0;
                dragonDead = false;
                theirDrawUntil = 0;
                myDrawUntil = 0;
                theirSeedUntil = 0;
                mySeedUntil = 0;
                matchChat.clear();
                ticks = 0;
                roomCode = null;
                state = State.LOADING;
                worldPending = true;
                log("match " + matchId + " vs " + opponent); // never the seed: the game log is visible to the player
                break;
            case "chat":
                if (Json.str(ev, "match", "").equals(matchId)) {
                    String from = Json.str(ev, "from", "?");
                    boolean me = name != null && name.equalsIgnoreCase(from);
                    String line = (me ? "§b<" : "§e<") + from + ">§r " + Json.str(ev, "text", "");
                    matchChat.add(line);
                    if (matchChat.size() > 8) matchChat.remove(0);
                    if (mc != null && mc.screen instanceof WaitingScreen) log("chat: " + line.replaceAll("§.", "")); // the load-in screen shows it
                    else chat(line);
                }
                break;
            case "drawOffer":
                if (Json.str(ev, "match", "").equals(matchId)) {
                    theirDrawUntil = Json.num(ev, "expires", 0) - Net.clockOffset;
                    chat("§e" + opponent + "§r offers a draw. Esc, then §aAccept draw§r (30s).");
                }
                break;
            case "drawExpired":
                if (Json.str(ev, "match", "").equals(matchId) && (theirDrawUntil != 0 || myDrawUntil != 0)) {
                    theirDrawUntil = 0;
                    myDrawUntil = 0;
                    chat("§7The draw offer ran out.");
                }
                break;
            case "seedOffer":
                if (Json.str(ev, "match", "").equals(matchId)) {
                    theirSeedUntil = Json.num(ev, "expires", 0) - Net.clockOffset;
                    chat("§e" + opponent + "§r wants a new seed. Esc, then §aAccept seed§r (30s).");
                }
                break;
            case "seedExpired":
                if (Json.str(ev, "match", "").equals(matchId) && (theirSeedUntil != 0 || mySeedUntil != 0)) {
                    theirSeedUntil = 0;
                    mySeedUntil = 0;
                    chat("§7The new seed vote ran out.");
                }
                break;
            case "opponentReady":
                opponentReady = true;
                break;
            case "start":
                if (!Json.str(ev, "match", "").equals(matchId)) return;
                long offset = Json.num(ev, "serverTime", Net.serverNow()) - System.currentTimeMillis();
                startAtLocal = Json.num(ev, "startAt", 0) - offset;
                opponentReady = true;
                state = State.COUNTDOWN;
                break;
            case "opponentSplit": {
                if (!Json.str(ev, "match", "").equals(matchId)) return;
                String split = Json.str(ev, "split", "");
                long t = Json.num(ev, "time", 0);
                oppSplits.put(split, t);
                chat("§e" + opponent + "§r: " + splitName(split) + " at " + time(t) + compare(split));
                break;
            }
            case "result":
                onResult(ev);
                break;
            default:
                break;
        }
    }

    private static String compare(String split) {
        Long mine = mySplits.get(split);
        if (mine == null) return "";
        long d = oppSplits.get(split) - mine;
        return " §7(you were " + time(Math.abs(d)) + (d >= 0 ? " ahead)" : " behind)");
    }

    private static void onResult(Map<String, Object> ev) {
        if (!Json.str(ev, "match", "").equals(matchId)) return;
        boolean won = Json.bool(ev, "won"), draw = Json.bool(ev, "draw");
        long change = Json.num(ev, "eloChange", 0);
        String reason = Json.str(ev, "reason", "");
        long t = Json.num(ev, "time", -1);
        elo = Json.num(ev, "elo", elo);
        lastWon = won;
        lastDraw = draw;
        lastReason = reason;
        lastChange = change;
        lastOppElo = Json.num(ev, "opponentElo", 0);
        lastOppChange = Json.num(ev, "opponentEloChange", 0);
        if (rankedMatch && !draw) {
            if (won) wins++;
            else losses++;
        }
        resultLines.clear();
        String head = reason.equals("draw") ? "§eDraw" : draw ? "§7No result" : won ? "§aYou won!" : "§cYou lost";
        if (solo) {
            resultLines.add(reason.equals("finish") ? "§aFinished in " + time(t) : "§7Private run ended (" + reason + ")");
        } else {
            resultLines.add(head + "§r vs " + opponent + " (" + reasonText(reason, won) + (t >= 0 ? ", " + time(t) : "") + ")");
        }
        if (rankedMatch) resultLines.add("Elo " + elo + " (" + (change >= 0 ? "+" : "") + change + ")");
        if (reason.equals("new seed")) {
            // called off by vote: the next match (new seed) follows straight away
            resultLines.clear();
            chat("§7New seed agreed, no elo either way. Loading the new world...");
        } else {
            for (String l : resultLines) chat(l);
            if (mc != null && mc.world != null) chat("§7Esc, then Save and quit, to go back to the lobby.");
        }
        state = State.DONE;
        matchId = null;
        if (mc != null && mc.world != null && !reason.equals("new seed")) mc.openScreen(new ResultScreen());
    }

    private static String reasonText(String reason, boolean won) {
        switch (reason) {
            case "finish": return won ? "finished first" : "they finished first";
            case "draw": return "draw agreed";
            case "new seed": return "new seed";
            case "forfeit": return won ? "they forfeited" : "forfeited";
            case "opponent disconnected": return won ? "they disconnected" : "you disconnected";
            case "opponent did not load": return won ? "they never loaded in" : "you didn't load in time";
            default: return reason;
        }
    }

    // ---------- client tick ----------

    /** Start of every client tick (the game thread), on the title screen too. */
    public static void tick(Minecraft client) {
        mc = client;
        init();
        // no Peaceful in a match (it would wipe every hostile mob, endermen included): runs before the game copies the
        // option into the world this tick, so the world never sees Peaceful
        if (inMatch() && mc.options != null && mc.options.difficulty == 0) mc.options.difficulty = 1;
        Runnable r;
        while ((r = Net.inbox.poll()) != null) {
            try {
                r.run();
            } catch (RuntimeException e) {
                log("event failed: " + e);
            }
        }
        if (worldPending) {
            worldPending = false;
            createWorld();
            return;
        }
        switch (state) {
            case COUNTDOWN:
                if (mc.world != null && System.currentTimeMillis() >= startAtLocal) go();
                break;
            case RUNNING:
                if (mc.world == null) {
                    forfeit();
                    break;
                }
                if (!mc.paused) {
                    ticks++;
                    Standard.tick(mc);
                }
                checkSplits();
                break;
            case LEAVING:
            case DONE:
                if (mc.world == null) {
                    matchWorldLoaded = false;
                    Standard.noDesertCaves = false;
                }
                if (mc.world == null && state == State.DONE) state = State.IDLE;
                break;
            default:
                break;
        }
    }

    private static void createWorld() {
        try {
            mc.setWorld(null);
            mc.getWorldStorageSource().delete(FOLDER);
            mc.interactionManager = new net.minecraft.client.SurvivalInteractionManager(mc);
            resetBiomeArrayPool();
            Standard.noDesertCaves = true; // from the very first chunk
            mc.startGame(FOLDER, "Ranked match", newWorldSettings(seed));
            MatchSetup.apply(mc.world, seed);
        } catch (RuntimeException e) {
            Standard.noDesertCaves = false;
            log("couldn't create the match world: " + e);
            chat("§cCouldn't create the match world: " + e);
            forfeit();
            return;
        }
        matchWorldLoaded = true;
        Standard.reset(seed);
        mc.openScreen(new WaitingScreen());
        Net.post("/api/ready", Net.map("match", matchId), (r, err) -> {
            if (err != null) chat("§c" + err);
        });
    }

    private static WorldSettings newWorldSettings(long seed) {
        // 1.0: seed, survival, structures, not hardcore
        return new WorldSettings(seed, 0, true, false);
    }

    /**
     * Vanilla 1.0 bug: BiomeSource.findBiome (spawn point and strongholds) reads leftovers from IntCache's pooled
     * arrays, so the same seed can give a different spawn or stronghold depending on what was generated before in that
     * session. Every match world starts from an empty pool, so both players get exactly the same world.
     */
    public static void resetBiomeArrayPool() {
        IntCacheAccessor.ranked$setMaxSize(256);
        IntCacheAccessor.ranked$setTcache(new ArrayList<int[]>());
        IntCacheAccessor.ranked$setTallocated(new ArrayList<int[]>());
        IntCacheAccessor.ranked$setCache(new ArrayList<int[]>());
        IntCacheAccessor.ranked$setAllocated(new ArrayList<int[]>());
    }

    private static void go() {
        state = State.RUNNING;
        ticks = 0;
        if (mc.screen instanceof WaitingScreen) mc.openScreen(null);
        if (solo) chat("§a§lGO!§r Private room: just you and the clock.");
        else chat("§a§lGO!§r " + (rankedMatch ? "Ranked" : "Room") + " match vs §e" + opponent + "§r" + (rankedMatch ? " (" + opponentElo + ")" : ""));
    }

    public static long elapsed() {
        if (state == State.COUNTDOWN || startAtLocal == 0) return 0;
        return Math.max(0, System.currentTimeMillis() - startAtLocal);
    }

    private static void checkSplits() {
        if (mc.player == null || mc.world == null) return;
        int dim = mc.world.dimension.id;
        if (dim == -1) split("nether");
        int under = ticks % 2 == 0 ? blockUnder() : 0;
        // a fortress: the first time they stand on nether brick
        if (dim == -1 && (under == Block.NETHER_BRICKS.id || under == Block.NETHER_BRICK_FENCE.id || under == Block.NETHER_BRICK_STAIRS.id)) split("fortress");
        if (!mySplits.containsKey("blaze") && ticks % 10 == 0 && hasBlazeRod()) split("blaze");
        if (dim == 0 && !mySplits.containsKey("eye") && ticks % 2 == 0 && eyeThrown()) split("eye");
        // the stronghold: the first time they stand on stone bricks (only strongholds have them in 1.0), or near the portal
        if (dim == 0 && (under == Block.STONE_BRICKS.id || under == Block.STONE_BRICK_STAIRS.id)) split("stronghold");
        if (dim == 0 && !mySplits.containsKey("stronghold") && ticks % 10 == 0 && nearPortalFrame()) split("stronghold");
        if (dim == 1) {
            split("stronghold");
            split("end");
            // the run ends when they jump into the exit portal: 1.0 opens the credits right then
            if (!finishSent && mc.screen instanceof net.minecraft.client.gui.screen.CreditsScreen) finish();
        }
        // and in case the credits were skipped somehow: leaving the End after the dragon is dead
        if (lastDim == 1 && dim == 0 && !finishSent && mySplits.containsKey("end") && dragonDead) finish();
        if (dim == 1 && !dragonDead) {
            EnderDragonEntity d = dragon();
            if (d != null && d.getHealth() <= 0) dragonDead = true;
        }
        lastDim = dim;
    }

    private static int lastDim;
    private static boolean dragonDead;

    private static int blockUnder() {
        int x = (int) Math.floor(mc.player.x), z = (int) Math.floor(mc.player.z);
        int y = (int) Math.floor(mc.player.shape.minY - 0.01);
        return y < 0 || y > 127 ? 0 : mc.world.getBlock(x, y, z);
    }

    /** An eye of ender in the air near them: they just threw one. */
    private static boolean eyeThrown() {
        for (Object o : mc.world.entities) {
            if (o instanceof net.minecraft.entity.EnderEyeEntity) {
                net.minecraft.entity.Entity e = (net.minecraft.entity.Entity) o;
                double dx = e.x - mc.player.x, dz = e.z - mc.player.z;
                if (dx * dx + dz * dz < 16 * 16) return true;
            }
        }
        return false;
    }

    private static void split(String s) {
        if (mySplits.containsKey(s)) return;
        long t = elapsed();
        mySplits.put(s, t);
        Long theirs = oppSplits.get(s);
        String cmp = theirs == null ? "" : " §7(" + time(Math.abs(t - theirs)) + (t <= theirs ? " ahead)" : " behind)");
        chat("§b" + splitName(s) + "§r " + time(t) + cmp);
        Net.post("/api/split", Net.map("match", matchId, "split", s), null);
    }

    private static void finish() {
        finishSent = true;
        mySplits.put("finish", elapsed());
        Net.post("/api/finish", Net.map("match", matchId, "igt", ticks), (r, err) -> {
            if (err != null) chat("§c" + err);
        });
    }

    private static boolean hasBlazeRod() {
        for (ItemStack s : mc.player.inventory.items) if (s != null && s.id == Item.BLAZE_ROD.id) return true;
        return false;
    }

    private static boolean nearPortalFrame() {
        int px = (int) Math.floor(mc.player.x), py = (int) Math.floor(mc.player.y), pz = (int) Math.floor(mc.player.z);
        int id = Block.END_PORTAL_FRAME.id;
        for (int x = px - 4; x <= px + 4; x++) {
            for (int y = py - 3; y <= py + 1; y++) {
                for (int z = pz - 4; z <= pz + 4; z++) {
                    if (mc.world.getBlock(x, y, z) == id) return true;
                }
            }
        }
        return false;
    }

    private static EnderDragonEntity dragon() {
        for (Object o : mc.world.entities) {
            if (o instanceof EnderDragonEntity) return (EnderDragonEntity) o;
        }
        return null;
    }

    // ---------- HUD ----------

    /** Top left while a match world is open. */
    public static List<String> hud() {
        List<String> lines = new ArrayList<>();
        if (!(inMatch() || state == State.DONE) || (opponent == null && !solo)) return lines;
        lines.add(solo ? "§bPrivate room§r" : (rankedMatch ? "§6Ranked§r" : "§dRoom§r") + " vs §e" + opponent + "§r" + (rankedMatch ? " (" + opponentElo + ")" : ""));
        if (state == State.LOADING) {
            lines.add(solo ? "Loading" : opponentReady ? "Opponent ready" : "Waiting for opponent to load");
            return lines;
        }
        if (state == State.COUNTDOWN) {
            lines.add("Starting in " + Math.max(1, (startAtLocal - System.currentTimeMillis() + 999) / 1000));
            return lines;
        }
        if (state == State.RUNNING) {
            lines.add("§f" + time(elapsed()));
            // F3 is off in matches; the height (feet) is the one coordinate players get
            if (mc != null && mc.player != null) lines.add("§7Y §f" + (int) Math.floor(mc.player.shape.minY));
            long now = System.currentTimeMillis();
            if (theirDrawUntil > now) lines.add("§eDraw offered: Esc to accept §7(" + ((theirDrawUntil - now) / 1000 + 1) + "s)");
            else if (myDrawUntil > now) lines.add("§7You offered a draw (" + ((myDrawUntil - now) / 1000 + 1) + "s)");
            if (theirSeedUntil > now) lines.add("§eNew seed asked: Esc to accept §7(" + ((theirSeedUntil - now) / 1000 + 1) + "s)");
            else if (mySeedUntil > now) lines.add("§7You asked for a new seed (" + ((mySeedUntil - now) / 1000 + 1) + "s)");
        }
        for (int i = 0; i < SPLITS.length; i++) {
            Long mine = mySplits.get(SPLITS[i]), theirs = oppSplits.get(SPLITS[i]);
            if (mine == null && theirs == null) continue;
            lines.add("§7" + SPLIT_NAMES[i] + "§r " + (mine == null ? "-" : "§b" + time(mine)) + (solo ? "" : "§7 | §e" + (theirs == null ? "-" : time(theirs))));
        }
        if (state == State.DONE) lines.addAll(resultLines);
        return lines;
    }

    // ---------- helpers ----------

    public static String splitName(String s) {
        for (int i = 0; i < SPLITS.length; i++) if (SPLITS[i].equals(s)) return SPLIT_NAMES[i];
        return s;
    }

    /** ms as m:ss.S, or h:mm:ss for long ones. */
    public static String time(long ms) {
        long s = ms / 1000;
        if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d.%d", s / 60, s % 60, ms % 1000 / 100);
    }

    public static void chat(String s) {
        log("chat: " + s.replaceAll("§.", ""));
        if (mc != null && mc.gui != null && mc.world != null) mc.gui.addChatMessage(s);
    }

    public static boolean onTitleScreen() {
        return mc != null && mc.screen instanceof TitleScreen;
    }
}
