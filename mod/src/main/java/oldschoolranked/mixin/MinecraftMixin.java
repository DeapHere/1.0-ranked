package oldschoolranked.mixin;

import net.minecraft.client.Minecraft;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void ranked$tick(CallbackInfo ci) {
        Ranked.tick((Minecraft) (Object) this);
    }
}
