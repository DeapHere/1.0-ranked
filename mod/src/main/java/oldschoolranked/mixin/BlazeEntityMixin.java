package oldschoolranked.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.mob.monster.BlazeEntity;
import oldschoolranked.Standard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Blaze rod drops from the match's shared sequence (see Standard). */
@Mixin(BlazeEntity.class)
public abstract class BlazeEntityMixin {
    @ModifyVariable(method = "dropItems", at = @At("STORE"), index = 3)
    private int ranked$rods(int count, @Local(argsOnly = true) int looting) {
        return Standard.rods(count, looting);
    }
}
