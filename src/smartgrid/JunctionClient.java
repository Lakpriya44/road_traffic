package smartgrid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Junction client. Run one process for each junction ID. */
public class JunctionClient {
    private static volatile boolean automaticMode = true;
    private static volatile boolean running = true;
    private static PrintWriter tcpOut;
    private static UDPClient udpClient;
    private static String junctionId;
    // Last snapshot typed with the 'traffic' command. It is re-sent every
    // update interval, because the server discards data older than 10 s.
    private static volatile TrafficUpdate lastManualUpdate;
    private static long lastTimestamp = 0L;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: JunctionClient <J01|J02|J03> [server-host]");
            System.out.println("Example: JunctionClient J01 127.0.0.1");
            return;
        }

        junctionId = args[0].trim().toUpperCase();
        String serverHost = args.length >= 2 ? args[1].trim() : "127.0.0.1";
        if (!SmartGridConfig.isValidJunctionId(junctionId)) {
            System.out.println("Junction ID must be J01, J02 or J03.");
            return;
        }

        running = true;
        automaticMode = true;
        lastManualUpdate = null;
        Socket socket = new Socket();
        Thread receiver = null;
        Thread sender = null;
        Thread signalWorker = null;
        UDPClient localUdpClient = null;

        try {
            socket.connect(new InetSocketAddress(serverHost, TrafficCenter.TCP_PORT),
                    SmartGridConfig.TCP_CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(SmartGridConfig.TCP_HANDSHAKE_TIMEOUT_MS);
            tcpOut = new PrintWriter(socket.getOutputStream(), true);
            final BufferedReader tcpIn = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));

            tcpOut.println("HELLO|" + junctionId);
            if (tcpOut.checkError()) throw new IOException("Could not send HELLO to server");
            String welcome = tcpIn.readLine();
            if (welcome == null || !welcome.startsWith("WELCOME|" + junctionId)) {
                throw new IOException(welcome == null ? "Server closed the connection during login" : welcome);
            }
            socket.setSoTimeout(0);

            localUdpClient = new UDPClient(serverHost, TrafficCenter.UDP_PORT);
            udpClient = localUdpClient;
            final TrafficSimulator simulator = new TrafficSimulator();
            final BlockingQueue<SignalCommand> signalQueue =
                    new LinkedBlockingQueue<SignalCommand>();
            final Socket clientSocket = socket;

            System.out.println("==============================================");
            System.out.println("          SMARTGRID " + junctionId + " CLIENT");
            System.out.println("==============================================");
            System.out.println("Connected to server " + serverHost);
            System.out.println("Automatic traffic simulation is ON.");
            System.out.println("TCP heartbeat is enabled; disconnects are detected automatically.");

            // This thread only receives messages. It never sleeps for a signal phase.
            receiver = new Thread(new Runnable() {
                public void run() {
                    try {
                        String line;
                        while (running && (line = tcpIn.readLine()) != null) {
                            if (line.startsWith("SIGNAL_COMMAND|")) {
                                SignalCommand command = SignalCommand.fromPacketString(line);
                                if (!junctionId.equals(command.getJunctionId())) {
                                    System.out.println("[TCP] Ignored command for another junction");
                                } else {
                                    signalQueue.offer(command);
                                }
                            } else if (line.startsWith("PONG|")) {
                                // Heartbeat response; no need to print every one.
                            } else {
                                System.out.println("[SERVER] " + line);
                            }
                        }
                    } catch (Exception ex) {
                        if (running) System.out.println("[TCP] Server connection ended: " + ex.getMessage());
                    } finally {
                        running = false;
                    }
                }
            }, "Server-Message-Receiver");
            receiver.setDaemon(true);
            receiver.start();

            // Signal display runs separately so the TCP receiver remains responsive.
            signalWorker = new Thread(new Runnable() {
                public void run() {
                    while (running) {
                        try {
                            SignalCommand command = signalQueue.take();
                            displayCommand(command, simulator);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }, "Signal-Phase-Display");
            signalWorker.setDaemon(true);
            signalWorker.start();

            sender = new Thread(new Runnable() {
                public void run() {
                    long lastHeartbeat = 0L;
                    while (running) {
                        try {
                            if (automaticMode) {
                                sendTraffic(simulator.generateRandom(junctionId));
                                System.out.println("[UDP] Traffic update sent: " + junctionId);
                            } else {
                                TrafficUpdate manual = lastManualUpdate;
                                if (manual != null) sendTraffic(manual);
                            }
                            long now = System.currentTimeMillis();
                            if (now - lastHeartbeat >= SmartGridConfig.CLIENT_HEARTBEAT_INTERVAL_MS) {
                                sendTcp("HEARTBEAT|" + junctionId);
                                lastHeartbeat = now;
                            }
                        } catch (Exception ex) {
                            System.out.println("[CLIENT] Communication failed: " + ex.getMessage());
                            running = false;
                            break;
                        }
                        try {
                            Thread.sleep(SmartGridConfig.TRAFFIC_UPDATE_INTERVAL_MS);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }, "Traffic-Sensor-Simulator");
            sender.setDaemon(true);
            sender.start();

            // Console input is daemonized. The main thread can exit immediately
            // when the server disconnects, even if the user is not typing.
            Thread inputThread = new Thread(new Runnable() {
                public void run() {
                    runConsole(clientSocket);
                }
            }, "Junction-Console-Input");
            inputThread.setDaemon(true);
            inputThread.start();

            while (running) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception ex) {
            System.out.println("[CLIENT] Could not connect: " + ex.getMessage());
            System.out.println("Start TrafficCenter first and check the server IP/port.");
        } finally {
            running = false;
            try { socket.close(); } catch (Exception ignored) { }
            if (localUdpClient != null) localUdpClient.close();
            if (sender != null) sender.interrupt();
            if (receiver != null) receiver.interrupt();
            if (signalWorker != null) signalWorker.interrupt();
            System.out.println(junctionId + " client stopped.");
        }
    }

    private static void runConsole(Socket socket) {
        Scanner scanner = new Scanner(System.in);
        printHelp();
        while (running) {
            System.out.print(junctionId + "> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.length() == 0) continue;

            String[] parts = line.split("\\s+");
            String command = parts[0].toLowerCase();
            try {
                if ("help".equals(command)) {
                    printHelp();
                } else if ("auto".equals(command)) {
                    automaticMode = true;
                    System.out.println("Automatic traffic simulation ON.");
                } else if ("manual".equals(command)) {
                    automaticMode = false;
                    System.out.println("Automatic traffic simulation OFF. Use 'traffic' to enter data;"
                            + " the last entered snapshot is re-sent every 3 s.");
                } else if ("traffic".equals(command)) {
                    automaticMode = false;
                    TrafficUpdate update = TrafficSimulator.readManual(junctionId, scanner);
                    lastManualUpdate = update;
                    sendTraffic(update);
                    System.out.println("[UDP] Manual traffic update sent"
                            + " (re-sent every 3 s until you type 'auto').");
                } else if ("ped".equals(command) && parts.length == 3) {
                    PedestrianRequest request = new PedestrianRequest(junctionId,
                            parts[1], Integer.parseInt(parts[2]), System.currentTimeMillis());
                    sendTcp(request.toPacketString());
                } else if ("emergency".equals(command) && parts.length == 3) {
                    EmergencyRequest request = new EmergencyRequest(junctionId,
                            parts[1], parts[2], System.currentTimeMillis());
                    sendTcp(request.toPacketString());
                } else if ("quit".equals(command)) {
                    running = false;
                    try { socket.close(); } catch (Exception ignored) { }
                    break;
                } else {
                    System.out.println("Unknown command. Type help.");
                }
            } catch (Exception ex) {
                System.out.println("[INPUT] " + ex.getMessage());
                System.out.println("Check the command format and try again.");
            }
        }
    }

    /** Every UDP snapshot gets a strictly increasing timestamp so the server never rejects it as out-of-order. */
    private static void sendTraffic(TrafficUpdate update) throws Exception {
        udpClient.send(restamp(update));
    }

    private static synchronized TrafficUpdate restamp(TrafficUpdate u) {
        lastTimestamp = Math.max(System.currentTimeMillis(), lastTimestamp + 1);
        return new TrafficUpdate(u.getJunctionId(), lastTimestamp,
                u.getNorth(), u.getEast(), u.getSouth(), u.getWest());
    }

    private static void sendTcp(String message) throws IOException {
        synchronized (tcpOut) {
            tcpOut.println(message);
            if (tcpOut.checkError()) {
                running = false;
                throw new IOException("TCP connection is not available");
            }
        }
        if (!message.startsWith("HEARTBEAT|")) {
            System.out.println("[TCP] Sent: " + message);
        }
    }

    private static void displayCommand(SignalCommand command, TrafficSimulator simulator) {
        try {
            System.out.println();
            System.out.println("========== SIGNAL CHANGE: " + junctionId + " ==========");
            System.out.println("Received: " + command.toPacketString());
            System.out.println("YELLOW: current vehicle phase changing...");
            Thread.sleep(1000L);
            System.out.println("ALL RED: clearance phase...");
            Thread.sleep(1000L);

            if ("PEDESTRIAN".equals(command.getPhaseType())) {
                System.out.println("ALL VEHICLE SIGNALS: RED");
                System.out.println("PEDESTRIAN WALK: " + command.getPhaseName());
            } else {
                System.out.println("GREEN: " + command.getPhaseName());
                System.out.println("Other approaches: RED");
                if ("EMERGENCY".equals(command.getMode())) {
                    System.out.println("EMERGENCY PRIORITY: " + command.getPriorityVehicle());
                }
            }
            System.out.println("Active duration: " + command.getDurationSeconds() + " seconds");
            Thread.sleep(command.getDurationSeconds() * 1000L);

            if ("VEHICLE".equals(command.getPhaseType())) {
                simulator.applyGreen(command.getPhaseName(), command.getDurationSeconds());
            }
            System.out.println("Phase completed: " + command.getPhaseName());
            System.out.println("==============================================");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  help                         Show commands");
        System.out.println("  auto                         Enable automatic traffic data");
        System.out.println("  manual                       Stop automatic traffic data");
        System.out.println("  traffic                      Enter manual counts and send UDP update");
        System.out.println("  ped NORTH 4                  Request pedestrian crossing (N/E/S/W)");
        System.out.println("  emergency NORTH AMBULANCE    Request emergency priority");
        System.out.println("  quit                         Disconnect this junction");
        System.out.println();
        System.out.println("Manual traffic format for each approach:");
        System.out.println("  LEFT STRAIGHT RIGHT QUEUE WAIT_SECONDS");
        System.out.println();
    }
}
