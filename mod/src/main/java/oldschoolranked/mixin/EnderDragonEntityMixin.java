package oldschoolranked.mixin;

import java.util.Random;
import net.minecraft.entity.mob.monster.boss.EnderDragonEntity;
import oldschoolranked.Standard;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * In match worlds the dragon's decisions come from a sequence seeded by the match: each time it picks a new target,
 * whether it charges at you or flies to a point (and which point) is the same for both players' 1st, 2nd, 3rd...
 * choice. Its other randomness (particles, damage knockback) is untouched.
 */
@Mixin(EnderDragonEntity.class)
public abstract class EnderDragonEntityMixin {
    @Redirect(method = "chooseTarget", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/mob/monster/boss/EnderDragonEntity;random:Ljava/util/Random;", opcode = Opcodes.GETFIELD))
    private Random ranked$targetRandom(EnderDragonEntity dragon) {
        return Standard.dragon(((EntityAccessor) dragon).ranked$random());
    }

    // the small wobble added to a target point when the dragon reaches it
    @Redirect(method = "mobTick", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextGaussian()D"))
    private double ranked$wobble(Random own) {
        return Standard.dragon(own).nextGaussian();
    }
}
