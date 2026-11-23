package oldschoolranked.mixin;

import net.minecraft.entity.EnderEyeEntity;
import oldschoolranked.MatchSetup;
import oldschoolranked.Standard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In match worlds the first two eyes of ender thrown always drop back as items instead of breaking. */
@Mixin(EnderEyeEntity.class)
public abstract class EnderEyeEntityMixin {
    @Shadow private boolean dropsItem;

    @Inject(method = "setTarget", at = @At("TAIL"))
    private void ranked$safeFirstEyes(double x, int y, double z, CallbackInfo ci) {
        if (!Standard.noDesertCaves) return;
        if (MatchSetup.eyesThrown++ < MatchSetup.SAFE_EYES) dropsItem = true;
    }
}
