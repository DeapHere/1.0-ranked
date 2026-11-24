package oldschoolranked.seeds;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** The seed finder's rules, from config/ranked-seedfinder.properties (made with the defaults on first launch). */
public final class FinderSettings {
    public static int desertMinPercent = 8, desertRadius = 160;
    public static double fortressMaxChunks = 8;
    public static int waterMaxPercent = 100, waterRadius = 100; // 100 = don't check: the desert run check covers water
    public static int lavaRadius = 0; // match worlds always get a lava pool now
    /** No Extreme Hills within this many blocks of spawn (0 = don't check). */
    public static int hillsRadius = 0;
    /** At most this much river biome within runLength blocks of spawn (percent, 100 = don't check). */
    public static int riverMaxPercent = 100;
    /** At most this much water within nearWaterRadius blocks of spawn (percent). */
    public static int nearWaterMaxPercent = 20, nearWaterRadius = 48;
    /** Within 100 blocks: at most highMaxPercent of the ground at y 78 or above, and heights spread at most maxSpread (standard deviation). */
    public static int highMaxPercent = 100;
    public static double maxSpread = 99;
    /** At least minRuns of 32 straight lines from spawn, runLength blocks long, cross no water and no steep climbs. */
    public static int minRuns = 0, runLength = 210;
    /** The run toward the desert: a strip 2*runWidth+1 wide and runLength long (or 48 blocks into the desert if further) with no water, climbing at most maxClimb blocks in total. */
    public static int runWidth = 2, maxClimb = 12;
    public static int minLogs = 8, treeRadius = 64;
    public static int minFood = 0, foodRadius = 64, minSheep = 0, sheepRadius = 200; // match worlds get pigs, cows, sheep and chickens
    /** Worlds checked at once when Queue Wall is installed. 0 = cores minus 2. */
    public static int threads = 0;
    /** A cave open to the sky within this many blocks of spawn. 0 = don't check. */
    public static int caveRadius = 64;
    public static int caveIron = 6;
    /** A desert reachable from spawn over plains only, within this many blocks. */
    public static int desertPathRadius = 160;
    /** Lava blocks a surface pool needs (within 6 blocks of each other). */
    public static int minLavaBlocks = 6;
    /** A towerArea x towerArea spot in that desert with no cave, water or lava for towerDepth blocks down. 0 = don't check. */
    public static int towerDepth = 0, towerArea = 12;

    private FinderSettings() {
    }

