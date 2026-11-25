package oldschoolranked.pace;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Oldschool pace tracker for Minecraft 1.0. Not a mod: it reads the per-world stats file StatsPerWorld writes
 * (play time, items crafted and used), whether the world's Nether and End folders exist yet, and when its region
 * files were last saved, and sends splits to the Oldschool Ranked server, which posts good paces to Discord.
 * It never opens level.dat or region files.
 */
public final class Tracker {
    static final String SERVER = System.getProperty("pace.server", "https://ranked-1-0.fly.dev");
    // 1.0 stat ids
    static final int PLAY_TIME = 1100, CRAFTED = 16842752, USED = 16908288, DEATHS = 2022, MINED = 16777216, STONE_PLATE = 70, WOOD_PLATE = 72, BLAZE_POWDER = 377, ENDER_EYE = 381;
    static final File HOME = new File(System.getProperty("user.home"), ".oldschoolranked");
    static final File CONFIG = new File(HOME, "pace.properties");
    static final String[] SPLITS = {"nether", "fortress", "pit", "end", "finish"};
    static final String[] SPLIT_NAMES = {"Nether", "Fortress", "Pit start", "End", "Finish"};

    static Properties cfg = new Properties();
    static volatile String token, name;
    static volatile boolean signedIn;
    static volatile List<File> instances = new ArrayList<>();
    static Ui ui;
    static String lastShown;
    static final boolean DEBUG = Boolean.getBoolean("pace.debug");

    /** One world being watched. Each world on a wall is its own run; only the one you actually play gets splits. */
    static final class Run {
        String id; // null: not a run (an old world, or one first seen too late)
        final Map<String, Long> times = new LinkedHashMap<>();
        long lastMod, sent, deathsAtEnd, lastPlates, lastDeaths, deathsAtPlate = -1, netherAtPlate;
        boolean stronghold; // an eye placed: not posted, but the pit can only come before it
        final java.util.ArrayDeque<long[]> saves = new java.util.ArrayDeque<>(); // {stats file time, play time}

        /** Play time at a past moment: from the last stats save before it, plus the wall time since (up to 10s). */
        long playTimeAt(long when) {
            long[] best = null;
            for (long[] e : saves) if (e[0] <= when) best = e;
            if (best == null) return 0;
            return best[1] + Math.max(0, Math.min(200, (when - best[0]) / 50));
        }
        Map<Integer, Long> stats = new java.util.HashMap<>();
    }
    static final Map<File, Run> runs = new java.util.HashMap<>();
    static Run run; // the one split() and stat() work on

    public static void main(String[] args) throws Exception {
        HOME.mkdirs();
        if (CONFIG.exists()) try (InputStream in = new FileInputStream(CONFIG)) { cfg.load(in); }
        boolean headless = args.length > 0 && args[0].equals("--nogui") || java.awt.GraphicsEnvironment.isHeadless();
        for (String p : cfg.getProperty("saves", "").split(java.util.regex.Pattern.quote(File.pathSeparator))) if (!p.trim().isEmpty()) instances.add(new File(p.trim()));
        name = cfg.getProperty("name", "");
        if (headless) {
            if (!name.matches("[A-Za-z0-9_]{1,16}")) { System.out.println("Set name=YourMinecraftName in " + CONFIG); return; }
            if (instances.isEmpty()) { System.out.println("Set saves=<your 1.0 instance folders, separated by " + File.pathSeparator + "> in " + CONFIG); return; }
        } else {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
            SwingUtilities.invokeAndWait(() -> ui = new Ui());
            while (!name.matches("[A-Za-z0-9_]{1,16}")) {
                String n = JOptionPane.showInputDialog(ui.frame, "Your Minecraft name (shown in Discord):", "Oldschool Pace", JOptionPane.QUESTION_MESSAGE);
                if (n == null) System.exit(0);
                name = n.trim();
            }
            cfg.setProperty("name", name);
            saveConfig();
            if (instances.isEmpty()) SwingUtilities.invokeAndWait(Tracker::pickInstances);
            if (instances.isEmpty()) System.exit(0);
        }
        signIn();
        while (true) {
            try {
                tick(instances);
            } catch (Exception e) {
                show("Error: " + e.getMessage());
            }
            Thread.sleep(500);
        }
    }

