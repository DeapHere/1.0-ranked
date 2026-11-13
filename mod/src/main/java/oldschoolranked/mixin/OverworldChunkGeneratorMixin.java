package oldschoolranked.mixin;

import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.gen.Generator;
import net.minecraft.world.gen.chunk.OverworldChunkGenerator;
import net.minecraft.world.gen.structure.MineshaftStructure;
import java.util.Random;
import oldschoolranked.Standard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * No caves, ravines or mineshafts in desert chunks of match worlds: towers in the desert can dig straight down. The
 * chunk's biomes are the ones the generator just worked out for its surface.
 */
@Mixin(OverworldChunkGenerator.class)
public abstract class OverworldChunkGeneratorMixin {
    @Shadow private Biome[] biomes;

    @Redirect(method = "getChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/Generator;place(Lnet/minecraft/world/chunk/ChunkSource;Lnet/minecraft/world/World;II[B)V", ordinal = 0))
    private void ranked$caves(Generator carver, ChunkSource source, World world, int x, int z, byte[] blocks) {
        if (!skip()) carver.place(source, world, x, z, blocks);
    }

    @Redirect(method = "getChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/Generator;place(Lnet/minecraft/world/chunk/ChunkSource;Lnet/minecraft/world/World;II[B)V", ordinal = 1))
    private void ranked$ravines(Generator carver, ChunkSource source, World world, int x, int z, byte[] blocks) {
        if (!skip()) carver.place(source, world, x, z, blocks);
    }

    // mineshaft corridors are placed while populating, over the 16x16 area offset by 8
    @Redirect(method = "populateChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/structure/MineshaftStructure;place(Lnet/minecraft/world/World;Ljava/util/Random;II)Z"))
    private boolean ranked$mineshafts(MineshaftStructure mineshafts, World world, Random random, int x, int z) {
        if (Standard.noDesertCaves) {
            int bx = x * 16 + 8, bz = z * 16 + 8;
            for (int dx = 0; dx <= 16; dx += 8) {
                for (int dz = 0; dz <= 16; dz += 8) {
                    if (world.getBiomeSource().getBiome(bx + dx, bz + dz) == Biome.DESERT) return false;
                }
            }
        }
        return mineshafts.place(world, random, x, z);
    }

    private boolean skip() {
        if (!Standard.noDesertCaves || this.biomes == null) return false;
        for (Biome b : this.biomes) if (b == Biome.DESERT) return true;
        return false;
    }
}
