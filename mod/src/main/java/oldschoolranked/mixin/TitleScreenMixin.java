package oldschoolranked.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import oldschoolranked.Ranked;
import oldschoolranked.RankedScreen;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A blaze rod button right of Singleplayer opens the ranked lobby. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private static final int RANKED_BUTTON = 7315;

    private ButtonWidget ranked$button;

    @Inject(method = "init", at = @At("TAIL"))
    private void ranked$addButton(CallbackInfo ci) {
        this.ranked$button = new ButtonWidget(RANKED_BUTTON, this.width / 2 + 104, this.height / 4 + 48, 20, 20, "");
        this.buttons.add(this.ranked$button);
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void ranked$click(ButtonWidget button, CallbackInfo ci) {
        if (button.id == RANKED_BUTTON) {
            ci.cancel();
            this.minecraft.openScreen(new RankedScreen(this));
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ranked$renderButton(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        ButtonWidget b = this.ranked$button;
        if (b == null || !this.buttons.contains(b)) return;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.minecraft.textureManager.load("/gui/items.png"));
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int sprite = Item.BLAZE_ROD.getSprite(0);
        this.drawTexture(b.x + 2, b.y + 2, sprite % 16 * 16, sprite / 16 * 16, 16, 16);
        GL11.glDisable(GL11.GL_BLEND);
        if (mouseX >= b.x && mouseY >= b.y && mouseX < b.x + 20 && mouseY < b.y + 20) {
            String text = "Oldschool Ranked";
            int x = b.x + 24, y = b.y + 6;
            int w = this.textRenderer.getWidth(text);
            if (x + w + 3 > this.width) x = this.width - w - 4;
            this.fillGradient(x - 3, y - 3, x + w + 3, y + 8 + 3, 0xC0000000, 0xC0000000);
            this.drawString(this.textRenderer, text, x, y, 0xFFFFFF);
        }
        Ranked.init();
    }
}
