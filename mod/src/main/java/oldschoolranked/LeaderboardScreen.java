package oldschoolranked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** The leaderboards from the match server (top 100 by elo, fastest ranked wins, or fastest private room runs), a page at a time. */
public final class LeaderboardScreen extends Screen {
    private static final int PREV = 0, NEXT = 1, BACK = 2, REFRESH = 3, MODE = 4;

    private final Screen parent;
    private ButtonWidget prev, next, mode;
    private int view; // 0 elo, 1 fastest ranked wins, 2 fastest private room runs
    private final List<Object[]> rows = new ArrayList<>(); // rank, name, elo, wins, losses
    private String online = "", message = "§7Loading...";
    private int page;

    public LeaderboardScreen(Screen parent) {
        this(parent, false);
    }

    public LeaderboardScreen(Screen parent, boolean fastest) {
        this(parent, fastest ? 1 : 0);
    }

    /** view: 0 elo, 1 fastest ranked wins, 2 fastest private room runs */
    public LeaderboardScreen(Screen parent, int view) {
        this.parent = parent;
        this.view = view;
    }

    @Override
    public void init() {
        this.buttons.clear();
        int y = this.height - 28, cx = this.width / 2;
        prev = new ButtonWidget(PREV, cx - 152, y, 56, 20, "< Prev");
        next = new ButtonWidget(NEXT, cx - 92, y, 56, 20, "Next >");
        mode = new ButtonWidget(MODE, cx - 32, y, 64, 20, nextLabel());
        this.buttons.add(prev);
        this.buttons.add(next);
        this.buttons.add(mode);
        this.buttons.add(new ButtonWidget(REFRESH, cx + 36, y, 56, 20, "Refresh"));
        this.buttons.add(new ButtonWidget(BACK, cx + 96, y, 56, 20, "Back"));
        if (rows.isEmpty()) load();
        update();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        message = "§7Loading...";
        rows.clear();
        final int v = view;
        final boolean fast = v != 0;
        new Thread(() -> {
            try {
                Map<String, Object> r = Net.request("GET", v == 2 ? "/api/fastest-private" : v == 1 ? "/api/fastest" : "/api/leaderboard", null, 10000);
                List<Object[]> list = new ArrayList<>();
                int i = 1;
                for (Object o : (List<Object>) r.get("players")) {
                    Map<String, Object> p = (Map<String, Object>) o;
                    if (fast) list.add(new Object[] {i++, Json.str(p, "name", "?"), Json.num(p, "time", 0), Json.num(p, "at", 0)});
                    else list.add(new Object[] {i++, Json.str(p, "name", "?"), Json.num(p, "elo", 0), Json.num(p, "wins", 0), Json.num(p, "losses", 0)});
                }
                String on = fast ? "" : Json.num(r, "online", 0) + " online";
                Net.inbox.add(() -> {
                    if (v != view) return; // switched while loading
                    rows.clear();
                    rows.addAll(list);
                    online = on;
                    message = list.isEmpty() ? (v == 2 ? "§7Nobody has finished a private room run yet." : v == 1 ? "§7Nobody has won a ranked match by finishing yet." : "§7Nobody has played a ranked match yet.") : "";
                    // open on the page you're on
                    for (Object[] row : rows) {
                        if (Ranked.name != null && Ranked.name.equalsIgnoreCase((String) row[1])) page = ((Integer) row[0] - 1) / perPage();
                    }
                    update();
                });
            } catch (Exception e) {
                Net.inbox.add(() -> message = "§cCan't reach the ranked server");
            }
        }, "Ranked leaderboard").start();
    }

    /** As many rows as fit between the header and the buttons. */
    private int perPage() {
        return Math.max(1, (this.height - 28 - 4 - 54) / 11);
    }

    private int pages() {
        return Math.max(1, (rows.size() + perPage() - 1) / perPage());
    }

    private void update() {
        page = Math.max(0, Math.min(page, pages() - 1));
        prev.active = page > 0;
        next.active = page < pages() - 1;
    }

