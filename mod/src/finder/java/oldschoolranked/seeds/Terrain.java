package oldschoolranked.seeds;

import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/** How wet and hilly the land around spawn is (for culling the pool). */
public final class Terrain {
    private Terrain() {
    }

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

    /** No Extreme Hills within hillsRadius, and not too much river within runLength (slack: extra room for a spawn guess). */
    public static boolean biomesOk(net.minecraft.world.biome.source.BiomeSource b, int sx, int sz, int slack) {
        int hr = FinderSettings.hillsRadius;
        if (hr > 0) {
            for (int x = sx - hr; x <= sx + hr; x += 8) {
                for (int z = sz - hr; z <= sz + hr; z += 8) {
                    if ((x - sx) * (x - sx) + (z - sz) * (z - sz) > (hr - slack) * (hr - slack)) continue;
                    Biome bi = b.getBiome(x, z);
                    if (bi == Biome.EXTREME_HILLS || bi == Biome.ICE_MOUNTAINS) return false;
                }
            }
        }
        if (FinderSettings.riverMaxPercent < 100) {
            int r = FinderSettings.runLength, river = 0, n = 0;
            for (int x = sx - r; x <= sx + r; x += 8) {
                for (int z = sz - r; z <= sz + r; z += 8) {
                    if ((x - sx) * (x - sx) + (z - sz) * (z - sz) > r * r) continue;
                    n++;
                    if (b.getBiome(x, z) == Biome.RIVER) river++;
                }
            }
            if (river * 100 / n > FinderSettings.riverMaxPercent + (slack > 0 ? 1 : 0)) return false;
        }
        return true;
    }