    public static void load() {
        File f = FabricLoader.getInstance().getConfigDir().resolve("ranked-seedfinder.properties").toFile();
        Properties p = new Properties();
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (Exception e) {
                System.out.println("[finder] couldn't read " + f + ": " + e);
            }
        }
        desertMinPercent = i(p, "desertMinPercent", desertMinPercent);
        desertRadius = i(p, "desertRadius", desertRadius);
        fortressMaxChunks = d(p, "fortressMaxChunks", fortressMaxChunks);
        // settings from before version 3 checked a full circle around spawn; now only the run toward the desert has to be clear
        boolean v3 = i(p, "settingsVersion", 1) >= 3;
        if (v3) {
            waterMaxPercent = i(p, "waterMaxPercent", waterMaxPercent);
            waterRadius = i(p, "waterRadius", waterRadius);
        }
        // settings from before version 2 still ask for lava and animals, which match worlds now get anyway
        boolean old = i(p, "settingsVersion", 1) < 2;
        if (!old) lavaRadius = i(p, "lavaRadius", lavaRadius);
        if (v3) {
            hillsRadius = i(p, "hillsRadius", hillsRadius);
            riverMaxPercent = i(p, "riverMaxPercent", riverMaxPercent);
            nearWaterMaxPercent = i(p, "nearWaterMaxPercent", nearWaterMaxPercent);
            nearWaterRadius = i(p, "nearWaterRadius", nearWaterRadius);
            minRuns = i(p, "minRuns", minRuns);
            highMaxPercent = i(p, "highMaxPercent", highMaxPercent);
            maxSpread = d(p, "maxSpread", maxSpread);
        }
        runLength = i(p, "runLength", runLength);
        runWidth = i(p, "runWidth", runWidth);
        maxClimb = i(p, "maxClimb", maxClimb);
        minLogs = i(p, "minLogs", minLogs);
        treeRadius = i(p, "treeRadius", treeRadius);
        if (!old) minFood = i(p, "minFood", minFood);
        foodRadius = i(p, "foodRadius", foodRadius);
        if (!old) minSheep = i(p, "minSheep", minSheep);
        sheepRadius = i(p, "sheepRadius", sheepRadius);
        threads = i(p, "threads", threads);
        caveRadius = i(p, "caveRadius", caveRadius);
        caveIron = i(p, "caveIron", caveIron);
        desertPathRadius = i(p, "desertPathRadius", desertPathRadius);
        minLavaBlocks = i(p, "minLavaBlocks", minLavaBlocks);
        towerDepth = i(p, "towerDepth", towerDepth);
        towerArea = i(p, "towerArea", towerArea);
        // write it back so every setting is listed, with its current value
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            String comments = "Oldschool Ranked seed finder. Change a value, save, and restart the finder.\n"
                    + "Every seed must have a plains spawn and pass all of these. Looser values find seeds faster.\n"
                    + "desertMinPercent: how much of the area within desertRadius blocks of spawn is desert (percent)\n"
                    + "fortressMaxChunks: fortress start within this many chunks of spawn/8 in the nether\n"
                    + "waterMaxPercent: at most this much water within waterRadius blocks of spawn (percent)\n"
                    + "lavaRadius: a surface lava pool within this many blocks of spawn, 0 to skip (match worlds always get one)\n"
                    + "hillsRadius: no Extreme Hills within this many blocks of spawn, 0 to skip\n"
                    + "riverMaxPercent: at most this much river within runLength blocks of spawn (percent)\n"
                    + "nearWaterMaxPercent: at most this much water within nearWaterRadius blocks of spawn (percent)\n"
                    + "highMaxPercent, maxSpread: within 100 blocks, at most this much ground at y 78+ (percent), and how uneven the ground is (lower is flatter)\n"
                    + "minRuns: of 32 straight lines from spawn, runLength blocks long, at least this many cross no water and no steep climbs (0 to skip)\n"
                    + "runLength, runWidth, maxClimb: the run from spawn toward the desert, runLength blocks long (and at least 48 into the desert) and 2*runWidth+1 wide, must have no water and climb at most maxClimb blocks\n"
                    + "minLogs: trees within treeRadius blocks (counts columns with a log in them)\n"
                    + "minFood, minSheep: animals near spawn, 0 to skip (match worlds always get pigs, cows, chickens and sheep)\n"
                    + "desertPathRadius: a desert you can walk to from spawn over plains (no forest in the way), within this many blocks\n"
                    + "towerDepth, towerArea: a towerArea x towerArea spot in that desert where digging down towerDepth blocks hits no cave, water or lava (off by default: match worlds have no caves in desert chunks)\n"
                    + "minLavaBlocks: lava blocks showing at the surface for it to count as a lava pool\n"
                    + "caveRadius: a cave opening (open to the sky) within this many blocks of spawn, 0 to skip this check\n"
                    + "caveIron: iron ore blocks you can see from inside that cave (0 for any cave)\n"
                    + "threads: with Queue Wall installed, how many worlds to check at once (0 = number of cores minus 2)";
            Properties outP = new Properties() {
                @Override
                public synchronized java.util.Enumeration<Object> keys() {
                    return java.util.Collections.enumeration(new java.util.TreeSet<Object>(super.keySet()));
                }
            };
            outP.setProperty("desertMinPercent", "" + desertMinPercent);
            outP.setProperty("desertRadius", "" + desertRadius);
            outP.setProperty("fortressMaxChunks", "" + fortressMaxChunks);
            outP.setProperty("waterMaxPercent", "" + waterMaxPercent);
            outP.setProperty("waterRadius", "" + waterRadius);
            outP.setProperty("settingsVersion", "3");
            outP.setProperty("lavaRadius", "" + lavaRadius);
            outP.setProperty("hillsRadius", "" + hillsRadius);
            outP.setProperty("riverMaxPercent", "" + riverMaxPercent);
            outP.setProperty("nearWaterMaxPercent", "" + nearWaterMaxPercent);
            outP.setProperty("nearWaterRadius", "" + nearWaterRadius);
            outP.setProperty("minRuns", "" + minRuns);
            outP.setProperty("highMaxPercent", "" + highMaxPercent);
            outP.setProperty("maxSpread", "" + maxSpread);
            outP.setProperty("runLength", "" + runLength);
            outP.setProperty("runWidth", "" + runWidth);
            outP.setProperty("maxClimb", "" + maxClimb);
            outP.setProperty("minLogs", "" + minLogs);
            outP.setProperty("treeRadius", "" + treeRadius);
            outP.setProperty("minFood", "" + minFood);
            outP.setProperty("foodRadius", "" + foodRadius);
            outP.setProperty("minSheep", "" + minSheep);
            outP.setProperty("sheepRadius", "" + sheepRadius);
            outP.setProperty("threads", "" + threads);
            outP.setProperty("caveRadius", "" + caveRadius);
            outP.setProperty("caveIron", "" + caveIron);
            outP.setProperty("desertPathRadius", "" + desertPathRadius);
            outP.setProperty("minLavaBlocks", "" + minLavaBlocks);
            outP.setProperty("towerDepth", "" + towerDepth);
            outP.setProperty("towerArea", "" + towerArea);
            outP.store(out, comments);
        } catch (Exception e) {
            System.out.println("[finder] couldn't write " + f + ": " + e);
        }
    }

    private static int i(Properties p, String k, int def) {
        try {
            return Integer.parseInt(p.getProperty(k, "" + def).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double d(Properties p, String k, double def) {
        try {
            return Double.parseDouble(p.getProperty(k, "" + def).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
