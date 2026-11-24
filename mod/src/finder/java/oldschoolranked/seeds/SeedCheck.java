package oldschoolranked.seeds;

import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.animal.ChickenEntity;
import net.minecraft.entity.mob.animal.CowEntity;
import net.minecraft.entity.mob.animal.PigEntity;
import net.minecraft.entity.mob.animal.SheepEntity;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;

/**
 * The seed finder's rules, run against a world. Holds no shared state, so worlds can be checked on several threads
 * at once. Terrain is only generated where a check reads it, so failing seeds cost little.
 */
public final class SeedCheck {
    private SeedCheck() {
    }

    /**
     * Makes sure every chunk within r blocks of spawn is generated and populated. Lakes, lava pools, trees and animals
     * are placed when a chunk is populated, which happens once its neighbours exist, so this loads one ring further.
     */
    private static final class Area {
        final World w;
        final int sx, sz;
        int loaded = -1;

        Area(World w, int sx, int sz) {
            this.w = w;
            this.sx = sx;
            this.sz = sz;
        }

        void ensure(int r) {
            if (r <= loaded) return;
            for (int cx = (sx - r - 16) >> 4; cx <= (sx + r + 16) >> 4; cx++) {
                for (int cz = (sz - r - 16) >> 4; cz <= (sz + r + 16) >> 4; cz++) w.getChunkAt(cx, cz);
            }
            loaded = r;
        }
    }

