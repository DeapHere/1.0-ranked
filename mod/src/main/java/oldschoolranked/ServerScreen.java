package oldschoolranked;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

/** Change the match server address. */
public final class ServerScreen extends Screen {
    private static final int DONE = 0, CANCEL = 1;
    private final Screen parent;
    private TextFieldWidget field;

    public ServerScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        this.buttons.clear();
        this.field = new TextFieldWidget(this, this.textRenderer, this.width / 2 - 150, this.height / 4 + 40, 300, 20, Ranked.cfg.server);
        this.field.setMaxLength(200);
        this.field.setFocused(true);
        this.buttons.add(new ButtonWidget(DONE, this.width / 2 - 100, this.height / 4 + 96, 98, 20, "Done"));
        this.buttons.add(new ButtonWidget(CANCEL, this.width / 2 + 2, this.height / 4 + 96, 98, 20, "Cancel"));
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void tick() {
        this.field.tick();
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == Keyboard.KEY_RETURN) {
            buttonClicked((ButtonWidget) this.buttons.get(DONE));
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            this.minecraft.openScreen(parent);
            return;
        }
        this.field.keyPressed(chr, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        this.field.mouseClicked(x, y, button);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == DONE) Ranked.setServer(this.field.getText());
        this.minecraft.openScreen(parent);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, "Ranked server", this.width / 2, this.height / 4, 0xFFFFFF);
        this.drawString(this.textRenderer, "Server address", this.width / 2 - 150, this.height / 4 + 28, 0xA0A0A0);
        this.field.render();
        this.drawCenteredString(this.textRenderer, "§7Everyone you play must use the same server.", this.width / 2, this.height / 4 + 70, 0xFFFFFF);
        super.render(mouseX, mouseY, tickDelta);
    }
}
