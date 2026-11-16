package smartgrid;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read-only live dashboard. Pushes server events to browsers using
 * Server-Sent Events (plain HTTP, built into the JDK, no libraries).
 * Failures here never affect the traffic server.
 */
public class DashboardHub {
    private static final List<BlockingQueue<String>> subscribers = new CopyOnWriteArrayList<BlockingQueue<String>>();
    private static final Map<String, String> state = new LinkedHashMap<String, String>();
    private static final Deque<String> recentLogs = new ArrayDeque<String>();
    private static final AtomicLong udp = new AtomicLong(), cmd = new AtomicLong(),
            ped = new AtomicLong(), emg = new AtomicLong();

    public static void publish(String type, String data) {
        try {
            long ts = System.currentTimeMillis();
            String json = "{\"t\":\"" + type + "\",\"d\":\"" + esc(data) + "\",\"ts\":" + ts + "}";
            if ("traffic".equals(type)) udp.incrementAndGet();
            else if ("command".equals(type)) cmd.incrementAndGet();
            else if ("ped".equals(type)) ped.incrementAndGet();
            else if ("emerg".equals(type)) emg.incrementAndGet();
            synchronized (state) {
                if ("log".equals(type)) {
                    recentLogs.addLast(json);
                    if (recentLogs.size() > 120) recentLogs.removeFirst();
                } else if ("join".equals(type) || "leave".equals(type)) {
                    state.put("conn:" + data, json);
                } else if ("traffic".equals(type) || "command".equals(type)) {
                    String[] p = data.split("\\|");
                    if (p.length > 1) state.put(type + ":" + p[1], json);
                }
            }
            for (BlockingQueue<String> q : subscribers) q.offer(json);
        } catch (Exception ignored) {
            // dashboard problems must never disturb the server
        }
    }

    /** Mirrors everything printed on the server console to the dashboard, console output stays unchanged. */
    public static void installConsoleTee() {
        final PrintStream original = System.out;
        System.setOut(new PrintStream(new OutputStream() {
            private final StringBuilder line = new StringBuilder();
            public synchronized void write(int b) {
                original.write(b);
                if (b == '\n') {
                    String s = line.toString().replace("\r", "");
                    line.setLength(0);
                    if (!s.isEmpty()) publish("log", s);
                } else {
                    line.append((char) (b & 0xff));
                }
            }
            public void flush() { original.flush(); }
        }, true));
    }

    public static void start(int port) {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/events", DashboardHub::events);
            http.createContext("/", DashboardHub::page);
            http.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "Dashboard-HTTP");
                t.setDaemon(true);
                return t;
            }));
            http.start();
            System.out.println("[DASHBOARD] Open http://localhost:" + port
                    + " here, or http://<this-laptop-IP>:" + port + " from another laptop.");
        } catch (Exception ex) {
            System.out.println("[DASHBOARD] Not started (" + ex.getMessage() + "). Traffic server is unaffected.");
        }
    }

    private static void page(HttpExchange ex) throws IOException {
        Path file = Paths.get("web", "dashboard.html");
        byte[] body;
        int code = 200;
        if (Files.isReadable(file)) {
            body = Files.readAllBytes(file);
        } else {
            code = 404;
            body = ("dashboard.html not found. Run the server from the project folder (it needs web/dashboard.html). Looked in: "
                    + file.toAbsolutePath()).getBytes(StandardCharsets.UTF_8);
        }
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private static void events(HttpExchange ex) throws IOException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<String>(500);
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);
        OutputStream os = ex.getResponseBody();
        try {
            StringBuilder first = new StringBuilder();
            synchronized (state) {
                first.append(frame("{\"t\":\"hello\",\"d\":\"" + udp + "|" + cmd + "|" + ped + "|" + emg
                        + "\",\"ts\":" + System.currentTimeMillis() + "}"));
                for (String s : state.values()) first.append(frame(replay(s)));
                for (String s : recentLogs) first.append(frame(replay(s)));
                subscribers.add(queue);
            }
            os.write(first.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            while (true) {
                String json = queue.poll(15, TimeUnit.SECONDS);
                os.write((json == null ? ": ping\n\n" : frame(json)).getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (IOException | InterruptedException done) {
            // browser closed the page
        } finally {
            subscribers.remove(queue);
            try { os.close(); } catch (IOException ignored) { }
        }
    }

    private static String replay(String json) { return json.substring(0, json.length() - 1) + ",\"r\":1}"; }
    private static String frame(String json) { return "data: " + json + "\n\n"; }
    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\t", " "); }
}
