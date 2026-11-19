package oldschoolranked.mixin;

import net.minecraft.client.options.GameOptions;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Peaceful in a match: cycling the difficulty button onto Peaceful lands on Easy instead. Easy, Normal and Hard stay free to pick. */
@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
    @Shadow public int difficulty;

    @Inject(method = "set(Lnet/minecraft/client/options/GameOptions$Option;I)V", at = @At("TAIL"))
    private void ranked$noPeaceful(GameOptions.Option option, int value, CallbackInfo ci) {
        if (option == GameOptions.Option.DIFFICULTY && difficulty == 0 && Ranked.inMatch()) difficulty = 1;
    }
}
