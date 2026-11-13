package oldschoolranked;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * The load-in screen, shown over the match world until the race starts: who's playing, a countdown, and a chat box.
 * It's a pause screen, so the world doesn't tick: nobody can move, and both worlds start the race at the same game
 * tick no matter who loaded first.
 */
public final class WaitingScreen extends Screen {
    private static final int FORFEIT = 0;
    private TextFieldWidget chatBox;

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        this.buttons.clear();
        this.buttons.add(new ButtonWidget(FORFEIT, this.width / 2 - 100, this.height - 26, "Leave match"));
        String typed = chatBox == null ? "" : chatBox.getText();
        chatBox = null;
        if (!Ranked.solo) {
            chatBox = new TextFieldWidget(this, this.textRenderer, this.width / 2 - 150, this.height - 50, 300, 18, typed);
            chatBox.setMaxLength(100);
            chatBox.focused = true;
        }
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void tick() {
        if (chatBox != null) chatBox.tick();
    }

    @Override
    protected void keyPressed(char chr, int key) {
        // Esc does nothing: the screen closes itself at the start
        if (chatBox == null || key == Keyboard.KEY_ESCAPE) return;
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            Ranked.sendChat(chatBox.getText());
            chatBox.setText("");
        } else {
            chatBox.keyPressed(chr, key);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (chatBox != null) chatBox.mouseClicked(mouseX, mouseY, button);
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
        int cx = this.width / 2, y = 16;
        String kind = Ranked.solo ? "§bPrivate room" : Ranked.rankedMatch ? "§6Ranked match" : "§dRoom match";
        this.drawCenteredString(this.textRenderer, kind, cx, y, 0xFFFFFF);
        if (!Ranked.solo) {
            String me = "§b" + Ranked.name + (Ranked.rankedMatch ? " §7(" + Ranked.elo + ")" : "");
            String them = "§e" + Ranked.opponent + (Ranked.rankedMatch ? " §7(" + Ranked.opponentElo + ")" : "");
            this.drawCenteredString(this.textRenderer, me + "  §fvs  " + them, cx, y + 14, 0xFFFFFF);
        }
        int bigY = y + 34;
        if (Ranked.state == Ranked.State.COUNTDOWN) {
            long left = Math.max(1, (Ranked.startAtLocal - System.currentTimeMillis() + 999) / 1000);
            GL11.glPushMatrix();
            GL11.glTranslatef(cx, bigY, 0);
            GL11.glScalef(4, 4, 1);
            this.drawCenteredString(this.textRenderer, (left <= 3 ? "§c" : "§a") + left, 0, 0, 0xFFFFFF);
            GL11.glPopMatrix();
        } else {
            String line = Ranked.state != Ranked.State.LOADING ? "" : Ranked.solo ? "Loading..."
                : Ranked.opponentReady ? "§7Opponent is ready, finishing your world..." : "§7Waiting for your opponent to load the world...";
            this.drawCenteredString(this.textRenderer, line, cx, bigY + 10, 0xFFFFFF);
        }
        if (chatBox != null) {
            int ly = this.height - 62;
            for (int i = Ranked.matchChat.size() - 1; i >= 0 && ly > bigY + 40; i--, ly -= 10) {
                this.drawString(this.textRenderer, Ranked.matchChat.get(i), cx - 150, ly, 0xFFFFFF);
            }
            if (Ranked.matchChat.isEmpty()) this.drawString(this.textRenderer, "§8Say gl hf... (Enter to send, T to chat in game)", cx - 150, ly, 0xFFFFFF);
            chatBox.render();
        }
        super.render(mouseX, mouseY, tickDelta);
    }
}
