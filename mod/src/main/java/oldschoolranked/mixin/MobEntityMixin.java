package oldschoolranked.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.animal.PigEntity;
import net.minecraft.entity.mob.monster.SpiderEntity;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * In match worlds pigs always drop at least one porkchop and spiders at least one string (vanilla 1.0: 0 to 2). Both use
 * the default mob drops; spider eyes are unchanged.
 */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @ModifyVariable(method = "dropItems", at = @At(value = "STORE", ordinal = 0), index = 4)
    private int ranked$atLeastOne(int count) {
        Object self = this;
        return (self instanceof PigEntity || self instanceof SpiderEntity) && Ranked.inMatchWorld() ? Math.max(1, count) : count;
    }
}
