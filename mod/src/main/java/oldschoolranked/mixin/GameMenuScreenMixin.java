package oldschoolranked.mixin;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a match, Save and quit becomes Forfeit and quit (leaving any other way counts as a forfeit too). */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    private static final int SAVE_AND_QUIT = 1;

    @Inject(method = "init", at = @At("TAIL"))
    private void ranked$rename(CallbackInfo ci) {
        if (!Ranked.inMatch()) return;
        for (ButtonWidget b : this.buttons) {
            if (b.id == SAVE_AND_QUIT) b.message = "§cForfeit and quit";
        }
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"))
    private void ranked$forfeit(ButtonWidget button, CallbackInfo ci) {
        if (button.id == SAVE_AND_QUIT && Ranked.inMatch()) Ranked.forfeit();
    }
}
