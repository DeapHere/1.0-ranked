package oldschoolranked.finder;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import oldschoolranked.seeds.FinderStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The finder's progress over the title screen, which it passes through between worlds. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Inject(method = "render", at = @At("TAIL"))
    private void ranked$status(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        fill(0, 0, this.width, 48, 0xC0000000);
        this.drawCenteredString(this.textRenderer, "§e" + FinderStatus.status, this.width / 2, 6, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, "§7Good seeds are saved to .minecraft/ranked-seeds.txt. Close the game to stop.", this.width / 2, 20, 0xFFFFFF);
        if (!FinderStatus.lastGood.isEmpty()) {
            String s = "Last good: " + FinderStatus.lastGood;
            if (this.textRenderer.getWidth(s) > this.width - 8) s = s.substring(0, Math.min(s.length(), 60)) + "...";
            this.drawCenteredString(this.textRenderer, "§a" + s, this.width / 2, 34, 0xFFFFFF);
        }
    }
}