    /** "OK <notes>" for a good seed, otherwise "no: <the first rule it failed>". */
    public static String check(World w, int sx, int sz) {
        Area area = new Area(w, sx, sz);
        // 1. biomes only (no terrain yet, so this is quick): a plains spawn with desert close by
        Biome b = w.getBiomeSource().getBiome(sx, sz);
        if (b != Biome.PLAINS) return "no: biome " + b.name;
        int desert = 0, plains = 0, samples = 0;
        int dr = FinderSettings.desertRadius;
        for (int x = sx - dr; x <= sx + dr; x += 16) {
            for (int z = sz - dr; z <= sz + dr; z += 16) {
                samples++;
                Biome ob = w.getBiomeSource().getBiome(x, z);
                if (ob == Biome.DESERT) desert++;
                if (ob == Biome.PLAINS) plains++;
            }
        }
        int desertPct = desert * 100 / samples, plainsPct = plains * 100 / samples;
        if (desertPct < FinderSettings.desertMinPercent) return "no: desert " + desertPct + "%";
        int[] desertAt = openDesert(w.getBiomeSource(), sx, sz);
        if (desertAt == null) return "no: desert not reachable through plains";
        int desertDist = desertAt[0];
        // 2. fortress, from 1.0's placement maths: a portal built near spawn comes out at spawn / 8
        double fortress = fortress(w.getSeed(), (sx >> 3) >> 4, (sz >> 3) >> 4)[0];
        if (fortress > FinderSettings.fortressMaxChunks) return "no: fortress " + (int) fortress + " chunks";
        // 3. terrain: reading blocks generates it. First the run toward the desert, which only needs the chunks along it
        int[] run = Terrain.desertRun(w, sx, sz);
        if (run == null) return "no: desert run";
        String t = Terrain.check(w, sx, sz);
        if (t != null) return t;
        int water = 0, cells = 1;
        if (FinderSettings.waterMaxPercent < 100) {
        area.ensure(FinderSettings.waterRadius);
        cells = 0;
        for (int x = sx - FinderSettings.waterRadius; x <= sx + FinderSettings.waterRadius; x += 2) {
            for (int z = sz - FinderSettings.waterRadius; z <= sz + FinderSettings.waterRadius; z += 2) {
                cells++;
                int id = top(w, x, z);
                if (id == 8 || id == 9 || id == 79) water++;
            }
        }
        }
        int waterPct = water * 100 / cells;
        if (waterPct > FinderSettings.waterMaxPercent) return "no: water " + waterPct + "%";
        if (!Terrain.biomesOk(w.getBiomeSource(), sx, sz, 0)) return "no: hills or rivers";
        area.ensure(FinderSettings.treeRadius);
        int logs = 0;
        for (int x = sx - FinderSettings.treeRadius; x <= sx + FinderSettings.treeRadius; x++) {
            for (int z = sz - FinderSettings.treeRadius; z <= sz + FinderSettings.treeRadius; z++) {
                int h = height(w, x, z);
                for (int y = h - 1; y > h - 12 && y > 0; y--) {
                    if (w.getBlock(x, y, z) == 17) { logs++; break; }
                }
            }
        }
        if (logs < FinderSettings.minLogs) {
            if (Boolean.getBoolean("ranked.finderDebug")) {
                int pop = 0, all = 0;
                java.util.Map<String, Integer> bs = new java.util.TreeMap<>();
                for (int cx = (sx - 64) >> 4; cx <= (sx + 64) >> 4; cx++) for (int cz = (sz - 64) >> 4; cz <= (sz + 64) >> 4; cz++) {
                    all++;
                    if (w.getChunkAt(cx, cz).terrainPopulated) pop++;
                    bs.merge(w.getBiomeSource().getBiome(cx * 16 + 8, cz * 16 + 8).name, 1, Integer::sum);
                }
                System.out.println("[finder] debug trees: populated " + pop + "/" + all + " biomes " + bs);
            }
            return "no: trees " + logs;
        }
        int lavaDist = -1;
        for (int r = 4; r <= FinderSettings.lavaRadius && lavaDist < 0; r += 4) {
            area.ensure(r);
            for (int x = sx - r; x <= sx + r && lavaDist < 0; x += 2) {
                for (int z = sz - r; z <= sz + r; z += 2) {
                    if (Math.max(Math.abs(x - sx), Math.abs(z - sz)) < r - 4) continue;
                    int id = top(w, x, z);
                    if (id == 10 || id == 11) {
                        int d = (int) Math.sqrt((x - sx) * (x - sx) + (z - sz) * (z - sz));
                        if (d > FinderSettings.lavaRadius || lavaPool(area, x, z) < FinderSettings.minLavaBlocks) continue;
                        lavaDist = d;
                        break;
                    }
                }
            }
        }
        if (FinderSettings.lavaRadius > 0 && lavaDist < 0) return "no: lava";
        int towerDist = -1;
        if (FinderSettings.towerDepth > 0) {
            towerDist = towerSpot(area, desertAt, FinderSettings.towerArea, FinderSettings.towerDepth);
            if (towerDist < 0) return "no: no cave-free spot to dig down in the desert";
        }
        int caveDist = -1, caveIron = 0;
        if (FinderSettings.caveRadius > 0) {
            int[] c = cave(area, FinderSettings.caveRadius);
            if (c == null) return "no: cave";
            if (c[1] < FinderSettings.caveIron) return "no: cave iron " + c[1];
            caveDist = c[0];
            caveIron = c[1];
        }
        // 4. animals: placed when chunks are populated
        if (FinderSettings.minFood > 0) area.ensure(FinderSettings.foodRadius);
        int food = 0, chickens = 0;
        for (Object o : w.entities) {
            Entity e = (Entity) o;
            double dx = e.x - sx, dz = e.z - sz;
            if (dx * dx + dz * dz > FinderSettings.foodRadius * FinderSettings.foodRadius) continue;
            if (e instanceof ChickenEntity) { chickens++; food++; }
            else if (e instanceof PigEntity || e instanceof CowEntity) food++;
        }
        if (food < FinderSettings.minFood) return "no: food " + food;
        int sheep = countSheep(w, sx, sz);
        if (FinderSettings.minSheep > 0 && sheep < FinderSettings.minSheep) {
            // sheep can be further out than the terrain read so far: generate (and populate) the rest of the circle
            area.ensure(FinderSettings.sheepRadius);
            sheep = countSheep(w, sx, sz);
        }
        if (sheep < FinderSettings.minSheep) return "no: sheep " + sheep;
        return "OK Plains, spawn " + sx + " " + sz + ", desert " + desertPct + "%, plains " + plainsPct + "%, desert path " + desertDist + " blocks, clear run " + run[0] + " blocks (desert at " + run[1] + "), sheep " + sheep
                + ", food " + food + " (" + chickens + " chickens), trees " + logs + ", lava " + lavaDist + " blocks"
                + (caveDist >= 0 ? ", cave " + caveDist + " blocks (" + caveIron + " iron)" : "") + (towerDist >= 0 ? ", dig spot " + towerDist + " blocks" : "") + ", fortress "
                + String.format(java.util.Locale.ROOT, "%.1f", fortress) + " chunks";
    }

    private static int countSheep(World w, int sx, int sz) {
        int sheep = 0;
        for (Object o : w.entities) {
            Entity e = (Entity) o;
            double dx = e.x - sx, dz = e.z - sz;
            if (e instanceof SheepEntity && dx * dx + dz * dz <= FinderSettings.sheepRadius * FinderSettings.sheepRadius) sheep++;
        }
        return sheep;
    }

