package oldschoolranked.mixin;

import net.minecraft.world.World;
import net.minecraft.world.WorldData;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No rain or thunder in match worlds. Weather in 1.0 is random rather than from the seed, so one player could get a
 * storm (darker, more mobs, slower to see) while the other gets clear skies.
 */
@Mixin(World.class)
public abstract class WorldMixin {
    @Shadow protected WorldData data;
    @Shadow protected float prevRain;
    @Shadow protected float rain;
    @Shadow protected float prevThunder;
    @Shadow protected float thunder;

    @Inject(method = "tickWeather", at = @At("HEAD"), cancellable = true)
    private void ranked$noWeather(CallbackInfo ci) {
        if (!Ranked.inMatchWorld() || data == null) return;
        data.setRaining(false);
        data.setThundering(false);
        data.setRainTime(Integer.MAX_VALUE / 2);
        data.setThunderTime(Integer.MAX_VALUE / 2);
        prevRain = rain = prevThunder = thunder = 0;
        ci.cancel();
    }
}
