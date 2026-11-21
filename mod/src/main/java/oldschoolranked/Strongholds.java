package oldschoolranked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;

/**
 * Where the three strongholds go in a match world: 1.0's own placement (same ring, same random calls, all from the
 * seed) but never under a mountain. Extreme Hills and Ice Mountains are off the list of biomes it looks for, and if a
 * stronghold still lands with mountains around it, it moves to the nearest chunk with none, so nobody has to dig down
 * from y 100 to reach one.
 */
public final class Strongholds {
    private static final List<Biome> VALID = Arrays.asList(Biome.DESERT, Biome.FOREST, Biome.SWAMPLAND, Biome.TAIGA, Biome.ICE_PLAINS);

    private Strongholds() {
    }

    /** Chunk x, z of each stronghold. random is the structure's own, as 1.0 uses it. */
    public static int[][] place(BiomeSource biomes, Random random, long seed, int count) {
        random.setSeed(seed);
        double a = random.nextDouble() * Math.PI * 2;
        int[][] out = new int[count][];
        for (int i = 0; i < count; i++) {
            double d = (1.25 + random.nextDouble()) * 32;
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            BlockPos p = biomes.findBiome((x << 4) + 8, (z << 4) + 8, 112, new ArrayList<>(VALID), random);
            if (p != null) {
                x = p.x >> 4;
                z = p.z >> 4;
            }
            out[i] = awayFromMountains(biomes, x, z);
            a += Math.PI * 2 / count;
        }
        return out;
    }

    static boolean mountain(Biome b) {
        return b == Biome.EXTREME_HILLS || b == Biome.ICE_MOUNTAINS;
    }

    /** Mountains anywhere within 32 blocks of the middle of chunk (x, z). */
    public static boolean nearMountains(BiomeSource biomes, int x, int z) {
        int bx = (x << 4) + 8, bz = (z << 4) + 8;
        for (int dx = -32; dx <= 32; dx += 16) {
            for (int dz = -32; dz <= 32; dz += 16) {
                if (mountain(biomes.getBiome(bx + dx, bz + dz))) return true;
            }
        }
        return false;
    }

    private static int[] awayFromMountains(BiomeSource biomes, int x, int z) {
        if (!nearMountains(biomes, x, z)) return new int[] {x, z};
        for (int r = 1; r <= 16; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    if (!nearMountains(biomes, x + dx, z + dz)) return new int[] {x + dx, z + dz};
                }
            }
        }
        return new int[] {x, z};
    }
}
