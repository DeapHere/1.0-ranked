package oldschoolranked;

import java.util.Random;
import net.minecraft.entity.mob.animal.ChickenEntity;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.LakeFeature;

/**
 * Things every match world gets on top of the seed, placed right after the world is created (before the countdown),
 * from a sequence seeded by the match seed, so both players get exactly the same:
 * a surface lava pool in plains or desert within 100 blocks of spawn, a pack of chickens near spawn, and a patch of
 * gravel on the floor of the iron cave near spawn, in sight of the iron.
 */
public final class MatchSetup {
    /** Where the last lava pool and chickens went (for tests). */
    public static int[] lastLava, lastChickens;
    /** How many gravel blocks the last setup placed, and where the cave opening was. */
    public static int lastGravel;
    public static int[] lastCave, lastGravelAt;

    private MatchSetup() {
    }

    /** Eyes of ender thrown in this match world so far; the first SAFE_EYES never break. */
    public static int eyesThrown;
    public static final int SAFE_EYES = 2;

    public static void apply(World w, long seed) {
        eyesThrown = 0;
        try {
            long t0 = System.currentTimeMillis();
            int sx = w.getData().getSpawnX(), sz = w.getData().getSpawnZ();
            int[] lava = lavaPool(w, seed, sx, sz);
            int[] hens = chickens(w, seed, sx, sz);
            animals(w, seed, sx, sz);
            lastLava = lava;
            lastChickens = hens;
            lastCave = null;
            lastGravel = gravel(w, seed, sx, sz);
            Ranked.log("match setup: lava pool " + (lava == null ? "none" : (int) Math.hypot(lava[0] - sx, lava[1] - sz) + " blocks")
                    + ", chickens " + (hens == null ? "none" : (int) Math.hypot(hens[0] - sx, hens[1] - sz) + " blocks")
                    + ", pigs " + far(lastPigs, sx, sz) + ", cows " + far(lastCows, sx, sz) + ", sheep packs " + sheepPacks + " (nearest " + far(lastSheep, sx, sz) + ")"
                    + ", cave gravel " + lastGravel + (lastCave == null ? "" : " (cave " + (int) Math.hypot(lastCave[0] - sx, lastCave[2] - sz) + " blocks)")
                    + ", " + (System.currentTimeMillis() - t0) + " ms");
        } catch (RuntimeException e) {
            Ranked.log("match setup failed: " + e);
        }
    }

    private static String far(int[] at, int sx, int sz) {
        return at == null ? "none" : (int) Math.hypot(at[0] - sx, at[1] - sz) + " blocks";
    }

    private static boolean plainsOrDesert(World w, int x, int z) {
        for (int dx = -6; dx <= 6; dx += 6) {
            for (int dz = -6; dz <= 6; dz += 6) {
                Biome b = w.getBiomeSource().getBiome(x + dx, z + dz);
                if (b != Biome.PLAINS && b != Biome.DESERT) return false;
            }
        }
        return true;
    }

    /** Generates and populates the chunks around (x, z), so whatever we place isn't overwritten later. */
    private static void load(World w, int x, int z, int r) {
        for (int cx = (x >> 4) - r; cx <= (x >> 4) + r; cx++) {
            for (int cz = (z >> 4) - r; cz <= (z >> 4) + r; cz++) w.getBlock(cx << 4, 64, cz << 4);
        }
    }

