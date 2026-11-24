package oldschoolranked.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Arrays;
import net.minecraft.world.biome.layer.Layer;
import net.minecraft.world.biome.source.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeSource.class)
public abstract class BiomeSourceMixin {
    /**
     * Vanilla 1.0 bug: findBiome (world spawn and stronghold placement) loops over the whole array the biome layers
     * hand back, which is a pooled array usually longer than the area asked for. The rest holds leftovers from earlier
     * generation, which can crash the game or move the spawn and strongholds. Only the area asked for is looked at, so
     * a seed always gives both players the same world.
     *
     * WrapOperation rather than Redirect, so it stacks with OldschoolPrac's fix for the same call.
     */
    @WrapOperation(method = "findBiome", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/layer/Layer;getArea(IIII)[I"))
    private int[] ranked$onlyTheArea(Layer layer, int x, int z, int width, int height, Operation<int[]> original) {
        int[] area = original.call(layer, x, z, width, height);
        return area.length <= width * height ? area : Arrays.copyOf(area, width * height);
    }

    /** Spawn and stronghold placement see 1.0's rivers (see OceanRiverMixerLayerMixin). */
    @Inject(method = "findBiome", at = @At("HEAD"))
    private void ranked$withRiversIn(int x, int z, int radius, java.util.List<?> biomes, java.util.Random random, CallbackInfoReturnable<?> cir) {
        oldschoolranked.Standard.placingWithRivers.get()[0]++;
    }

    @Shadow private Layer noiseBiomes;
    @Shadow private java.util.List<net.minecraft.world.biome.Biome> biomesForSpawnPoint;
    @org.spongepowered.asm.mixin.Unique private boolean ranked$centerSet;

    @Inject(method = "findBiome", at = @At("RETURN"))
    private void ranked$withRiversOut(int x, int z, int radius, java.util.List<?> biomes, java.util.Random random, CallbackInfoReturnable<net.minecraft.util.math.BlockPos> cir) {
        oldschoolranked.Standard.placingWithRivers.get()[0]--;
        // the first spawn search fixes where the river-free area goes (before any terrain is generated)
        if (!ranked$centerSet && biomes == biomesForSpawnPoint && noiseBiomes instanceof oldschoolranked.RiverFree) {
            ranked$centerSet = true;
            net.minecraft.util.math.BlockPos p = cir.getReturnValue();
            ((oldschoolranked.RiverFree) noiseBiomes).ranked$setRiverFreeCenter(p == null ? x : p.x, p == null ? z : p.z);
        }
    }
}
