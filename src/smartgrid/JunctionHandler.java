package smartgrid;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** One TCP handler thread per connected junction. */
public class JunctionHandler extends Thread {
    private final Socket socket;
    private final TrafficStore store;
    private final Map<String, BlockingQueue<String>> clients;
    private String junctionId;
    private PrintWriter out;
    private BlockingQueue<String> outbound;
    private Thread writerThread;
    private boolean registered;

    public JunctionHandler(Socket socket, TrafficStore store,
            Map<String, BlockingQueue<String>> clients) {
        this.socket = socket;
        this.store = store;
        this.clients = clients;
    }

    public void run() {
        Thread.currentThread().setName("JunctionHandler-" + socket.getRemoteSocketAddress());
        try {
            socket.setKeepAlive(true);
            socket.setSoTimeout(SmartGridConfig.TCP_HANDSHAKE_TIMEOUT_MS);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true);

            String hello = readLimitedLine(in);
            if (hello == null || !hello.startsWith("HELLO|")) {
                System.out.println("[TCP] Client did not identify itself. Closing connection.");
                return;
            }

            junctionId = hello.substring("HELLO|".length()).trim().toUpperCase();
            if (!SmartGridConfig.isValidJunctionId(junctionId)) {
                send("ERROR|Invalid junction ID. Use J01, J02 or J03");
                return;
            }

            synchronized (clients) {
                if (clients.containsKey(junctionId)) {
                    out.println("ERROR|Junction already connected");
                    System.out.println("[TCP] Duplicate connection rejected: " + junctionId);
                    return;
                }
                outbound = new ArrayBlockingQueue<String>(100);
                clients.put(junctionId, outbound);
                store.registerJunction(junctionId, socket.getInetAddress());
                registered = true;
            }

            socket.setSoTimeout(SmartGridConfig.TCP_HEARTBEAT_TIMEOUT_MS);
            startWriterThread();
            DashboardHub.publish("join", junctionId);
            System.out.println("[TCP] " + junctionId + " connected from "
                    + socket.getInetAddress().getHostAddress());
            send("WELCOME|" + junctionId);

            String line;
            while ((line = readLimitedLine(in)) != null) {
                if (line.length() == 0) continue;
                try {
                    if (line.startsWith("HEARTBEAT|")) {
                        String heartbeatId = line.substring("HEARTBEAT|".length()).trim();
                        if (!junctionId.equals(heartbeatId)) {
                            throw new IllegalArgumentException("Heartbeat junction does not match connection");
                        }
                        send("PONG|" + System.currentTimeMillis());
                    } else if (line.startsWith("PED_REQUEST|")) {
                        PedestrianRequest request = PedestrianRequest.fromPacketString(line);
                        if (!junctionId.equals(request.getJunctionId())) {
                            throw new IllegalArgumentException("Request junction does not match connection");
                        }
                        if (store.addPedestrianRequest(request)) {
                            send("ACK|PED_REQUEST|QUEUED");
                            DashboardHub.publish("ped", line);
                            DashboardHub.publish("ack", "ACK|PED|" + junctionId + "|OK|QUEUED|via junction console");
                            DashboardHub.publishRequestState(store, junctionId);
                            System.out.println("[REQUEST] Pedestrian request queued for " + junctionId);
                        } else {
                            send("ERROR|Pedestrian request queue is full");
                            DashboardHub.publish("ack", "ACK|PED|" + junctionId + "|ERROR|Pedestrian request queue is full");
                        }
                    } else if (line.startsWith("EMERGENCY_REQUEST|")) {
                        EmergencyRequest request = EmergencyRequest.fromPacketString(line);
                        if (!junctionId.equals(request.getJunctionId())) {
                            throw new IllegalArgumentException("Request junction does not match connection");
                        }
                        if (store.addEmergencyRequest(request)) {
                            send("ACK|EMERGENCY_REQUEST|QUEUED");
                            DashboardHub.publish("emerg", line);
                            DashboardHub.publish("ack", "ACK|EMERG|" + junctionId + "|OK|QUEUED|via junction console");
                            DashboardHub.publishRequestState(store, junctionId);
                            System.out.println("[REQUEST] Emergency request queued for " + junctionId);
                        } else {
                            send("ERROR|Emergency request queue is full");
                            DashboardHub.publish("ack", "ACK|EMERG|" + junctionId + "|ERROR|Emergency request queue is full");
                        }
                    } else {
                        send("ERROR|Unknown message");
                    }
                } catch (IllegalArgumentException ex) {
                    send("ERROR|" + ex.getMessage());
                    DashboardHub.publish("ack", "ACK|REQUEST|" + junctionId + "|ERROR|"
                            + String.valueOf(ex.getMessage()).replace('|', '/'));
                    System.out.println("[TCP] Invalid message from " + junctionId + ": " + ex.getMessage());
                }
            }
        } catch (SocketTimeoutException ex) {
            if (registered) {
                System.out.println("[TCP] " + junctionId + " heartbeat timeout; closing stale connection");
            }
        } catch (Exception ex) {
            if (registered) {
                System.out.println("[TCP] " + junctionId + " connection ended: " + ex.getMessage());
            }
        } finally {
            if (registered) {
                // Only the session that owns this junction ID may clear its data,
                // and the check + removal happen under one lock, so a quick
                // reconnect can never have its fresh registration wiped.
                synchronized (clients) {
                    if (clients.get(junctionId) == outbound) {
                        clients.remove(junctionId);
                        store.unregisterJunction(junctionId);
                    }
                }
                DashboardHub.publish("leave", junctionId);
                DashboardHub.publishRequestState(store, junctionId);
                System.out.println("[TCP] " + junctionId + " disconnected");
            }
            if (writerThread != null) writerThread.interrupt();
            try { socket.close(); } catch (Exception ignored) { }
        }
    }

    private String readLimitedLine(BufferedReader in) throws Exception {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = in.read()) != -1) {
            if (value == '\n') break;
            if (value != '\r') {
                line.append((char) value);
                if (line.length() > SmartGridConfig.MAX_TCP_LINE_LENGTH) {
                    throw new IllegalArgumentException("TCP message is too long");
                }
            }
        }
        if (value == -1 && line.length() == 0) return null;
        return line.toString();
    }

    private void startWriterThread() {
        writerThread = new Thread(new Runnable() {
            public void run() {
                Thread.currentThread().setName("TCP-Writer-" + junctionId);
                try {
                    while (!socket.isClosed()) {
                        String message = outbound.take();
                        out.println(message);
                        if (out.checkError()) {
                            System.out.println("[TCP] Write failed for " + junctionId);
                            socket.close();
                            break;
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    try { socket.close(); } catch (Exception ignored) { }
                }
            }
        }, "TCP-Writer-" + junctionId);
        writerThread.setDaemon(true);
        writerThread.start();
    }

    private void send(String message) {
        if (outbound != null) {
            if (!outbound.offer(message)) {
                try { socket.close(); } catch (Exception ignored) { }
                throw new IllegalStateException("Outbound message queue is full");
            }
        } else {
            out.println(message);
            if (out.checkError()) throw new IllegalStateException("TCP write failed");
        }
    }
}
