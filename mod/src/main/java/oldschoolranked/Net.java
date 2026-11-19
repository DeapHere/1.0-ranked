package oldschoolranked;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The match server connection: plain HTTPS requests plus one long-poll thread for events. Everything runs off the
 * game thread; results come back through {@link #inbox} and are handled in the client tick.
 */
public final class Net {
    public static final int PROTOCOL = 2; // 2: 1.0.6 (no Peaceful). Bump to lock out older mods.

    /** Something that happened off the game thread: a server event, or the reply (or error) of a request. */
    public static final class Message {
        public final String type;
        public final Map<String, Object> data;

        Message(String type, Map<String, Object> data) {
            this.type = type;
            this.data = data;
        }
    }

    public interface Reply {
        void handle(Map<String, Object> body, String error);
    }

    public static final ConcurrentLinkedQueue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    private static final ExecutorService requests = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Ranked requests");
        t.setDaemon(true);
        return t;
    });

    public static volatile String token;
    /** Server clock minus ours, in ms. */
    public static volatile long clockOffset;
    private static volatile long since;
    private static Thread poller;
    public static volatile boolean connected;
    /** The last poll failed (shown as Reconnecting). False before the first poll, so a fresh sign-in doesn't show it. */
    public static volatile boolean failing;

    private Net() {
    }

    public static long serverNow() {
        return System.currentTimeMillis() + clockOffset;
    }

    /** Sends a POST in the background; the reply runs on the game thread. The token is added automatically. */
    public static void post(String path, Map<String, Object> body, Reply reply) {
        final Map<String, Object> b = body == null ? new LinkedHashMap<>() : body;
        if (token != null && !b.containsKey("token")) b.put("token", token);
        requests.execute(() -> {
            Map<String, Object> res = null;
            String err = null;
            try {
                res = request("POST", path, Json.write(b), 15000);
            } catch (ServerError e) {
                err = e.getMessage();
            } catch (Exception e) {
                err = "can't reach the ranked server";
                Ranked.log("POST " + path + " failed: " + e);
            }
            final Map<String, Object> r = res;
            final String er = err;
            if (reply != null) inbox.add(() -> reply.handle(r, er));
        });
    }

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static final class ServerError extends IOException {
        ServerError(String msg) {
            super(msg);
        }
    }

    static Map<String, Object> request(String method, String path, String body, int timeout) throws IOException {
        URL url = new URL(Ranked.cfg.server.replaceAll("/+$", "") + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10000);
        c.setReadTimeout(timeout);
        c.setRequestProperty("User-Agent", "OldschoolRanked/" + Ranked.VERSION);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = c.getResponseCode();
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = in == null ? "" : read(in);
        Map<String, Object> m;
        try {
            m = Json.obj(text);
        } catch (RuntimeException e) {
            throw new IOException("bad reply (" + status + ")");
        }
        if (status >= 400) throw new ServerError(Json.str(m, "error", "server error " + status));
        Object t = m.get("serverTime");
        if (t instanceof Number) clockOffset = ((Number) t).longValue() - System.currentTimeMillis();
        return m;
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = i.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Starts the event loop once signed in. Events seen before {@code from} are skipped. */
    public static synchronized void startPolling(long from) {
        since = from;
        if (poller != null) return;
        poller = new Thread(Net::pollLoop, "Ranked events");
        poller.setDaemon(true);
        poller.start();
    }

    @SuppressWarnings("unchecked")
    private static void pollLoop() {
        int failures = 0;
        while (true) {
            try {
                String t = token;
                if (t == null) {
                    Thread.sleep(1000);
                    continue;
                }
                Map<String, Object> r = request("GET", "/api/poll?token=" + URLEncoder.encode(t, "UTF-8") + "&since=" + since, null, 35000);
                connected = true;
                failing = false;
                failures = 0;
                // events first: a result must be seen before the state that says the match is over
                for (Object o : (List<Object>) r.get("events")) {
                    Map<String, Object> ev = (Map<String, Object>) o;
                    since = Math.max(since, Json.num(ev, "seq", since));
                    inbox.add(() -> Ranked.onEvent(ev));
                }
                final Map<String, Object> state = r;
                inbox.add(() -> Ranked.onPollState(state));
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                connected = false;
                failing = true;
                failures++;
                if (failures == 1 || failures % 10 == 0) Ranked.log("poll failed: " + e);
                try {
                    Thread.sleep(Math.min(10000, 1000L * failures));
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }
}