    private static int surfaceLava(World w, int x, int z) {
        int n = 0;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                int id = w.getBlock(x + dx, w.getHeight(x + dx, z + dz) - 1, z + dz);
                if (id == 10 || id == 11) n++;
            }
        }
        return n;
    }

    private static int[] lavaPool(World w, long seed, int sx, int sz) {
        Random r = new Random(seed * 31L + 0x1A7AL);
        for (int attempt = 0; attempt < 80; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            int d = 40 + r.nextInt(52); // 40 to 91, so the whole pool is within 100
            int x = sx + (int) Math.round(Math.cos(a) * d), z = sz + (int) Math.round(Math.sin(a) * d);
            long featureSeed = r.nextLong();
            if (!plainsOrDesert(w, x, z)) continue;
            load(w, x, z, 2);
            int y = w.getHeight(x, z);
            int ground = w.getBlock(x, y - 1, z);
            if (ground != 2 && ground != 3 && ground != 12 && ground != 1) continue;
            if (!clearTop(w, x, z)) continue;
            new LakeFeature(11).place(w, new Random(featureSeed), x, y, z);
            if (surfaceLava(w, x, z) >= 6) return new int[] {x, z};
        }
        return null;
    }

    /** No water, lava or trees showing near (x, z): open ground. */
    private static boolean clearTop(World w, int x, int z) {
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -8; dz <= 8; dz += 2) {
                int id = w.getBlock(x + dx, w.getHeight(x + dx, z + dz) - 1, z + dz);
                if (id == 8 || id == 9 || id == 10 || id == 11 || id == 17 || id == 18) return false;
            }
        }
        return true;
    }

    private static int[] chickens(World w, long seed, int sx, int sz) {
        return herd(w, new Random(seed * 31L + 0xC41CL), sx, sz, 8, 24, 4, ChickenEntity::new);
    }

    /**
     * Food and wool near spawn every match, so seeds don't have to be picked for animals. Chickens are always there;
     * each seed adds nothing more, cows, pigs, or both. Sheep come in 3 packs of 4 for wool.
     */
    public static int[] lastPigs, lastCows, lastSheep;
    public static int sheepPacks;

    private static void animals(World w, long seed, int sx, int sz) {
        int mix = new Random(seed * 31L + 0xA1A1L).nextInt(4); // 0 chickens only, 1 + cows, 2 + pigs, 3 + both
        lastCows = (mix & 1) != 0 ? herd(w, new Random(seed * 31L + 0xC0C0L), sx, sz, 12, 40, 3, net.minecraft.entity.mob.animal.CowEntity::new) : null;
        lastPigs = (mix & 2) != 0 ? herd(w, new Random(seed * 31L + 0x9165L), sx, sz, 12, 40, 3, net.minecraft.entity.mob.animal.PigEntity::new) : null;
        lastSheep = null;
        sheepPacks = 0;
        int[][] bands = {{16, 40}, {24, 56}, {32, 64}};
        for (int i = 0; i < bands.length; i++) {
            int[] at = herd(w, new Random(seed * 31L + 0x5EE9L + i * 0x101L), sx, sz, bands[i][0], bands[i][1], 4, net.minecraft.entity.mob.animal.SheepEntity::new);
            if (at != null) { sheepPacks++; if (lastSheep == null) lastSheep = at; }
        }
    }

    /** count animals within 2 blocks of a grass spot min to max blocks from spawn. */
    private static int[] herd(World w, Random r, int sx, int sz, int min, int max, int count, java.util.function.Function<World, net.minecraft.entity.Entity> make) {
        for (int attempt = 0; attempt < 60; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            int d = min + r.nextInt(max - min + 1);
            int x = sx + (int) Math.round(Math.cos(a) * d), z = sz + (int) Math.round(Math.sin(a) * d);
            int y = w.getHeight(x, z);
            if (w.getBlock(x, y - 1, z) != 2) continue;
            for (int i = 0; i < count; i++) {
                int cx = x + r.nextInt(5) - 2, cz = z + r.nextInt(5) - 2;
                int cy = w.getHeight(cx, cz);
                if (w.getBlock(cx, cy - 1, cz) == 8 || w.getBlock(cx, cy - 1, cz) == 9) { cx = x; cz = z; cy = y; }
                net.minecraft.entity.Entity c = make.apply(w);
                c.setPositionAndAngles(cx + 0.5, cy, cz + 0.5, r.nextFloat() * 360, 0);
                w.addEntity(c);
            }
            return new int[] {x, z};
        }
        return null;
    }

    // ---- gravel in the iron cave ----

    private static final int CAVE_RADIUS = 64, CAVE_REACH = 40;

    /**
     * Finds the cave open to the sky near spawn with the most iron showing (the same search the seed finder uses, so it's
     * the cave every pool seed was picked for), and turns the stone floor right next to that iron into gravel.
     */
    private static int gravel(World w, long seed, int sx, int sz) {
        Random gr = new Random(seed * 31L + 0x6EA7L);
        int count = 8 + gr.nextInt(9); // 8 to 16, different every seed
        long jitter = gr.nextLong();
        int r = CAVE_RADIUS + CAVE_REACH + 16;
        for (int cx = (sx - r) >> 4; cx <= (sx + r) >> 4; cx++) {
            for (int cz = (sz - r) >> 4; cz <= (sz + r) >> 4; cz++) w.getChunkAt(cx, cz);
        }
        Cave best = null;
        int[][] side = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        search:
        for (int d = 0; d <= CAVE_RADIUS; d += 4) {
            for (int x = sx - d; x <= sx + d; x += 2) {
                for (int z = sz - d; z <= sz + d; z += 2) {
                    if (Math.max(Math.abs(x - sx), Math.abs(z - sz)) < d - 4) continue;
                    if ((x - sx) * (x - sx) + (z - sz) * (z - sz) > CAVE_RADIUS * CAVE_RADIUS) continue;
                    int g = ground(w, x, z);
                    if (g < 10) continue;
                    int id = w.getBlock(x, g, z);
                    if (id == 8 || id == 9 || id == 10 || id == 11 || id == 79) continue;
                    if (Math.min(Math.min(ground(w, x + 6, z), ground(w, x - 6, z)), Math.min(ground(w, x, z + 6), ground(w, x, z - 6))) < g + 5) continue;
                    boolean tunnel = false;
                    for (int[] s : side) {
                        int tx = x + s[0] * 4, tz = z + s[1] * 4;
                        if (w.getBlock(tx, g + 1, tz) == 0 && w.getBlock(tx, g + 2, tz) == 0 && ground(w, tx, tz) >= g + 4) tunnel = true;
                    }
                    if (!tunnel) continue;
                    Cave c = follow(w, x, g + 1, z);
                    if (best == null || c.iron.size() > best.iron.size()) best = c;
                    if (c.iron.size() >= 6) break search;
                }
            }
        }
        if (best == null || best.iron.isEmpty()) return 0;
        lastCave = best.open;
        // a patch of gravel on the cave floor, a few blocks from the iron and in plain sight of it
        java.util.ArrayList<long[]> floor = new java.util.ArrayList<>(), spots = new java.util.ArrayList<>();
        for (long k : best.air) {
            int x = kx(k), y = ky(k), z = kz(k);
            if (w.getBlock(x, y - 1, z) != 1) continue;
            floor.add(new long[] {x, y - 1, z});
            int near = Integer.MAX_VALUE;
            long seen = -1;
            for (long ik : best.iron) {
                int dx = kx(ik) - x, dy = ky(ik) - y, dz = kz(ik) - z, d = dx * dx + dy * dy + dz * dz;
                if (d < near && d >= 4 * 4 && d <= 12 * 12 && canSee(w, x, y, z, kx(ik), ky(ik), kz(ik))) {
                    near = d;
                    seen = ik;
                }
            }
            if (seen >= 0) spots.add(new long[] {near, x, y - 1, z});
        }
        if (spots.isEmpty()) return 0;
        spots.sort(MatchSetup::compare);
        long[] c = spots.get(0);
        // the floor around that spot, nearest first
        java.util.ArrayList<long[]> patch = new java.util.ArrayList<>();
        for (long[] f : floor) {
            long dx = f[0] - c[1], dy = f[1] - c[2], dz = f[2] - c[3];
            if (Math.abs(dy) <= 2 && dx * dx + dz * dz <= 20) {
                // nearest first, with some noise so the patch isn't a neat circle
                long noise = new Random(jitter ^ (f[0] * 341873128712L + f[1] * 132897987541L + f[2])).nextInt(8);
                patch.add(new long[] {(dx * dx + dy * dy + dz * dz) * 2 + noise, f[0], f[1], f[2]});
            }
        }
        patch.sort(MatchSetup::compare);
        int n = 0;
        for (long[] s : patch) {
            if (n >= count) break;
            w.setBlock((int) s[1], (int) s[2], (int) s[3], 13);
            if (n == 0) lastGravelAt = new int[] {(int) s[1], (int) s[2], (int) s[3]};
            n++;
        }
        return n;
    }

    private static int compare(long[] a, long[] b) {
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return Long.compare(a[i], b[i]);
        return 0;
    }

    /** Nothing but air between the block at (x, y, z) and the block at (tx, ty, tz). */
    private static boolean canSee(World w, int x, int y, int z, int tx, int ty, int tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        int steps = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 2);
        for (int i = 1; i < steps; i++) {
            double f = (double) i / steps;
            int bx = (int) Math.floor(x + 0.5 + dx * f), by = (int) Math.floor(y + 0.5 + dy * f), bz = (int) Math.floor(z + 0.5 + dz * f);
            if (bx == tx && by == ty && bz == tz) return true;
            if (w.getBlock(bx, by, bz) != 0) return false;
        }
        return true;
    }

    private static final class Cave {
        int[] open;
        java.util.HashSet<Long> air = new java.util.HashSet<>(), iron = new java.util.HashSet<>();
    }

    /** Follows the cave's connected air underground from its opening and collects the air and the iron ore touching it. */
    private static Cave follow(World w, int ox, int oy, int oz) {
        Cave c = new Cave();
        c.open = new int[] {ox, oy, oz};
        java.util.HashMap<Long, Integer> groundAt = new java.util.HashMap<>();
        java.util.ArrayDeque<int[]> todo = new java.util.ArrayDeque<>();
        todo.add(new int[] {ox, oy, oz});
        c.air.add(key(ox, oy, oz));
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!todo.isEmpty() && c.air.size() < 30000) {
            int[] p = todo.poll();
            for (int[] d : dirs) {
                int x = p[0] + d[0], y = p[1] + d[1], z = p[2] + d[2];
                if (y < 1 || y > 126 || Math.abs(x - ox) > CAVE_REACH || Math.abs(z - oz) > CAVE_REACH) continue;
                int id = w.getBlock(x, y, z);
                if (id == 15) {
                    c.iron.add(key(x, y, z));
                    continue;
                }
                if (id != 0) continue;
                long col = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                Integer g = groundAt.get(col);
                if (g == null) groundAt.put(col, g = ground(w, x, z));
                if (y > oy + 1 && y >= g) continue;
                if (c.air.add(key(x, y, z))) todo.add(new int[] {x, y, z});
            }
        }
        return c;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int kx(long k) {
        return (int) (k >> 38) << 6 >> 6;
    }

    private static int kz(long k) {
        return (int) ((k >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    private static int ky(long k) {
        return (int) (k & 0xFFF);
    }

    /** The top block that's ground: skips air, leaves, logs, snow and plants. */
    private static int ground(World w, int x, int z) {
        w.getChunkAt(x >> 4, z >> 4);
        int y = w.getHeight(x, z) - 1;
        while (y > 0) {
            int id = w.getBlock(x, y, z);
            if (id != 0 && id != 17 && id != 18 && id != 31 && id != 32 && id != 37 && id != 38 && id != 39 && id != 40 && id != 78 && id != 81 && id != 83 && id != 106) break;
            y--;
        }
        return y;
    }
}
