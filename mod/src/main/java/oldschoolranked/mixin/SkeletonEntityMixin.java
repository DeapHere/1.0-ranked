package oldschoolranked.mixin;

import net.minecraft.entity.mob.monster.SkeletonEntity;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** In match worlds a skeleton always drops at least one arrow (vanilla 1.0: 0 to 2). Bones are left alone. */
@Mixin(SkeletonEntity.class)
public abstract class SkeletonEntityMixin {
    // local 3 is stored twice: arrows first, then bones; only the first store is the arrow count
    @ModifyVariable(method = "dropItems", at = @At(value = "STORE", ordinal = 0), index = 3)
    private int ranked$atLeastOneArrow(int arrows) {
        return Ranked.inMatchWorld() ? Math.max(1, arrows) : arrows;
    }
}
