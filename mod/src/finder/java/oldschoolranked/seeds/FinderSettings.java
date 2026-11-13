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
    public static int waterMaxPercent = 15, waterRadius = 24;
    public static int lavaRadius = 128;
    public static int minLogs = 8, treeRadius = 64;
    public static int minFood = 4, foodRadius = 64, minSheep = 2, sheepRadius = 200;
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
        waterMaxPercent = i(p, "waterMaxPercent", waterMaxPercent);
        waterRadius = i(p, "waterRadius", waterRadius);
        lavaRadius = i(p, "lavaRadius", lavaRadius);
        minLogs = i(p, "minLogs", minLogs);
        treeRadius = i(p, "treeRadius", treeRadius);
        minFood = i(p, "minFood", minFood);
        foodRadius = i(p, "foodRadius", foodRadius);
        minSheep = i(p, "minSheep", minSheep);
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
                    + "lavaRadius: a surface lava pool within this many blocks of spawn (straight-line distance)\n"
                    + "minLogs: trees within treeRadius blocks (counts columns with a log in them)\n"
                    + "minFood: pigs, cows and chickens within foodRadius blocks of spawn\n"
                    + "minSheep: sheep within sheepRadius blocks of spawn (a big radius makes each check slower)\n"
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
            outP.setProperty("lavaRadius", "" + lavaRadius);
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