    /** Lets you pick one or more instance folders at once (ctrl-click), e.g. all four of a wall. */
    static void pickInstances() {
        File start = !instances.isEmpty() ? instanceRoot(instances.get(0)).getParentFile() : null;
        if (start == null || !start.isDirectory()) start = new File(System.getProperty("user.home"));
        JFileChooser c = new JFileChooser(start);
        c.setDialogTitle("Pick your 1.0 instance folders (ctrl-click to pick several)");
        c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        c.setMultiSelectionEnabled(true);
        c.setApproveButtonText("Use these");
        if (c.showOpenDialog(ui != null ? ui.frame : null) != JFileChooser.APPROVE_OPTION) return;
        File[] fs = c.getSelectedFiles();
        if (fs == null || fs.length == 0) fs = c.getSelectedFile() != null ? new File[] {c.getSelectedFile()} : new File[0];
        List<File> picked = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        for (File f : fs) {
            if (!new File(f, "level.dat").isFile() && !savesDir(f).getName().equals("saves")) bad.add(f.getName());
            else if (!picked.contains(f)) picked.add(f);
        }
        if (picked.isEmpty()) {
            JOptionPane.showMessageDialog(ui != null ? ui.frame : null, "Couldn't find a saves folder in what you picked. Pick the instance folders "
                + "(the ones with .minecraft or minecraft inside).", "Oldschool Pace", JOptionPane.WARNING_MESSAGE);
            return;
        }
        instances = picked;
        StringBuilder sb = new StringBuilder();
        for (File f : picked) sb.append(sb.length() > 0 ? File.pathSeparator : "").append(f.getPath());
        cfg.setProperty("saves", sb.toString());
        saveConfig();
        if (ui != null) ui.refresh();
        if (!bad.isEmpty()) show("Skipped (no saves folder): " + String.join(", ", bad));
    }

    /** A readable name for a picked folder: the instance's name, whichever folder inside it was picked. */
    static File instanceRoot(File f) {
        File s = savesDir(f);
        File mc = s.getParentFile();
        if (mc == null) return f;
        return (mc.getName().equals(".minecraft") || mc.getName().equals("minecraft")) && mc.getParentFile() != null ? mc.getParentFile() : mc;
    }

    static void changeName() {
        String n = JOptionPane.showInputDialog(ui.frame, "Your Minecraft name (shown in Discord):", name);
        if (n == null || !n.trim().matches("[A-Za-z0-9_]{1,16}") || n.trim().equals(name)) return;
        name = n.trim();
        cfg.setProperty("name", name);
        saveConfig();
        signedIn = false;
        ui.refresh();
        new Thread(() -> { try { signIn(); } catch (InterruptedException ignored) { } }).start();
    }

    /** Your Twitch name, linked from your name in the Discord posts. Empty removes it. */
    static void changeTwitch() {
        String t = JOptionPane.showInputDialog(ui.frame, "Your Twitch name (linked in Discord, leave empty for none):", cfg.getProperty("twitch", ""));
        if (t == null) return;
        t = t.trim().replaceFirst("(?i)^(https?://)?(www\\.)?twitch\\.tv/", "").replaceAll("/.*$", "");
        if (!t.matches("[A-Za-z0-9_]{0,25}")) { JOptionPane.showMessageDialog(ui.frame, "That isn't a Twitch name."); return; }
        cfg.setProperty("twitch", t);
        saveConfig();
        signedIn = false;
        ui.refresh();
        new Thread(() -> { try { signIn(); } catch (InterruptedException ignored) { } }).start();
    }

    static void saveConfig() {
        try (OutputStream out = new FileOutputStream(CONFIG)) {
            cfg.store(out, "Oldschool pace tracker. name: shown in Discord. saves: your 1.0 instance folders. twitch: linked in Discord. token: your sign-in, keep it private.");
        } catch (Exception e) {
            System.out.println("couldn't save " + CONFIG + ": " + e);
        }
    }

    /** Signs in once per name; the server hands back a private token, kept in pace.properties. */
    static void signIn() throws InterruptedException {
        String me = name;
        String tk0 = cfg.getProperty("token." + me.toLowerCase(), null);
        if (tk0 == null) {
            // a ranked player's own token works too (from the ranked mod, on this PC)
            Properties t = new Properties();
            File f = new File(HOME, "tokens.properties");
            if (f.exists()) try (InputStream in = new FileInputStream(f)) { t.load(in); } catch (Exception ignored) { }
            for (String k : t.stringPropertyNames()) if (k.toLowerCase().endsWith("." + me.toLowerCase()) || k.equalsIgnoreCase(me)) tk0 = t.getProperty(k);
        }
        while (me.equals(name)) {
            try {
                String r = post("/api/pace/hello", "{\"name\":\"" + me + "\"" + (tk0 != null ? ",\"token\":\"" + tk0 + "\"" : "")
                    + ",\"twitch\":\"" + cfg.getProperty("twitch", "") + "\"}");
                String tk = jsonStr(r, "token");
                if (tk != null) tk0 = tk;
                cfg.setProperty("token." + me.toLowerCase(), tk0);
                saveConfig();
                token = tk0;
                signedIn = true;
                if (ui != null) ui.refresh();
                show("Ready. Start a new world.");
                return;
            } catch (Exception e) {
                show("Can't sign in: " + e.getMessage() + ". Retrying...");
                if (e.getMessage() != null && e.getMessage().contains("another PC")) {
                    show("The name " + me + " is already used on another PC. Change it below.");
                    return;
                }
                Thread.sleep(10000);
            }
        }
    }

