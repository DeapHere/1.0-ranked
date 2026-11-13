package oldschoolranked.mixin;

import java.util.List;
import net.minecraft.world.biome.IntCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** IntCache's static array pool, so each match world can start with an empty pool like the first world after launch. */
@Mixin(IntCache.class)
public interface IntCacheAccessor {
    @Accessor("maxSize")
    static void ranked$setMaxSize(int size) {
        throw new AssertionError();
    }

    @Accessor("tcache")
    static void ranked$setTcache(List<int[]> list) {
        throw new AssertionError();
    }

    @Accessor("tallocated")
    static void ranked$setTallocated(List<int[]> list) {
        throw new AssertionError();
    }

    @Accessor("cache")
    static void ranked$setCache(List<int[]> list) {
        throw new AssertionError();
    }

    @Accessor("allocated")
    static void ranked$setAllocated(List<int[]> list) {
        throw new AssertionError();
    }
}
