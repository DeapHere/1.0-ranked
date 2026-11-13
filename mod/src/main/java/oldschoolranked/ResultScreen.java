package oldschoolranked;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.opengl.GL11;

/** Pops up when a match ends: Victory or Defeat in big letters, how it ended, the elo change and both players' splits. */
public final class ResultScreen extends Screen {
    private static final int LOBBY = 0, STAY = 1;
    /** Ticks since it opened: the title shows first, then the elo changes count in. */
    private int age;

    @Override
    public void tick() {
        age++;
    }

    @Override
    public void init() {
        this.buttons.clear();
        int y = this.height - 28;
        this.buttons.add(new ButtonWidget(LOBBY, this.width / 2 - 152, y, 150, 20, "Back to lobby"));
        this.buttons.add(new ButtonWidget(STAY, this.width / 2 + 2, y, 150, 20, "Keep looking around"));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == LOBBY) {
            backToLobby();
        } else if (button.id == STAY) {
            this.minecraft.openScreen(null);
        }
    }

    public void backToLobby() {
        this.minecraft.setWorld(null);
        this.minecraft.openScreen(new RankedScreen(new net.minecraft.client.gui.screen.TitleScreen()));
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.fillGradient(0, 0, this.width, this.height, 0xF0101010, 0xF0101010);
        int cx = this.width / 2;
        String title;
        int color;
        if (Ranked.solo) {
            title = Ranked.lastReason.equals("finish") ? "RUN COMPLETE" : "RUN ENDED";
            color = Ranked.lastReason.equals("finish") ? 0x55FFFF : 0xAAAAAA;
        } else if (Ranked.lastReason.equals("draw")) {
            title = "DRAW";
            color = 0xFFFF55;
        } else if (Ranked.lastDraw) {
            title = "NO RESULT";
            color = 0xAAAAAA;
        } else {
            title = Ranked.lastWon ? "VICTORY" : "DEFEAT";
            color = Ranked.lastWon ? 0x55FF55 : 0xFF5555;
        }
        int y = Math.max(8, this.height / 2 - 100);
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, y, 0);
        GL11.glScalef(4, 4, 1);
        this.drawCenteredString(this.textRenderer, title, 0, 0, color);
        GL11.glPopMatrix();
        y += 42;
        // how it ended (the elo line is drawn below, for both players)
        if (!Ranked.resultLines.isEmpty()) {
            this.drawCenteredString(this.textRenderer, Ranked.resultLines.get(0), cx, y, 0xFFFFFF);
            y += 11;
        }
        // after the title: what each player's elo did, counting up to the new value
        if (Ranked.rankedMatch && (!Ranked.lastDraw || Ranked.lastReason.equals("draw")) && age >= 30) {
            float k = Math.min(1f, (age - 30 + tickDelta) / 30f);
            y += 4;
            y = eloLine(Ranked.name, Ranked.elo, Ranked.lastChange, k, "§b", cx, y);
            y = eloLine(Ranked.opponent, Ranked.lastOppElo, Ranked.lastOppChange, k, "§e", cx, y);
        }
        // splits: you, and your opponent
        y += 8;
        int c1 = cx - 90, c2 = cx + 10, c3 = cx + 70;
        if (!Ranked.solo) {
            this.drawString(this.textRenderer, "§bYou", c2, y, 0xFFFFFF);
            this.drawString(this.textRenderer, "§e" + Ranked.opponent, c3, y, 0xFFFFFF);
            y += 11;
        }
        for (int i = 0; i < Ranked.SPLITS.length; i++) {
            Long mine = Ranked.mySplits.get(Ranked.SPLITS[i]), theirs = Ranked.oppSplits.get(Ranked.SPLITS[i]);
            if (y > this.height - 42) break;
            this.drawString(this.textRenderer, "§7" + Ranked.SPLIT_NAMES[i], c1, y, 0xFFFFFF);
            this.drawString(this.textRenderer, mine == null ? "§8-" : "§b" + Ranked.time(mine), c2, y, 0xFFFFFF);
            if (!Ranked.solo) this.drawString(this.textRenderer, theirs == null ? "§8-" : "§e" + Ranked.time(theirs), c3, y, 0xFFFFFF);
            y += 10;
        }
        super.render(mouseX, mouseY, tickDelta);
    }

    private int eloLine(String who, long elo, long change, float k, String col, int cx, int y) {
        long before = elo - change;
        long shown = before + Math.round(change * k);
        String delta = (change >= 0 ? "§a+" : "§c") + change;
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, y, 0);
        GL11.glScalef(1.5f, 1.5f, 1);
        this.drawCenteredString(this.textRenderer, col + who + "§7  " + before + " -> §f" + shown + "  " + delta, 0, 0, 0xFFFFFF);
        GL11.glPopMatrix();
        return y + 16;
    }
}
