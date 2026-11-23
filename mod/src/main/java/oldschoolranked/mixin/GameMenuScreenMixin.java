package oldschoolranked.mixin;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import oldschoolranked.Ranked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During a match: Save and quit becomes Forfeit and quit (leaving any other way counts as a forfeit too), and there are
 * match buttons in place of Achievements and Statistics: Reset world (your own world again on the same seed, clock still
 * running; click twice to confirm), Vote new seed / Accept seed, and Offer draw / Accept draw.
 */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    private static final int SAVE_AND_QUIT = 1;
    @Unique
    private static final int RESET = 7301, DRAW = 7302, SEED = 7303, ACHIEVEMENTS = 5, STATS = 6;
    @Unique
    private static long ranked$resetConfirmUntil;
    @Unique
    private ButtonWidget ranked$reset, ranked$draw, ranked$seed;

    @Inject(method = "init", at = @At("TAIL"))
    private void ranked$buttons(CallbackInfo ci) {
        if (!Ranked.inMatch()) return;
        for (ButtonWidget b : this.buttons) {
            if (b.id == SAVE_AND_QUIT) b.message = "§cForfeit and quit";
        }
        if (Ranked.state != Ranked.State.RUNNING) return;
        // achievements and statistics make way for the match buttons
        this.buttons.removeIf(b -> ((ButtonWidget) b).id == ACHIEVEMENTS || ((ButtonWidget) b).id == STATS);
        int x = this.width / 2 - 100, y = this.height / 4 + 32;
        ranked$reset = new ButtonWidget(RESET, x, y, 98, 20, "Reset world");
        ranked$seed = new ButtonWidget(SEED, x + 102, y, 98, 20, "");
        this.buttons.add(ranked$reset);
        this.buttons.add(ranked$seed);
        if (!Ranked.solo) {
            ranked$draw = new ButtonWidget(DRAW, x, y + 24, 200, 20, "");
            this.buttons.add(ranked$draw);
        }
        ranked$labels();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ranked$tick(CallbackInfo ci) {
        ranked$labels();
    }

    @Unique
    private void ranked$labels() {
        long now = System.currentTimeMillis();
        if (ranked$reset != null) ranked$reset.message = now < ranked$resetConfirmUntil ? "§cSure? Click again" : "Reset world";
        if (ranked$seed != null) {
            if (Ranked.solo) {
                ranked$seed.message = "New seed";
            } else if (Ranked.seedPending()) {
                ranked$seed.message = "§aAccept seed §7(" + ((Ranked.theirSeedUntil - now) / 1000 + 1) + "s)";
                ranked$seed.active = true;
            } else if (Ranked.mySeedUntil > now) {
                ranked$seed.message = "§7Asked (" + ((Ranked.mySeedUntil - now) / 1000 + 1) + "s)";
                ranked$seed.active = false;
            } else {
                ranked$seed.message = "Vote new seed";
                ranked$seed.active = true;
            }
        }
        if (ranked$draw != null) {
            if (Ranked.drawPending()) {
                ranked$draw.message = "§aAccept draw §7(" + ((Ranked.theirDrawUntil - now) / 1000 + 1) + "s)";
                ranked$draw.active = true;
            } else if (Ranked.myDrawUntil > now) {
                ranked$draw.message = "§7Offered (" + ((Ranked.myDrawUntil - now) / 1000 + 1) + "s)";
                ranked$draw.active = false;
            } else {
                ranked$draw.message = "Offer draw";
                ranked$draw.active = Ranked.state == Ranked.State.RUNNING;
            }
        }
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void ranked$clicked(ButtonWidget button, CallbackInfo ci) {
        if (button.id == SAVE_AND_QUIT && Ranked.inMatch()) {
            ci.cancel();
            net.minecraft.client.Minecraft mc = this.minecraft;
            mc.openScreen(new oldschoolranked.ConfirmLeaveScreen((net.minecraft.client.gui.screen.Screen) (Object) this, () -> {
                Ranked.forfeit();
                mc.setWorld(null);
                mc.openScreen(new net.minecraft.client.gui.screen.TitleScreen());
            }));
            return;
        }
        if (button.id == RESET) {
            ci.cancel();
            if (System.currentTimeMillis() < ranked$resetConfirmUntil) {
                ranked$resetConfirmUntil = 0;
                Ranked.resetWorld();
            } else {
                ranked$resetConfirmUntil = System.currentTimeMillis() + 3000;
                ranked$labels();
            }
        } else if (button.id == SEED) {
            ci.cancel();
            if (button.active) Ranked.newSeed();
            this.minecraft.openScreen(null);
        } else if (button.id == DRAW) {
            ci.cancel();
            if (button.active) Ranked.draw();
            this.minecraft.openScreen(null);
        }
    }
}
