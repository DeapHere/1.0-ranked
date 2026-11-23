package oldschoolranked;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

/** Private room: a random seed from the pool, or one you type in (those runs don't go on the leaderboard). */
public final class PrivateRoomScreen extends Screen {
    private static final int START = 0, BACK = 1;
    private final Screen parent;
    private TextFieldWidget seedBox;

    public PrivateRoomScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        this.buttons.clear();
        int x = this.width / 2 - 100, y = this.height / 4 + 40;
        String typed = seedBox == null ? "" : seedBox.getText();
        seedBox = new TextFieldWidget(this, this.textRenderer, x, y + 20, 200, 20, typed);
        seedBox.setMaxLength(32);
        seedBox.focused = true;
        this.buttons.add(new ButtonWidget(START, x, y + 74, 98, 20, "Start"));
        this.buttons.add(new ButtonWidget(BACK, x + 102, y + 74, 98, 20, "Back"));
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void tick() {
        seedBox.tick();
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            this.minecraft.openScreen(parent);
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            start();
        } else {
            seedBox.keyPressed(chr, key);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        seedBox.mouseClicked(x, y, button);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == START) start();
        else if (button.id == BACK) this.minecraft.openScreen(parent);
    }

    private void start() {
        Ranked.privateRoom(seedOf(seedBox.getText()));
        this.minecraft.openScreen(parent);
    }

    /** The seed the way 1.0's Create World screen reads it: a number, or any other text's hash. Null for none. */
    static String seedOf(String text) {
        text = text == null ? "" : text.trim();
        if (text.isEmpty()) return null;
        try {
            long v = Long.parseLong(text);
            if (v != 0) return String.valueOf(v);
        } catch (NumberFormatException e) {
            // text: hashed below
        }
        return String.valueOf((long) text.hashCode());
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        int cx = this.width / 2, y = this.height / 4;
        this.drawCenteredString(this.textRenderer, "§bPrivate room", cx, y - 8, 0xFFFFFF);
        this.drawString(this.textRenderer, "Seed", cx - 100, y + 48, 0xA0A0A0);
        seedBox.render();
        this.drawCenteredString(this.textRenderer, "§7Leave empty for a random seed from the pool.", cx, y + 86, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, "§7Runs on a custom seed don't go on the leaderboard.", cx, y + 98, 0xFFFFFF);
        super.render(mouseX, mouseY, tickDelta);
    }
}
