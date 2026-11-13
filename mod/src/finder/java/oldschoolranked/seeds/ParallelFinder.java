package oldschoolranked.seeds;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldStorage;
import net.minecraft.world.storage.WorldStorageSource;

/**
 * The seed finder on several cores at once, when Queue Wall is installed. 1.0's world generator assumes one thread;
 * Queue Wall makes it safe on its own generator threads (per-thread caches, decorators and pools), so each thread
 * here is one of those and checks its own worlds. Only loaded when Queue Wall is present.
 */
public final class ParallelFinder {
    private static final AtomicInteger tried = new AtomicInteger(), built = new AtomicInteger(), found = new AtomicInteger();
    private static final AtomicLong started = new AtomicLong();
    /** Seeds to check instead of random ones (testing: -Dranked.finderSeeds=1,2,3). */
    private static final ConcurrentLinkedQueue<Long> fixed = new ConcurrentLinkedQueue<>();
    private static boolean fixedMode;
    private static String out;

    private ParallelFinder() {
    }

    public static int threads() {
        int t = FinderSettings.threads > 0 ? FinderSettings.threads : Runtime.getRuntime().availableProcessors() - 2;
        // a checked world can hold a few hundred chunks: leave each thread room for one
        long mem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        int byMemory = (int) Math.max(1, (mem - 400) / 250);
        return Math.max(1, Math.min(t, byMemory));
    }

    public static void start(Minecraft mc, String outFile) {
        out = outFile;
        String list = System.getProperty("ranked.finderSeeds", "");
        if (!list.trim().isEmpty()) {
            fixedMode = true;
            for (String s : list.split(",")) if (!s.trim().isEmpty()) fixed.add(Long.parseLong(s.trim()));
        }
        int n = threads();
        started.set(System.currentTimeMillis());
        System.out.println("[finder] checking on " + n + " threads with Queue Wall (" + Runtime.getRuntime().availableProcessors() + " cores)");
        for (int i = 0; i < n; i++) {
            final int id = i;
            new queuewall.GenThread(() -> loop(mc, id), "Seed finder " + (i + 1)).start();
        }
    }

    public static String status() {
        long secs = Math.max(1, (System.currentTimeMillis() - started.get()) / 1000);
        return "Seed finder: " + tried.get() + " checked, " + found.get() + " good (" + threads() + " threads, "
                + String.format(java.util.Locale.ROOT, "%.1f", built.get() * 60.0 / secs) + " worlds a minute)";
    }

    private static void loop(Minecraft mc, int id) {
        Random random = new Random();
        String folder = "seed-finder-" + (id + 1);
        WorldStorageSource source = mc.getWorldStorageSource();
        while (true) {
            long seed;
            if (fixedMode) {
                Long s = fixed.poll();
                if (s == null) {
                    System.out.println("[finder] thread " + (id + 1) + " done");
                    return;
                }
                seed = s;
                tried.incrementAndGet();
            } else {
                // the biome prefilter also uses the biome layers, so it runs on this thread's own IntCache too
                do {
                    queuewall.ThreadSafety.freshIntCache();
                    seed = random.nextLong();
                    tried.incrementAndGet();
                } while (!SeedCheck.worthMaking(seed));
            }
            String verdict;
            int spawnX = 0, spawnZ = 0, entities = 0;
            try {
                source.delete(folder);
                queuewall.ThreadSafety.freshIntCache();
                WorldStorage storage = source.get(folder, false);
                World w = new World(storage, "Seed finder", new WorldSettings(seed, 0, true, false));
                built.incrementAndGet();
                spawnX = w.getData().getSpawnX();
                spawnZ = w.getData().getSpawnZ();
                verdict = SeedCheck.check(w, spawnX, spawnZ);
                entities = w.entities.size();
            } catch (Throwable t) {
                System.out.println("[finder] seed " + seed + " failed to check: " + t);
                t.printStackTrace();
                continue;
            }
            if (verdict.startsWith("OK")) {
                found.incrementAndGet();
                synchronized (ParallelFinder.class) {
                    try (PrintWriter w = new PrintWriter(new FileWriter(out, true))) {
                        w.println(seed + "  # " + verdict.substring(3));
                    } catch (Exception e) {
                        System.out.println("[finder] couldn't write " + out + ": " + e);
                    }
                }
                FinderStatus.lastGood = seed + "  " + verdict.substring(3);
            }
            System.out.println("[finder] " + tried.get() + " tried, " + found.get() + " found: " + seed + " " + verdict + (verdict.startsWith("OK") ? "" : " (spawn " + spawnX + " " + spawnZ + ", " + entities + " entities)"));
            FinderStatus.status = status();
        }
    }
}
