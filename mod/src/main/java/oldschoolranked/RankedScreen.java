package oldschoolranked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;

/** The ranked lobby, from the blaze rod button on the title screen: queue, private rooms, your elo and the top players. */
public final class RankedScreen extends Screen {
    private static final int QUEUE = 0, ROOM = 1, JOIN = 2, SERVER = 3, BACK = 4, RETRY = 5, PRIVATE = 6, LEADERBOARD = 7;

    private final Screen parent;
    private TextFieldWidget code;
    private ButtonWidget queueButton, roomButton, joinButton, retryButton, privateButton;
    private final List<String> top = new ArrayList<>();
    private String online = "";
    private int refresh;

    public RankedScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        if (!Ranked.signedIn()) Ranked.signIn();
        loadLeaderboard();
        this.buttons.clear();
        int x = this.width / 2 - 100, y = this.height / 4 + 40;
        queueButton = new ButtonWidget(QUEUE, x, y, 200, 20, "");
        roomButton = new ButtonWidget(ROOM, x, y + 24, 98, 20, "");
        this.code = new TextFieldWidget(this, this.textRenderer, x + 102, y + 24, 46, 20, "");
        this.code.setMaxLength(4);
        joinButton = new ButtonWidget(JOIN, x + 152, y + 24, 48, 20, "Join");
        retryButton = new ButtonWidget(RETRY, x, y, 200, 20, "Try again");
        this.buttons.add(queueButton);
        this.buttons.add(roomButton);
        this.buttons.add(joinButton);
        privateButton = new ButtonWidget(PRIVATE, x, y + 48, 200, 20, "Private room (solo run)");
        this.buttons.add(privateButton);
        // the server address is in config/oldschoolranked.properties (server=...) for anyone hosting their own
        this.buttons.add(new ButtonWidget(LEADERBOARD, x, y + 72, 98, 20, "Leaderboard"));
        this.buttons.add(new ButtonWidget(BACK, x + 102, y + 72, 98, 20, "Back"));
        update();
    }

    private void update() {
        boolean in = Ranked.signedIn();
        boolean busy = Ranked.inMatch();
        boolean queued = Ranked.state == Ranked.State.QUEUED;
        boolean room = Ranked.state == Ranked.State.ROOM;
        queueButton.message = queued ? "Cancel search" : "Find ranked match";
        queueButton.active = in && !busy && !room && !Ranked.blocked;
        roomButton.message = room ? "Close room" : "Create room";
        roomButton.active = in && !busy && !queued;
        privateButton.active = in && !busy && !queued;
        joinButton.active = in && !busy && !queued && !room && this.code.getText().trim().length() == 4;
        boolean failed = !in && Ranked.signInError != null;
        if (failed && !this.buttons.contains(retryButton)) {
            this.buttons.remove(queueButton);
            this.buttons.add(retryButton);
        } else if (!failed && this.buttons.contains(retryButton)) {
            this.buttons.remove(retryButton);
            this.buttons.add(0, queueButton);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLeaderboard() {
        new Thread(() -> {
            try {
                Map<String, Object> r = Net.request("GET", "/api/leaderboard", null, 10000);
                List<String> lines = new ArrayList<>();
                int i = 1;
                for (Object o : (List<Object>) r.get("players")) {
                    Map<String, Object> p = (Map<String, Object>) o;
                    lines.add(i + ". " + Json.str(p, "name", "?") + "  §6" + Json.num(p, "elo", 0) + "§7  " + Json.num(p, "wins", 0) + "-" + Json.num(p, "losses", 0));
                    if (++i > 8) break;
                }
                String on = Json.num(r, "online", 0) + " online";
                Net.inbox.add(() -> {
                    top.clear();
                    top.addAll(lines);
                    online = on;
                });
            } catch (Exception e) {
                Ranked.log("leaderboard: " + e);
            }
        }, "Ranked leaderboard").start();
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void tick() {
        this.code.tick();
        update();
        if (++refresh % 200 == 0) loadLeaderboard();
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            this.minecraft.openScreen(parent);
            return;
        }
        if (key == Keyboard.KEY_RETURN && joinButton.active) {
            buttonClicked(joinButton);
            return;
        }
        if (Character.isDigit(chr) || key == Keyboard.KEY_BACK || key == Keyboard.KEY_LEFT || key == Keyboard.KEY_RIGHT) {
            this.code.keyPressed(chr, key);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        this.code.mouseClicked(x, y, button);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) return;
        switch (button.id) {
            case QUEUE:
                if (Ranked.state == Ranked.State.QUEUED) Ranked.cancel();
                else Ranked.queue();
                break;
            case ROOM:
                if (Ranked.state == Ranked.State.ROOM) Ranked.cancel();
                else Ranked.createRoom();
                break;
            case PRIVATE:
                Ranked.privateRoom();
                break;
            case JOIN:
                Ranked.joinRoom(this.code.getText());
                break;
            case RETRY:
                Ranked.signIn();
                break;
            case LEADERBOARD:
                this.minecraft.openScreen(new LeaderboardScreen(this));
                break;
            case SERVER:
                this.minecraft.openScreen(new ServerScreen(this));
                break;
            case BACK:
                this.minecraft.openScreen(parent);
                break;
            default:
                break;
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        int cx = this.width / 2, y = this.height / 4;
        this.drawCenteredString(this.textRenderer, "§6Oldschool Ranked", cx, y - 8, 0xFFFFFF);
        String who;
        if (Ranked.signedIn()) {
            who = "§e" + Ranked.name + "§r   Elo §6" + Ranked.elo + "§r   " + Ranked.wins + "W " + Ranked.losses + "L";
        } else if (Ranked.signInError != null) {
            who = "§c" + Ranked.signInError;
        } else {
            who = "§7Connecting...";
        }
        this.drawCenteredString(this.textRenderer, who, cx, y + 6, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, status(), cx, y + 22, 0xFFFFFF);
        this.code.render();
        if (this.code.getText().isEmpty() && !this.code.focused) {
            this.drawString(this.textRenderer, "§8code", cx + 6, y + 70, 0xFFFFFF);
        }
        int ly = y + 140;
        if (!top.isEmpty()) {
            this.drawCenteredString(this.textRenderer, "Top players §7(" + online + ")", cx, ly, 0xFFFFFF);
            // only what fits above the bottom line; the Leaderboard button has the rest
            int bottom = this.height - (Ranked.motd.isEmpty() ? 4 : 16);
            for (int i = 0; i < top.size() && ly + 12 + i * 10 + 9 <= bottom; i++) {
                this.drawString(this.textRenderer, top.get(i), cx - 60, ly + 12 + i * 10, 0xFFFFFF);
            }
        }
        if (!Ranked.motd.isEmpty()) this.drawCenteredString(this.textRenderer, "§7" + Ranked.motd, cx, this.height - 12, 0xFFFFFF);
        super.render(mouseX, mouseY, tickDelta);
    }

    private static String status() {
        if (Ranked.lastError != null) return "§c" + Ranked.lastError;
        switch (Ranked.state) {
            case QUEUED:
                return "Searching for an opponent... " + Ranked.time(System.currentTimeMillis() - Ranked.queuedSince).replaceAll("\\.\\d$", "");
            case ROOM:
                return "Room code §a" + Ranked.roomCode + "§r: give it to your friend";
            case LOADING:
            case COUNTDOWN:
                return "Match found, loading...";
            case DONE:
                return Ranked.resultLines.isEmpty() ? "" : Ranked.resultLines.get(0);
            default:
                if (Ranked.signedIn() && Net.failing) return "§7Reconnecting...";
                if (Ranked.blocked) return "§7Ranked queue is off while OldschoolPrac is installed. Rooms work.";
                return "";
        }
    }
}