    /** Watches every world with a StatsPerWorld stats file: saves/<world>/stats/stats_<name>[_unsent].dat */
    static void tick(List<File> picked) {
        long now = System.currentTimeMillis();
        File shown = null;
        long shownMod = 0;
        boolean any = false;
        for (File p : picked) {
            File[] worlds = savesDir(p).listFiles();
            if (worlds == null) continue;
            for (File w : worlds) {
                // skip worlds nobody has touched in the last hour (walls make hundreds)
                if (!runs.containsKey(w) && now - w.lastModified() > 3600_000L) continue;
                long mod = 0;
                File[] fs = new File(w, "stats").listFiles();
                if (fs != null) for (File f : fs) if (f.getName().startsWith("stats_") && f.getName().endsWith(".dat")) mod = Math.max(mod, f.lastModified());
                if (mod == 0) continue;
                any = true;
                Run r = runs.get(w);
                if (r == null || mod != r.lastMod) {
                    Map<Integer, Long> st = readStats(new File(w, "stats"));
                    if (st == null) continue; // being written right now: try again next time
                    if (r == null) {
                        r = new Run();
                        runs.put(w, r);
                        Long t = st.get(PLAY_TIME);
                        // a run starts in a world first seen in its first 30 seconds, before anyone went to the Nether
                        if ((t == null || t < 600) && !new File(w, "DIM-1").exists()) r.id = Long.toHexString(new SecureRandom().nextLong() & Long.MAX_VALUE);
                    }
                    r.lastMod = mod;
                    r.stats = st;
                    Long pt = st.get(PLAY_TIME);
                    r.saves.addLast(new long[] {mod, pt == null ? 0 : pt});
                    if (DEBUG) System.out.println("stats " + mod + " pt=" + pt + " deaths=" + st.get(DEATHS) + " plates=" + (st.getOrDefault(USED + STONE_PLATE, 0L) + st.getOrDefault(USED + WOOD_PLATE, 0L))
                        + " nether=" + newestFile(new File(w, "DIM-1/region")) + " over=" + newestFile(new File(w, "region")));
                    while (r.saves.size() > 400) r.saves.removeFirst();
                }
                if (r.id != null && signedIn) check(w, r, now);
                if (mod > shownMod) { shownMod = mod; shown = w; }
            }
        }
        runs.entrySet().removeIf(e -> !e.getKey().isDirectory());
        if (!any) {
            show("Waiting for a world. Is StatsPerWorld installed in these instances?");
            if (ui != null) ui.run(null, null, 0);
            return;
        }
        if (shown == null) return;
        Run r = runs.get(shown);
        long t = time(r, now);
        if (ui != null) ui.run(shown.getName(), r, t);
        if (r.id == null) show("Old world: start a new one to track a run.");
        else if (signedIn) show(r.times.isEmpty() ? "Tracking this run." : "Tracking: last split " + SPLIT_NAMES[indexOf(r.times.keySet().toArray()[r.times.size() - 1])] + ".");
        if (ui == null) show(shown.getName() + " " + clock(t) + " " + r.times.keySet());
    }

    static int indexOf(Object s) {
        for (int i = 0; i < SPLITS.length; i++) if (SPLITS[i].equals(s)) return i;
        return 0;
    }

    /** Stats are saved every few seconds: add the time since, up to 10 seconds, so splits aren't late. */
    static long time(Run r, long now) {
        run = r;
        return stat(PLAY_TIME) + Math.max(0, Math.min(200, (now - r.lastMod) / 50));
    }

