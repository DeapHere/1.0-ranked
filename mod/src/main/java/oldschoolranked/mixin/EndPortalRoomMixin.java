package oldschoolranked.mixin;

import java.util.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.StrongholdPieces;
import net.minecraft.world.gen.structure.StructureBox;
import net.minecraft.world.gen.structure.StructurePiece;
import oldschoolranked.Standard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Match worlds: a portal room under the ocean is always open to the sea floor, so it can be seen from the water. The
 * room's ceiling and the ground above it, up to the sea, are turned into water.
 */
@Mixin(StrongholdPieces.EndPortalRoom.class)
public abstract class EndPortalRoomMixin extends StructurePiece {
    protected EndPortalRoomMixin(int depth) {
        super(depth);
    }

    @Inject(method = "postProcess", at = @At("RETURN"))
    private void ranked$openToOcean(World w, Random random, StructureBox clip, CallbackInfoReturnable<Boolean> cir) {
        if (!Standard.noDesertCaves || !Boolean.TRUE.equals(cir.getReturnValue())) return; // match worlds only
        int cx = bounds.getCenterX(), cz = bounds.getCenterZ();
        Biome b = w.getBiomeSource().getBiome(cx, cz);
        if (b != Biome.OCEAN && b != Biome.FROZEN_OCEAN) return;
        // the whole ceiling (not the walls), so the portal is open to the sea whichever way the room faces
        for (int x = bounds.minX + 1; x <= bounds.maxX - 1; x++) {
            for (int z = bounds.minZ + 1; z <= bounds.maxZ - 1; z++) {
                if (!clip.contains(x, bounds.maxY, z)) continue;
                int top = -1;
                for (int y = bounds.maxY; y < 128; y++) {
                    int id = w.getBlock(x, y, z);
                    if (id == 8 || id == 9) {
                        top = y;
                        break;
                    }
                    if (id == 0) break; // open air before any water: not under the sea here
                }
                if (top < 0) continue;
                for (int y = bounds.maxY; y < top; y++) {
                    if (clip.contains(x, y, z)) w.setBlock(x, y, z, 9);
                }
            }
        }
    }
}
