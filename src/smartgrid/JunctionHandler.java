package smartgrid;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** One TCP handler thread per connected junction. */
public class JunctionHandler extends Thread {
    private final Socket socket;
    private final TrafficStore store;
    private final Map<String, PrintWriter> clients;
    private String junctionId;
    private PrintWriter out;

    public JunctionHandler(Socket socket, TrafficStore store,
            Map<String, PrintWriter> clients) {
        this.socket = socket;
        this.store = store;
        this.clients = clients;
        setName("JunctionHandler-" + socket.getRemoteSocketAddress());
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true);

            String hello = in.readLine();
            if (hello == null || !hello.startsWith("HELLO|")) {
                System.out.println("[TCP] Client did not identify itself. Closing connection.");
                return;
            }

            junctionId = hello.substring("HELLO|".length()).trim();
            if (!junctionId.matches("J0[1-3]")) {
                System.out.println("[TCP] Invalid junction ID: " + junctionId);
                return;
            }

            synchronized (clients) {
                if (clients.containsKey(junctionId)) {
                    out.println("ERROR|Junction already connected");
                    System.out.println("[TCP] Duplicate connection rejected: " + junctionId);
                    return;
                }
                clients.put(junctionId, out);
            }

            System.out.println("[TCP] " + junctionId + " connected from "
                    + socket.getInetAddress().getHostAddress());
            out.println("WELCOME|" + junctionId);

            String line;
            while ((line = in.readLine()) != null) {
                try {
                    if (line.startsWith("PED_REQUEST|")) {
                        PedestrianRequest request = PedestrianRequest.fromPacketString(line);
                        if (!junctionId.equals(request.getJunctionId())) {
                            throw new IllegalArgumentException("Request junction does not match connection");
                        }
                        store.addPedestrianRequest(request);
                        System.out.println("[REQUEST] Pedestrian request: " + line);
                        out.println("ACK|PED_REQUEST");
                    } else if (line.startsWith("EMERGENCY_REQUEST|")) {
                        EmergencyRequest request = EmergencyRequest.fromPacketString(line);
                        if (!junctionId.equals(request.getJunctionId())) {
                            throw new IllegalArgumentException("Request junction does not match connection");
                        }
                        store.addEmergencyRequest(request);
                        System.out.println("[REQUEST] Emergency request: " + line);
                        out.println("ACK|EMERGENCY_REQUEST");
                    } else {
                        System.out.println("[TCP] Unknown message from " + junctionId + ": " + line);
                        out.println("ERROR|Unknown message");
                    }
                } catch (IllegalArgumentException ex) {
                    out.println("ERROR|" + ex.getMessage());
                    System.out.println("[TCP] Invalid message: " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            System.out.println("[TCP] " + junctionId + " connection ended: " + ex.getMessage());
        } finally {
            if (junctionId != null && out != null) {
                synchronized (clients) {
                    if (clients.get(junctionId) == out) {
                        clients.remove(junctionId);
                    }
                }
                System.out.println("[TCP] " + junctionId + " disconnected");
            }
            try { socket.close(); } catch (Exception ignored) { }
        }
    }
}
