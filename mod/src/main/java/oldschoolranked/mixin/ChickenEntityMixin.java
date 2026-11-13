package oldschoolranked.mixin;

import net.minecraft.entity.mob.animal.ChickenEntity;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** In match worlds a chicken always drops at least one feather (vanilla 1.0: 0 to 2). Still up to 2. */
@Mixin(ChickenEntity.class)
public abstract class ChickenEntityMixin {
    @ModifyVariable(method = "dropItems", at = @At("STORE"), index = 3)
    private int ranked$atLeastOneFeather(int feathers) {
        return Ranked.inMatchWorld() ? Math.max(1, feathers) : feathers;
    }
}
