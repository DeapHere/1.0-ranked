package oldschoolranked.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.mob.monster.EndermanEntity;
import oldschoolranked.Standard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Pearl drops from the match's shared sequence (see Standard). */
@Mixin(EndermanEntity.class)
public abstract class EndermanEntityMixin {
    @ModifyVariable(method = "dropItems", at = @At("STORE"), index = 4)
    private int ranked$pearls(int count, @Local(argsOnly = true) int looting) {
        return Standard.pearls(count, looting);
    }
}