    /** Biomes and fortress from the seed alone (milliseconds), so only promising seeds get a world. */
    public static boolean worthMaking(long seed) {
        FastBiomes biomes = new FastBiomes(seed);
        int[] g = biomes.spawnGuess(seed);
        if (biomes.getBiome(g[0], g[1]) != Biome.PLAINS) return false;
        int desert = 0, samples = 0;
        int dr = FinderSettings.desertRadius;
        for (int x = g[0] - dr; x <= g[0] + dr; x += 16) {
            for (int z = g[1] - dr; z <= g[1] + dr; z += 16) {
                samples++;
                if (biomes.getBiome(x, z) == Biome.DESERT) desert++;
            }
        }
        if (desert * 100 / samples < FinderSettings.desertMinPercent - 2) return false; // the spawn moves a little, so a bit looser here
        if (openDesert(biomes, g[0], g[1]) == null) return false;
        if (!Terrain.biomesOk(biomes, g[0], g[1], 16)) return false;
        return fortress(seed, (g[0] >> 3) >> 4, (g[1] >> 3) >> 4)[0] <= FinderSettings.fortressMaxChunks + 0.5;
    }

    /**
     * {distance in chunks, start chunk x, start chunk z} of the fortress nearest nether chunk (cx, cz), from 1.0's
     * FortressStructure.isFeatureChunk: one candidate per 16x16-chunk region, at a random chunk 4 to 11 in from its
     * corner, kept 1 time in 3.
     */
    public static double[] fortress(long worldSeed, int cx, int cz) {
        double[] best = {1e9, 0, 0};
        Random r = new Random();
        for (int rx = (cx >> 4) - 1; rx <= (cx >> 4) + 1; rx++) {
            for (int rz = (cz >> 4) - 1; rz <= (cz >> 4) + 1; rz++) {
                r.setSeed((long) (rx ^ rz << 4) ^ worldSeed);
                r.nextInt();
                if (r.nextInt(3) != 0) continue;
                int fx = (rx << 4) + 4 + r.nextInt(8);
                int fz = (rz << 4) + 4 + r.nextInt(8);
                double d = Math.sqrt((double) (fx - cx) * (fx - cx) + (double) (fz - cz) * (fz - cz));
                if (d < best[0]) best = new double[] {d, fx, fz};
            }
        }
        return best;
    }