    @Override
    public void tick() {
        int wheel = Mouse.getDWheel();
        if (wheel < 0) page++;
        else if (wheel > 0) page--;
        update();
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == Keyboard.KEY_ESCAPE) this.minecraft.openScreen(parent);
        else if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_PRIOR) page--;
        else if (key == Keyboard.KEY_RIGHT || key == Keyboard.KEY_NEXT) page++;
        update();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) return;
        if (button.id == PREV) page--;
        else if (button.id == NEXT) page++;
        else if (button.id == REFRESH) load();
        else if (button.id == MODE) {
            view = (view + 1) % 3;
            mode.message = nextLabel();
            page = 0;
            load();
        }
        else if (button.id == BACK) this.minecraft.openScreen(parent);
        update();
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        int cx = this.width / 2;
        this.drawCenteredString(this.textRenderer, view == 2 ? "§6Fastest private room runs" : view == 1 ? "§6Fastest times" : "§6Ranked leaderboard", cx, 10, 0xFFFFFF);
        String sub = rows.isEmpty() ? "" : "§7" + (online.isEmpty() ? "" : online + "   ") + "page " + (page + 1) + " of " + pages();
        if (view != 0) {
            renderFastest(cx);
            super.render(mouseX, mouseY, tickDelta);
            this.drawCenteredString(this.textRenderer, sub, cx, 22, 0xFFFFFF);
            return;
        }
        this.drawCenteredString(this.textRenderer, sub, cx, 22, 0xFFFFFF);
        int x0 = cx - 150, y = 40;
        this.drawString(this.textRenderer, "§7#", x0, y, 0xFFFFFF);
        this.drawString(this.textRenderer, "§7Player", x0 + 30, y, 0xFFFFFF);
        this.drawString(this.textRenderer, "§7Elo", x0 + 160, y, 0xFFFFFF);
        this.drawString(this.textRenderer, "§7W-L", x0 + 205, y, 0xFFFFFF);
        this.drawString(this.textRenderer, "§7Win %", x0 + 260, y, 0xFFFFFF);
        y += 14;
        if (!message.isEmpty()) this.drawCenteredString(this.textRenderer, message, cx, y + 20, 0xFFFFFF);
        for (int i = page * perPage(); i < Math.min(rows.size(), (page + 1) * perPage()); i++) {
            Object[] r = rows.get(i);
            int rank = (Integer) r[0];
            String name = (String) r[1];
            long w = (Long) r[3], l = (Long) r[4];
            boolean me = Ranked.name != null && Ranked.name.equalsIgnoreCase(name);
            if (me) fill(x0 - 4, y - 2, x0 + 300, y + 9, 0x40FFFF00);
            String c = rank == 1 ? "§6" : rank == 2 ? "§f" : rank == 3 ? "§c" : "§7";
            this.drawString(this.textRenderer, c + rank + ".", x0, y, 0xFFFFFF);
            this.drawString(this.textRenderer, (me ? "§e" : "§f") + name, x0 + 30, y, 0xFFFFFF);
            this.drawString(this.textRenderer, "§6" + r[2], x0 + 160, y, 0xFFFFFF);
            this.drawString(this.textRenderer, "§f" + w + "-" + l, x0 + 205, y, 0xFFFFFF);
            this.drawString(this.textRenderer, "§7" + (w + l == 0 ? "-" : Math.round(w * 100.0 / (w + l)) + "%"), x0 + 260, y, 0xFFFFFF);
            y += 11;
        }
        super.render(mouseX, mouseY, tickDelta);
    }

    /** Best ranked win by finishing, per player (forfeits and draws don't count). */
    private void renderFastest(int cx) {
        int x0 = cx - 150, y = 40;
        this.drawString(this.textRenderer, "§7#", x0, y, 0xFFFFFF);
        this.drawString(this.textRenderer, "§7Player", x0 + 30, y, 0xFFFFFF);
        this.drawString(this.textRenderer, "§7Time", x0 + 160, y, 0xFFFFFF);
        this.drawString(this.textRenderer, "§7Date", x0 + 230, y, 0xFFFFFF);
        y += 14;
        if (!message.isEmpty()) this.drawCenteredString(this.textRenderer, message, cx, y + 20, 0xFFFFFF);
        java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("d MMM yyyy");
        for (int i = page * perPage(); i < Math.min(rows.size(), (page + 1) * perPage()); i++) {
            Object[] r = rows.get(i);
            int rank = (Integer) r[0];
            String name = (String) r[1];
            long ms = (Long) r[2], at = (Long) r[3];
            boolean me = Ranked.name != null && Ranked.name.equalsIgnoreCase(name);
            if (me) fill(x0 - 4, y - 2, x0 + 300, y + 9, 0x40FFFF00);
            String c = rank == 1 ? "§6" : rank == 2 ? "§f" : rank == 3 ? "§c" : "§7";
            this.drawString(this.textRenderer, c + rank + ".", x0, y, 0xFFFFFF);
            this.drawString(this.textRenderer, (me ? "§e" : "§f") + name, x0 + 30, y, 0xFFFFFF);
            this.drawString(this.textRenderer, "§a" + clock(ms), x0 + 160, y, 0xFFFFFF);
            this.drawString(this.textRenderer, "§7" + (at > 0 ? day.format(new java.util.Date(at)) : ""), x0 + 230, y, 0xFFFFFF);
            y += 11;
        }
    }

    private String nextLabel() {
        return view == 0 ? "Fastest" : view == 1 ? "Private" : "Elo";
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        String t = (s / 60) % 60 + ":" + String.format("%02d", s % 60) + "." + (ms % 1000) / 100;
        return s >= 3600 ? s / 3600 + ":" + (s / 60 % 60 < 10 ? "0" : "") + t : t;
    }
}
