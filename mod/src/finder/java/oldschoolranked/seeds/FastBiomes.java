package oldschoolranked.seeds;

import oldschoolranked.finder.BiomeSourceAccessor;

import java.util.Random;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.layer.Layer;
import net.minecraft.world.biome.source.BiomeSource;

/**
 * A seed's biomes without making a world: the same layers BiomeSource(World) builds, from the seed alone. Used to skip
 * seeds that can't pass before paying for world creation.
 */
public final class FastBiomes extends BiomeSource {
    public FastBiomes(long seed) {
        super();
        Layer[] l = Layer.init(seed);
        BiomeSourceAccessor a = (BiomeSourceAccessor) (Object) this;
        a.ranked$setNoiseBiomes(l[0]);
        a.ranked$setBiomes(l[1]);
        a.ranked$setTemperatures(l[2]);
        a.ranked$setDownfalls(l[3]);
    }

    /** Where 1.0 starts looking for the spawn point (World.init): the spawn ends up at or near here. */
    public int[] spawnGuess(long seed) {
        BlockPos p = findBiome(0, 0, 256, getBiomesForSpawnPoint(), new Random(seed));
        return p == null ? new int[] {0, 0} : new int[] {p.x, p.z};
    }
}
