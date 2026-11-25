package oldschoolranked.finder;

import net.minecraft.util.ProgressListener;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Finder worlds are thrown away, so they're never saved (saving was most of the time per world). */
@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "forceSave", at = @At("HEAD"), cancellable = true)
    private void ranked$noForceSave(ProgressListener listener, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void ranked$noSave(boolean all, ProgressListener listener, CallbackInfo ci) {
        if (Integer.getInteger("ranked.exportRadius", 0) > 0) return; // exporting a world to look at
        ci.cancel();
    }
}