    /** Lava blocks showing at the surface within 6 blocks of (x, z): a pool, not one stray block. */
    private static int lavaPool(Area a, int x, int z) {
        int n = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int id = top(a.w, x + dx, z + dz);
                if (id == 10 || id == 11) n++;
            }
        }
        return n;
    }

    /**
     * Distance to the nearest desert that spawn can walk to over open ground: every biome on the straight line there
     * (sampled every 4 blocks) is plains, desert or river. -1 if there's none within desertPathRadius.
     */
    private static int[] openDesert(BiomeSource biomes, int sx, int sz) {
        int r = FinderSettings.desertPathRadius;
        for (int d = 8; d <= r; d += 8) {
            for (int x = sx - d; x <= sx + d; x += 8) {
                for (int z = sz - d; z <= sz + d; z += 8) {
                    if (Math.max(Math.abs(x - sx), Math.abs(z - sz)) != d) continue;
                    if (biomes.getBiome(x, z) != Biome.DESERT) continue;
                    double len = Math.sqrt((double) (x - sx) * (x - sx) + (double) (z - sz) * (z - sz));
                    if (len > r) continue;
                    boolean open = true;
                    int steps = (int) (len / 4);
                    for (int i = 1; i < steps && open; i++) {
                        Biome b = biomes.getBiome(sx + (x - sx) * i / steps, sz + (z - sz) * i / steps);
                        open = b == Biome.PLAINS || b == Biome.DESERT || b == Biome.RIVER;
                    }
                    if (open) return new int[] {(int) len, x, z};
                }
            }
        }
        return null;
    }

    /**
     * A spot in the desert near where the open path reaches it, size x size blocks of desert, where digging straight
     * down from the surface for depth blocks never opens into a cave (air), water or lava. Tries a few spots going into
     * the desert; returns the distance from spawn to the first good one, or -1.
     */
    private static int towerSpot(Area a, int[] desert, int size, int depth) {
        double dx = desert[1] - a.sx, dz = desert[2] - a.sz, len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        dx /= len;
        dz /= len;
        World w = a.w;
        for (int along = 8; along <= 40; along += 8) {
            for (int side = -16; side <= 16; side += 16) {
                int cx = (int) Math.round(desert[1] + dx * along - dz * side), cz = (int) Math.round(desert[2] + dz * along + dx * side);
                if (clean(w, cx, cz, size, depth)) return (int) Math.sqrt((cx - a.sx) * (cx - a.sx) + (cz - a.sz) * (cz - a.sz));
            }
        }
        return -1;
    }

    private static boolean clean(World w, int cx, int cz, int size, int depth) {
        int h = size / 2;
        for (int x = cx - h; x < cx - h + size; x++) {
            for (int z = cz - h; z < cz - h + size; z++) {
                if (w.getBiomeSource().getBiome(x, z) != Biome.DESERT) return false;
                int top = height(w, x, z) - 1;
                for (int y = top - 1; y >= Math.max(1, top - depth); y--) {
                    int id = w.getBlock(x, y, z);
                    if (id == 0 || id == 10 || id == 11) {
                        if (Boolean.getBoolean("ranked.finderDebug")) System.out.println("[finder] dig spot " + cx + " " + cz + ": block " + id + " at " + x + " " + y + " " + z + " (surface " + top + ")");
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Distance to the nearest cave open to the sky within r blocks, or -1. An opening is a column whose ground is at
     * least 5 blocks below the ground 6 blocks away on all four sides (a pit, not a hillside), with no water or lava
     * in it, and with air running sideways under the ground next to it (a tunnel, not a dip in the terrain).
     */
    private static int[] cave(Area a, int r) {
        a.ensure(r + CAVE_REACH);
        int[] best = null;
        World w = a.w;
        for (int d = 0; d <= r; d += 4) {
            for (int x = a.sx - d; x <= a.sx + d; x += 2) {
                for (int z = a.sz - d; z <= a.sz + d; z += 2) {
                    if (Math.max(Math.abs(x - a.sx), Math.abs(z - a.sz)) < d - 4) continue;
                    int g = ground(w, x, z);
                    if (g < 10) continue;
                    int id = w.getBlock(x, g, z);
                    if (id == 8 || id == 9 || id == 10 || id == 11 || id == 79) continue;
                    if (Math.min(Math.min(ground(w, x + 6, z), ground(w, x - 6, z)), Math.min(ground(w, x, z + 6), ground(w, x, z - 6))) < g + 5) continue;
                    // a tunnel: air one block above this floor, under solid ground, a few blocks to one side
                    boolean tunnel = false;
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] dir : dirs) {
                        int tx = x + dir[0] * 4, tz = z + dir[1] * 4;
                        if (w.getBlock(tx, g + 1, tz) == 0 && w.getBlock(tx, g + 2, tz) == 0 && ground(w, tx, tz) >= g + 4) tunnel = true;
                    }
                    if (!tunnel) continue;
                    int dist = (int) Math.sqrt((x - a.sx) * (x - a.sx) + (z - a.sz) * (z - a.sz));
                    if (dist > r) continue;
                    int iron = caveIron(w, x, g + 1, z);
                    if (best == null || iron > best[1]) best = new int[] {dist, iron};
                    if (iron >= FinderSettings.caveIron) return best;
                }
            }
        }
        return best;
    }

    /** How far a cave is followed from its opening, in blocks either way. */
    private static final int CAVE_REACH = 40;

    /**
     * Iron ore you can see from inside the cave that opens at (x, y, z): follows the connected air underground (at
     * most CAVE_REACH blocks sideways from the opening) and counts the iron ore blocks touching it.
     */
    private static int caveIron(World w, int ox, int oy, int oz) {
        java.util.HashSet<Long> seen = new java.util.HashSet<>(), iron = new java.util.HashSet<>();
        java.util.HashMap<Long, Integer> groundAt = new java.util.HashMap<>();
        java.util.ArrayDeque<int[]> todo = new java.util.ArrayDeque<>();
        todo.add(new int[] {ox, oy, oz});
        seen.add(key(ox, oy, oz));
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!todo.isEmpty() && seen.size() < 30000) {
            int[] p = todo.poll();
            for (int[] d : dirs) {
                int x = p[0] + d[0], y = p[1] + d[1], z = p[2] + d[2];
                if (y < 1 || y > 126 || Math.abs(x - ox) > CAVE_REACH || Math.abs(z - oz) > CAVE_REACH) continue;
                int id = w.getBlock(x, y, z);
                if (id == 15) {
                    iron.add(key(x, y, z));
                    continue;
                }
                if (id != 0) continue;
                // stay underground: under the ground of its column, or in the opening's own pit
                long col = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                Integer g = groundAt.get(col);
                if (g == null) groundAt.put(col, g = ground(w, x, z));
                if (y > oy + 1 && y >= g) continue;
                if (seen.add(key(x, y, z))) todo.add(new int[] {x, y, z});
            }
        }
        return iron.size();
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The top block that's ground: skips air, leaves, logs, snow and plants. */
    private static int ground(World w, int x, int z) {
        int y = height(w, x, z) - 1;
        while (y > 0) {
            int id = w.getBlock(x, y, z);
            if (id != 0 && id != 17 && id != 18 && id != 31 && id != 32 && id != 37 && id != 38 && id != 39 && id != 40 && id != 78 && id != 81 && id != 83 && id != 106) break;
            y--;
        }
        return y;
    }

    /** World.getHeight answers 0 for a chunk that isn't loaded, so load (generate) it first. */
    private static int height(World w, int x, int z) {
        w.getChunkAt(x >> 4, z >> 4);
        return w.getHeight(x, z);
    }

    private static int top(World w, int x, int z) {
        int h = height(w, x, z);
        return h <= 0 ? 0 : w.getBlock(x, h - 1, z);
    }
}