    /** Generates and decorates the chunks around block (x, z), so lakes are there too. */
    private static void around(World w, int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) w.getChunkAt(cx + i, cz + j);
    }

    private static boolean wet(int id) {
        return id == 8 || id == 9 || id == 79;
    }

    /**
     * The run toward the desert: of 64 directions from spawn whose biomes are only plains until they reach desert within
     * desertPathRadius, the first (nearest desert) where a strip 2*runWidth+1 wide, out to runLength blocks or 48 blocks
     * into the desert, has no water and climbs at most maxClimb blocks along its middle. {run length, desert distance},
     * or null if no direction works. Only the chunks along the tried strips get generated.
     */
    public static int[] desertRun(World w, int sx, int sz) {
        net.minecraft.world.biome.source.BiomeSource b = w.getBiomeSource();
        java.util.List<int[]> dirs = new java.util.ArrayList<>();
        for (int d = 0; d < 64; d++) {
            double a = d * Math.PI * 2 / 64, ca = Math.cos(a), sa = Math.sin(a);
            for (int t = 8; t <= FinderSettings.desertPathRadius; t += 4) {
                Biome bi = b.getBiome(sx + (int) Math.round(ca * t), sz + (int) Math.round(sa * t));
                if (bi == Biome.DESERT) { dirs.add(new int[] {d, t}); break; }
                if (bi != Biome.PLAINS) break;
            }
        }
        dirs.sort((p, q) -> Integer.compare(p[1], q[1]));
        int rw = FinderSettings.runWidth;
        for (int[] dir : dirs) {
            double a = dir[0] * Math.PI * 2 / 64, ca = Math.cos(a), sa = Math.sin(a);
            int len = Math.max(FinderSettings.runLength, dir[1] + 48);
            boolean ok = true;
            int climb = 0, prev = -1;
            for (int t = 2; t <= len && ok; t += 2) {
                for (int side = -rw; side <= rw && ok; side++) {
                    int x = sx + (int) Math.round(ca * t - sa * side), z = sz + (int) Math.round(sa * t + ca * side);
                    around(w, x, z);
                    int g = ground(w, x, z);
                    if (wet(w.getBlock(x, g, z))) ok = false;
                    if (side == 0) {
                        if (prev >= 0) climb += Math.max(0, Math.abs(g - prev) - 1);
                        prev = g;
                        if (climb > FinderSettings.maxClimb) ok = false;
                    }
                }
            }
            if (ok) return new int[] {len, dir[1]};
        }
        return null;
    }

    /** Null if the land around spawn is fine, or why not: too much water close by, or not enough clear runs. */
    public static String check(World w, int sx, int sz) {
        if (FinderSettings.nearWaterMaxPercent < 100) {
            int r = FinderSettings.nearWaterRadius, n = 0, water = 0;
            for (int x = sx - r; x <= sx + r; x += 4) {
                for (int z = sz - r; z <= sz + r; z += 4) {
                    if ((x - sx) * (x - sx) + (z - sz) * (z - sz) > r * r) continue;
                    n++;
                    around(w, x, z);
                    int g = ground(w, x, z), id = w.getBlock(x, g, z);
                    if (id == 8 || id == 9 || id == 79) water++;
                }
            }
            if (water * 100 / n > FinderSettings.nearWaterMaxPercent) return "no: water near spawn " + water * 100 / n + "%";
        }
        if (FinderSettings.highMaxPercent < 100 || FinderSettings.maxSpread < 99) {
            int r = 100, n = 0, high = 0;
            double sum = 0, sq = 0;
            for (int x = sx - r; x <= sx + r; x += 4) {
                for (int z = sz - r; z <= sz + r; z += 4) {
                    if ((x - sx) * (x - sx) + (z - sz) * (z - sz) > r * r) continue;
                    n++;
                    int g = ground(w, x, z);
                    if (g >= 78) high++;
                    sum += g;
                    sq += (double) g * g;
                }
            }
            double mean = sum / n, spread = Math.sqrt(Math.max(0, sq / n - mean * mean));
            if (high * 100 / n > FinderSettings.highMaxPercent) return "no: high ground " + high * 100 / n + "%";
            if (spread > FinderSettings.maxSpread) return String.format(java.util.Locale.ROOT, "no: uneven ground %.1f", spread);
        }
        if (FinderSettings.minRuns > 0) {
            int runs = runs(w, sx, sz, FinderSettings.runLength)[1];
            if (runs < FinderSettings.minRuns) return "no: clear runs " + runs;
        }
        return null;
    }

    /** {dry, dry and flat} of 32 straight lines from spawn, len blocks long. */
    static int[] runs(World w, int sx, int sz, int len) {
        int clear = 0, flat = 0;
        for (int d = 0; d < 32; d++) {
            double a = d * Math.PI * 2 / 32;
            boolean wet = false;
            int climb = 0, prev = ground(w, sx, sz);
            for (int t = 4; t <= len; t += 2) {
                int x = sx + (int) Math.round(Math.cos(a) * t), z = sz + (int) Math.round(Math.sin(a) * t);
                int g = ground(w, x, z);
                int id = w.getBlock(x, g, z);
                if (id == 8 || id == 9 || id == 79) { wet = true; break; }
                climb += Math.max(0, Math.abs(g - prev) - 1);
                prev = g;
            }
            if (!wet) {
                clear++;
                if (climb <= 12) flat++;
            }
        }
        return new int[] {clear, flat};
    }

    /** "water48=% water100=% std100= high100=% range100= hills100=% bump48=": every 4 blocks out to 100. */
    public static String metrics(World w, int sx, int sz) {
        int[] rs = {48, 100};
        StringBuilder sb = new StringBuilder();
        for (int r : rs) {
            int n = 0, water = 0, high = 0, hills = 0, min = 999, max = -1;
            double sum = 0, sq = 0, bump = 0;
            for (int x = sx - r; x <= sx + r; x += 4) {
                for (int z = sz - r; z <= sz + r; z += 4) {
                    if ((x - sx) * (x - sx) + (z - sz) * (z - sz) > r * r) continue;
                    n++;
                    int g = ground(w, x, z);
                    int id = w.getBlock(x, g, z);
                    if (id == 8 || id == 9 || id == 79) water++;
                    if (g >= 78) high++;
                    Biome b = w.getBiomeSource().getBiome(x, z);
                    if (b == Biome.EXTREME_HILLS || b == Biome.ICE_MOUNTAINS) hills++;
                    min = Math.min(min, g);
                    max = Math.max(max, g);
                    sum += g;
                    sq += (double) g * g;
                    // roughness: height change to the sample 4 blocks east
                    bump += Math.abs(ground(w, x + 4, z) - g);
                }
            }
            double mean = sum / n, std = Math.sqrt(Math.max(0, sq / n - mean * mean));
            sb.append(String.format(java.util.Locale.ROOT, "water%d=%d std%d=%.1f high%d=%d range%d=%d hills%d=%d bump%d=%.2f ",
                    r, water * 100 / n, r, std, r, high * 100 / n, r, max - min, r, hills * 100 / n, r, bump / n));
        }
        // the 205 block run: of 32 straight lines out from spawn to 210 blocks, how many cross no water and no steep climbs,
        // and how much river there is in that circle
        int clear = 0, flat = 0;
        for (int d = 0; d < 32; d++) {
            double a = d * Math.PI * 2 / 32;
            boolean wet = false;
            int climb = 0, prev = ground(w, sx, sz);
            for (int t = 4; t <= 210; t += 2) {
                int x = sx + (int) Math.round(Math.cos(a) * t), z = sz + (int) Math.round(Math.sin(a) * t);
                int g = ground(w, x, z);
                int id = w.getBlock(x, g, z);
                if (id == 8 || id == 9 || id == 79) { wet = true; break; }
                climb += Math.max(0, Math.abs(g - prev) - 1);
                prev = g;
            }
            if (!wet) {
                clear++;
                if (climb <= 12) flat++;
            }
        }
        int river = 0, n = 0;
        for (int x = sx - 210; x <= sx + 210; x += 8) {
            for (int z = sz - 210; z <= sz + 210; z += 8) {
                if ((x - sx) * (x - sx) + (z - sz) * (z - sz) > 210 * 210) continue;
                n++;
                if (w.getBiomeSource().getBiome(x, z) == Biome.RIVER) river++;
            }
        }
        sb.append(String.format(java.util.Locale.ROOT, "dry210=%d flat210=%d river210=%d", clear, flat, river * 100 / n));
        return sb.toString().trim();
    }
}
