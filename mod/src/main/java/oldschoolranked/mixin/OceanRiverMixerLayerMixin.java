package oldschoolranked.mixin;

import net.minecraft.world.biome.layer.Layer;
import net.minecraft.world.biome.layer.OceanRiverMixerLayer;
import oldschoolranked.RiverFree;
import oldschoolranked.Standard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No rivers near spawn in match worlds (and the seed finder's): within RiverFree.RADIUS blocks of where 1.0 starts
 * its spawn search, river cells take the biome that was there before rivers were cut in, so the terrain generator
 * shapes them like the land around them. Rivers further out, oceans, lakes and swamps are unchanged. Spawn and
 * stronghold placement still see every river (Standard.placingWithRivers), so they stay where 1.0 puts them.
 * This layer works at 1:4 scale, so cell (x, z) covers blocks 4x to 4x+3.
 */
@Mixin(OceanRiverMixerLayer.class)
public abstract class OceanRiverMixerLayerMixin implements RiverFree {
    @Shadow private Layer biomeLayer;
    @Unique private volatile boolean ranked$hasCenter;
    @Unique private int ranked$cx, ranked$cz;

    @Override
    public void ranked$setRiverFreeCenter(int blockX, int blockZ) {
        ranked$cx = blockX;
        ranked$cz = blockZ;
        ranked$hasCenter = true;
    }

    @Inject(method = "getArea", at = @At("RETURN"))
    private void ranked$noRiversNearSpawn(int x, int z, int width, int height, CallbackInfoReturnable<int[]> cir) {
        if (!ranked$hasCenter || !Standard.noDesertCaves || Standard.placingWithRivers.get()[0] != 0) return;
        int r = RiverFree.RADIUS / 4, cx = ranked$cx >> 2, cz = ranked$cz >> 2;
        if (x > cx + r || z > cz + r || x + width < cx - r || z + height < cz - r) return;
        int[] out = cir.getReturnValue(), base = null;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int dx = x + i - cx, dz = z + j - cz;
                if (dx * dx + dz * dz > r * r) continue;
                int k = i + j * width, v = out[k];
                if (v != net.minecraft.world.biome.Biome.RIVER.id && v != net.minecraft.world.biome.Biome.FROZEN_RIVER.id && v != net.minecraft.world.biome.Biome.MUSHROOM_ISLAND_SHORE.id) continue;
                if (base == null) base = biomeLayer.getArea(x, z, width, height);
                out[k] = base[k];
            }
        }
    }
}
