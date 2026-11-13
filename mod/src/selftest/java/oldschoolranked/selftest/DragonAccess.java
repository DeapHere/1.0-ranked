package oldschoolranked.selftest;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.monster.boss.EnderDragonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EnderDragonEntity.class)
public interface DragonAccess {
    @Invoker("chooseTarget")
    void ranked$chooseTarget();

    @Accessor("target")
    Entity ranked$target();
}