    static void check(File w, Run r, long now) {
        long time = time(r, now);
        // the game makes these folders the moment you first go to the Nether and the End
        long deaths = stat(DEATHS);
        if (new File(w, "DIM-1").isDirectory()) split("nether", time);
        // only file times are looked at, never what's in them
        long nether = newestFile(new File(w, "DIM-1/region")), over = newestFile(new File(w, "region"));
        // the fortress: mining a block only fortresses have (nether brick, its fence and stairs, nether wart)
        if (stat(MINED + 112) + stat(MINED + 113) + stat(MINED + 114) + stat(MINED + 115) > 0) split("fortress", time);
        // the pit: a pressure plate placed (stone or wood), then a death (the reset to spawn), in the overworld before
        // the stronghold. Works for first day Nether (pit after) and second day Nether (pit before).
        long plates = plates();
        if (plates > r.lastPlates) { r.deathsAtPlate = r.lastDeaths; r.netherAtPlate = nether; } // a plate and a death in one save count too
        r.lastPlates = plates;
        r.lastDeaths = deaths;
        // only one dimension runs at a time, so the Nether's files don't change unless you were in it (dying there saves it)
        if (r.deathsAtPlate >= 0 && deaths > r.deathsAtPlate && nether == r.netherAtPlate && !r.stronghold) split("pit", time);
        if (stat(USED + ENDER_EYE) > 0) r.stronghold = true; // an eye put in an end portal frame
        if (new File(w, "DIM1").isDirectory()) {
            if (!r.times.containsKey("end")) r.deathsAtEnd = stat(DEATHS);
            split("end", time);
            // back in the overworld after the End without dying since: through the exit portal. Leaving the End saves
            // it; the stats are read from a few seconds after that, so a death before it would already show.
            long end = newestFile(new File(w, "DIM1/region"));
            if (end > 0 && over > end && r.lastMod > end + 6000 && stat(DEATHS) == r.deathsAtEnd) {
                split("finish", Math.max(r.sent, Math.min(time, r.playTimeAt(end))));
            }
        }
    }

    /** Takes whatever folder was picked (an instance, .minecraft, saves or a world) and finds the saves folder. */
    static File savesDir(File f) {
        if (new File(f, "level.dat").isFile()) return f.getParentFile();
        for (String sub : new String[] {"saves", ".minecraft/saves", "minecraft/saves"}) {
            File s = new File(f, sub);
            if (s.isDirectory()) return s;
        }
        return f;
    }

    /** The window: just whether it's connected and what it's watching. Split times only go to Discord. */
    static final class Ui {
        static final Color BG = new Color(0x131317), TEXT = new Color(0xf2f2f5), DIM = new Color(0x7a7a86), GOOD = new Color(0x4cd17f), BAD = new Color(0xe5a050);
        final JFrame frame = new JFrame("Oldschool Pace");
        final JLabel dot = label("\u25cf", 11, DIM, Font.PLAIN), title = label("Oldschool Pace", 14, TEXT, Font.BOLD),
            line = label(" ", 12, DIM, Font.PLAIN), gear = label("\u2699", 16, DIM, Font.PLAIN);
        volatile String problem, watching = " ";

