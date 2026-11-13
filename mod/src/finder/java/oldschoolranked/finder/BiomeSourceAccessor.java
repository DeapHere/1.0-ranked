package oldschoolranked.finder;

import net.minecraft.world.biome.layer.Layer;
import net.minecraft.world.biome.source.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeSource.class)
public interface BiomeSourceAccessor {
    @Accessor("noiseBiomes")
    void ranked$setNoiseBiomes(Layer l);

    @Accessor("biomes")
    void ranked$setBiomes(Layer l);

    @Accessor("temperatures")
    void ranked$setTemperatures(Layer l);

    @Accessor("downfalls")
    void ranked$setDownfalls(Layer l);
}
