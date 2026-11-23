package oldschoolranked.mixin;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StrongholdPieces;
import net.minecraft.world.gen.structure.StructureBox;
import net.minecraft.world.gen.structure.StructurePiece;
import oldschoolranked.Standard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No disconnected portal rooms in match worlds. In 1.0 every stronghold piece except the portal room skips itself when
 * it touches water or lava, so the corridor leading to the room can be missing and the room ends up sealed in stone.
 * It also depends on what had generated around it first, so two players could get different strongholds. Here every
 * stronghold piece always generates.
 */
@Mixin(StructurePiece.class)
public abstract class StructurePieceMixin {
    /** Every stronghold piece is a nested class of StrongholdPieces (the same in dev and in the released game). */
    private static final String STRONGHOLD = StrongholdPieces.class.getName() + "$";

    @Inject(method = "bordersOnLiquids", at = @At("HEAD"), cancellable = true)
    private void ranked$alwaysBuildStrongholds(World w, StructureBox box, CallbackInfoReturnable<Boolean> cir) {
        if (Standard.noDesertCaves && getClass().getName().startsWith(STRONGHOLD)) cir.setReturnValue(false);
    }
}
