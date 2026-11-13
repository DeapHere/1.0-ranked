package oldschoolranked.mixin;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The match timer and splits, top left (achievement popups cover the top right). F3 is turned off in match worlds:
 * it shows the seed (with some mods) and every coordinate; the HUD shows only the height.
 */
@Mixin(GameGui.class)
public abstract class GameGuiMixin {
    @Shadow private Minecraft minecraft;

    @Inject(method = "render", at = @At("HEAD"))
    private void ranked$noF3(float tickDelta, boolean screenOpen, int mouseX, int mouseY, CallbackInfo ci) {
        if (Ranked.inMatchWorld() || Ranked.inMatch() && this.minecraft.world != null) this.minecraft.options.debugEnabled = false;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ranked$hud(float tickDelta, boolean screenOpen, int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft mc = this.minecraft;
        if (mc.options.hideGui || mc.options.debugEnabled) return;
        List<String> lines = Ranked.hud();
        if (lines.isEmpty()) return;
        int y = 2;
        for (String s : lines) {
            mc.textRenderer.drawWithShadow(s, 2, y, 0xFFFFFF);
            y += 10;
        }
    }
}