        Ui() {
            JPanel root = new JPanel(new BorderLayout(10, 6));
            root.setBackground(BG);
            root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 14));
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            left.add(dot);
            left.add(title);
            root.add(left, BorderLayout.NORTH);
            line.setBorder(BorderFactory.createEmptyBorder(0, 27, 0, 0));
            root.add(line, BorderLayout.CENTER);
            gear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            gear.setToolTipText("Settings");
            gear.setVerticalAlignment(JLabel.TOP);
            gear.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) { menu().show(gear, 0, gear.getHeight()); }
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { gear.setForeground(TEXT); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { gear.setForeground(DIM); }
            });
            root.add(gear, BorderLayout.EAST);
            frame.setContentPane(root);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setSize(300, frame.getHeight());
            frame.setResizable(false);
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
            refresh();
        }

        javax.swing.JPopupMenu menu() {
            javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
            List<String> ns = new ArrayList<>();
            for (File f : instances) ns.add(instanceRoot(f).getName());
            javax.swing.JMenuItem info = new javax.swing.JMenuItem((signedIn ? "Signed in as " : "Signing in as ") + name);
            info.setEnabled(false);
            m.add(info);
            m.addSeparator();
            javax.swing.JMenuItem a = new javax.swing.JMenuItem("Change instances (" + ns.size() + ")");
            a.setToolTipText(String.join(", ", ns));
            a.addActionListener(e -> pickInstances());
            m.add(a);
            javax.swing.JMenuItem b = new javax.swing.JMenuItem("Change name");
            b.addActionListener(e -> changeName());
            m.add(b);
            String tw = cfg.getProperty("twitch", "");
            javax.swing.JMenuItem c = new javax.swing.JMenuItem(tw.isEmpty() ? "Add Twitch" : "Twitch: " + tw);
            c.addActionListener(e -> changeTwitch());
            m.add(c);
            return m;
        }

        static JLabel label(String s, int size, Color c, int style) {
            JLabel l = new JLabel(s);
            l.setFont(l.getFont().deriveFont(style, (float) size));
            l.setForeground(c);
            return l;
        }

        void refresh() {
            int n = instances.size();
            watching = signedIn ? "Watching " + n + (n == 1 ? " instance" : " instances") : "Connecting...";
            paint();
        }

        /** Only problems replace the normal line. */
        void status(String s) {
            boolean bad = s.startsWith("Can't") || s.startsWith("Waiting") || s.startsWith("Error") || s.startsWith("Skipped") || s.contains("another PC");
            problem = bad ? s : null;
            paint();
        }

        String painted = "";

        /** Redraws only when something actually changed (it's called twice a second). */
        void paint() {
            final String p = problem;
            final String text = p != null ? p : watching;
            final Color c = p != null ? BAD : signedIn ? GOOD : DIM;
            String key = text + c.getRGB();
            synchronized (this) {
                if (key.equals(painted)) return;
                painted = key;
            }
            SwingUtilities.invokeLater(() -> {
                dot.setForeground(c);
                line.setForeground(p != null ? BAD : DIM);
                line.setText("<html><body style='width:200px'>" + text + "</body></html>");
                int h = frame.getContentPane().getPreferredSize().height + frame.getInsets().top + frame.getInsets().bottom;
                if (frame.getHeight() != h) frame.setSize(300, h);
            });
        }

        void run(String worldName, Run r, long t) { }
    }

    static long newestFile(File dir) {
        long t = 0;
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) t = Math.max(t, f.lastModified());
        return t;
    }

    static long plates() {
        return stat(USED + STONE_PLATE) + stat(USED + WOOD_PLATE);
    }

    static long stat(int id) {
        Long v = run.stats.get(id);
        return v == null ? 0 : v;
    }

    /** Adds up the stats in both files (the game moves changes from _unsent into the other one). */
    static Map<Integer, Long> readStats(File dir) {
        Map<Integer, Long> out = new java.util.HashMap<>();
        File[] fs = dir.listFiles();
        if (fs == null) return null;
        for (File f : fs) {
            if (!f.getName().startsWith("stats_") || !f.getName().endsWith(".dat")) continue;
            String json;
            try {
                json = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return null;
            }
            int i = json.indexOf("\"stats-change\"");
            int j = json.indexOf(']', Math.max(i, 0));
            if (i < 0 || j < 0) return null; // half written
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(\\d+)\"\\s*:\\s*(\\d+)").matcher(json.substring(i, j));
            while (m.find()) out.merge(Integer.parseInt(m.group(1)), Long.parseLong(m.group(2)), Long::sum);
        }
        return out;
    }

    static void split(String s, long ticks) {
        if (run.times.containsKey(s)) return;
        run.times.put(s, ticks);
        run.sent = Math.max(run.sent, ticks);
        if (DEBUG) System.out.println("split " + s + " " + clock(ticks) + " at " + System.currentTimeMillis());
        final String body = "{\"token\":\"" + token + "\",\"run\":\"" + run.id + "\",\"split\":\"" + s + "\",\"igt\":" + ticks + "}";
        new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                try {
                    post("/api/pace", body);
                    return;
                } catch (Exception e) {
                    System.out.println("pace " + s + ": " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().startsWith("HTTP 4")) return;
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) { return; }
                }
            }
        }).start();
    }

    static String clock(long ticks) {
        long s = ticks / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    static void show(String s) {
        if (ui != null) ui.status(s);
        else if (!s.equals(lastShown)) System.out.println(s);
        lastShown = s;
    }

    static String post(String path, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(SERVER + path).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = c.getOutputStream()) { o.write(json.getBytes(StandardCharsets.UTF_8)); }
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        byte[] b = new byte[65536];
        int n = 0, r;
        if (in != null) while ((r = in.read(b, n, b.length - n)) > 0 && n < b.length) n += r;
        String s = new String(b, 0, n, StandardCharsets.UTF_8);
        if (code >= 400) {
            String err = jsonStr(s, "error");
            throw new Exception("HTTP " + code + (err != null ? ": " + err : ""));
        }
        return s;
    }

    static String jsonStr(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
