package oldschoolranked.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Arrays;
import net.minecraft.world.biome.layer.Layer;
import net.minecraft.world.biome.source.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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
}
