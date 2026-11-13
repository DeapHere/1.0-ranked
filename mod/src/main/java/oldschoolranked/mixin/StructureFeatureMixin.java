package oldschoolranked.mixin;

import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StrongholdStructure;
import net.minecraft.world.gen.structure.StructureFeature;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla 1.0 bug: an eye of ender flies to the nearest stronghold that has already been generated this session, and
 * only looks at all three when none has. So once you've passed near a far one, eyes can lead you there instead of to
 * the closest. In match worlds the eye always goes to the closest of the three.
 */
@Mixin(StructureFeature.class)
public abstract class StructureFeatureMixin {
    @Shadow
    protected abstract List<BlockPos> getPotentialPositions();

    @Inject(method = "findNearestPosition", at = @At("RETURN"), cancellable = true)
    private void ranked$closestStronghold(World world, int x, int y, int z, CallbackInfoReturnable<BlockPos> cir) {
        if (!((Object) this instanceof StrongholdStructure) || !Ranked.inMatchWorld()) return;
        List<BlockPos> all = getPotentialPositions();
        if (all == null || all.isEmpty()) return;
        BlockPos best = null;
        long bestD = Long.MAX_VALUE;
        for (BlockPos p : all) {
            if (p == null) continue;
            long dx = p.x - x, dz = p.z - z, d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best == null) return;
        BlockPos found = cir.getReturnValue();
        // vanilla's answer is fine when it's that same stronghold (it aims at the actual stairs); otherwise use the closest
        if (found != null) {
            long dx = found.x - best.x, dz = found.z - best.z;
            if (dx * dx + dz * dz <= 128L * 128L) return;
        }
        cir.setReturnValue(best);
    }
}
