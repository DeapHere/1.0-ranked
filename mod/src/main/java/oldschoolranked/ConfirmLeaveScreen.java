package oldschoolranked;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

/** "Are you sure?" before leaving a match, so a stray click on Leave match or Forfeit and quit doesn't throw the game. */
public final class ConfirmLeaveScreen extends Screen {
    private static final int YES = 0, NO = 1;
    private final Screen parent;
    private final Runnable leave;

    public ConfirmLeaveScreen(Screen parent, Runnable leave) {
        this.parent = parent;
        this.leave = leave;
    }

    @Override
    public void init() {
        this.buttons.clear();
        int x = this.width / 2, y = this.height / 2 + 10;
        this.buttons.add(new ButtonWidget(YES, x - 102, y, 98, 20, Ranked.solo ? "§cLeave" : "§cForfeit"));
        this.buttons.add(new ButtonWidget(NO, x + 4, y, 98, 20, "Cancel"));
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == Keyboard.KEY_ESCAPE) back();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == YES) leave.run();
        else if (button.id == NO) back();
    }

    private void back() {
        // the load-in screen is gone once the race has started
        if (parent instanceof WaitingScreen && Ranked.state == Ranked.State.RUNNING) this.minecraft.openScreen(null);
        else this.minecraft.openScreen(parent);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        int cx = this.width / 2, y = this.height / 2 - 30;
        if (Ranked.solo) {
            this.drawCenteredString(this.textRenderer, "Leave this private room?", cx, y, 0xFFFFFF);
            this.drawCenteredString(this.textRenderer, "§7The run ends here.", cx, y + 14, 0xFFFFFF);
        } else {
            this.drawCenteredString(this.textRenderer, "Forfeit this match?", cx, y, 0xFFFFFF);
            this.drawCenteredString(this.textRenderer, Ranked.rankedMatch ? "§7" + Ranked.opponent + " wins and you lose elo." : "§7" + Ranked.opponent + " wins.", cx, y + 14, 0xFFFFFF);
        }
        super.render(mouseX, mouseY, tickDelta);
    }
}
