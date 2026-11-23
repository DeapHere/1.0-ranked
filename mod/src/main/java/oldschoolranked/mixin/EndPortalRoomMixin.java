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
 * Match worlds: a portal room under the ocean, where the sea floor over it is below y 50, has the ground over its
 * roof cleared away, so the top of the room sticks out of the sea floor. The room itself isn't touched.
 */
@Mixin(StrongholdPieces.EndPortalRoom.class)
public abstract class EndPortalRoomMixin extends StructurePiece {
    /** Only dig the roof out when the sea floor over it is lower than this. */
    private static final int SEA_FLOOR_MAX = 50;

    protected EndPortalRoomMixin(int depth) {
        super(depth);
    }

    @Inject(method = "postProcess", at = @At("RETURN"))
    private void ranked$openToOcean(World w, Random random, StructureBox clip, CallbackInfoReturnable<Boolean> cir) {
        if (!Standard.noDesertCaves || !Boolean.TRUE.equals(cir.getReturnValue())) return; // match worlds only
        int cx = bounds.getCenterX(), cz = bounds.getCenterZ();
        Biome b = w.getBiomeSource().getBiome(cx, cz);
        if (b != Biome.OCEAN && b != Biome.FROZEN_OCEAN) return;
        // the room stays whole: only the ground over its roof goes, so the top layer of stone bricks shows on the sea
        // floor. Only where the sea floor over the room is below y 50.
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                if (!clip.contains(x, bounds.maxY, z)) continue;
                int water = -1;
                for (int y = bounds.maxY + 1; y < 128; y++) {
                    int id = w.getBlock(x, y, z);
                    if (id == 8 || id == 9) {
                        water = y;
                        break;
                    }
                    if (id == 0) break; // open air before any water: not under the sea here
                }
                if (water < 0 || water - 1 >= SEA_FLOOR_MAX) continue;
                for (int y = bounds.maxY + 1; y < water; y++) {
                    if (clip.contains(x, y, z)) w.setBlock(x, y, z, 9);
                }
            }
        }
    }
}
