package oldschoolranked.mixin;

import net.minecraft.client.entity.mob.player.ClientPlayerEntity;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat typed in game (T) during a two-player match goes to your opponent through the match server. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void ranked$matchChat(String text, CallbackInfo ci) {
        if (!Ranked.canChat()) return;
        Ranked.sendChat(text);
        ci.cancel();
    }
}
