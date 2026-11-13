package oldschoolranked;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * Shown over the match world until the race starts. It's a pause screen, so the world doesn't tick: nobody can move,
 * and both worlds start the race at the same game tick no matter who loaded first.
 */
public final class WaitingScreen extends Screen {
    private static final int FORFEIT = 0;

    @Override
    public void init() {
        this.buttons.clear();
        this.buttons.add(new ButtonWidget(FORFEIT, this.width / 2 - 100, this.height / 4 + 120, "Leave match"));
    }

    @Override
    protected void keyPressed(char chr, int key) {
        // Esc does nothing: the screen closes itself at the start
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == FORFEIT) {
            Ranked.forfeit();
            this.minecraft.setWorld(null);
            this.minecraft.openScreen(new RankedScreen(new net.minecraft.client.gui.screen.TitleScreen()));
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        int y = this.height / 4 + 20;
        String kind = Ranked.solo ? "§bPrivate room" : Ranked.rankedMatch ? "§6Ranked match" : "§dRoom match";
        this.drawCenteredString(this.textRenderer, kind, this.width / 2, y, 0xFFFFFF);
        if (!Ranked.solo) this.drawCenteredString(this.textRenderer, "vs §e" + Ranked.opponent + (Ranked.rankedMatch ? "§r (" + Ranked.opponentElo + ")" : ""), this.width / 2, y + 14, 0xFFFFFF);
        String line;
        if (Ranked.state == Ranked.State.COUNTDOWN) {
            long left = Math.max(1, (Ranked.startAtLocal - System.currentTimeMillis() + 999) / 1000);
            line = "§a§lStarting in " + left;
        } else if (Ranked.state == Ranked.State.LOADING) {
            line = Ranked.solo ? "Loading..." : Ranked.opponentReady ? "Opponent is ready" : "Waiting for your opponent to load the world...";
        } else {
            line = "";
        }
        this.drawCenteredString(this.textRenderer, line, this.width / 2, y + 40, 0xFFFFFF);
        super.render(mouseX, mouseY, tickDelta);
    }
}
