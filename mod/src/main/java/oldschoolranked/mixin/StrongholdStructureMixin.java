package oldschoolranked.mixin;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.StrongholdStructure;
import net.minecraft.world.gen.structure.StructureFeature;
import oldschoolranked.Standard;
import oldschoolranked.Strongholds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No strongholds under mountains in match worlds: see Strongholds. Both players get the same three. */
@Mixin(StrongholdStructure.class)
public abstract class StrongholdStructureMixin extends StructureFeature {
    @Shadow
    private boolean positionsFound;

    @Shadow
    private ChunkPos[] positions;

    @Inject(method = "isFeatureChunk", at = @At("HEAD"))
    private void ranked$noMountains(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
        if (positionsFound || !Standard.noDesertCaves) return;
        int[][] p = Strongholds.place(world.getBiomeSource(), random, world.getSeed(), positions.length);
        for (int i = 0; i < p.length; i++) positions[i] = new ChunkPos(p[i][0], p[i][1]);
        positionsFound = true;
    }
}
