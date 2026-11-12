package smartgrid;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** Junction client. Run one process for each junction ID. */
public class JunctionClient {
    private static volatile boolean automaticMode = true;
    private static volatile boolean running = true;
    private static PrintWriter tcpOut;
    private static UDPClient udpClient;
    private static String junctionId;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: JunctionClient <J01|J02|J03> [server-host]");
            System.out.println("Example: JunctionClient J01 127.0.0.1");
            return;
        }

        junctionId = args[0].toUpperCase();
        String serverHost = args.length >= 2 ? args[1] : "127.0.0.1";

        if (!junctionId.matches("J0[1-3]")) {
            System.out.println("Junction ID must be J01, J02 or J03.");
            return;
        }

        try {
            final Socket socket = new Socket(serverHost, TrafficCenter.TCP_PORT);
            tcpOut = new PrintWriter(socket.getOutputStream(), true);
            final BufferedReader tcpIn = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            udpClient = new UDPClient(serverHost, TrafficCenter.UDP_PORT);

            tcpOut.println("HELLO|" + junctionId);
            System.out.println("==============================================");
            System.out.println("          SMARTGRID " + junctionId + " CLIENT");
            System.out.println("==============================================");
            System.out.println("Connected to server " + serverHost);
            System.out.println("Automatic traffic simulation is ON.");

            Thread receiver = new Thread(new Runnable() {
                public void run() {
                    try {
                        String line;
                        while ((line = tcpIn.readLine()) != null) {
                            if (line.startsWith("SIGNAL_COMMAND|")) {
                                SignalCommand command = SignalCommand.fromPacketString(line);
                                displayCommand(command);
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
            receiver.start();

            Thread sender = new Thread(new Runnable() {
                public void run() {
                    TrafficSimulator simulator = new TrafficSimulator();
                    while (running) {
                        if (automaticMode) {
                            try {
                                TrafficUpdate update = simulator.generateRandom(junctionId);
                                udpClient.send(update);
                                System.out.println("[UDP] Sent traffic update: "
                                        + junctionId + " at " + update.getTimestamp());
                            } catch (Exception ex) {
                                System.out.println("[UDP] Send failed: " + ex.getMessage());
                            }
                        }
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }, "Traffic-Sensor-Simulator");
            sender.start();

            Scanner scanner = new Scanner(System.in);
            printHelp();
            while (running) {
                System.out.print(junctionId + "> ");
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine().trim();
                if (line.length() == 0) continue;

                String[] parts = line.split("\\s+");
                String command = parts[0].toLowerCase();

                if ("help".equals(command)) {
                    printHelp();
                } else if ("auto".equals(command)) {
                    automaticMode = true;
                    System.out.println("Automatic traffic simulation ON.");
                } else if ("manual".equals(command)) {
                    automaticMode = false;
                    System.out.println("Automatic traffic simulation OFF. Use 'traffic' to enter data.");
                } else if ("traffic".equals(command)) {
                    automaticMode = false;
                    try {
                        TrafficUpdate update = TrafficSimulator.readManual(junctionId, scanner);
                        udpClient.send(update);
                        System.out.println("[UDP] Manual traffic update sent.");
                    } catch (Exception ex) {
                        System.out.println("Could not send manual update: " + ex.getMessage());
                    }
                } else if ("ped".equals(command) && parts.length == 3) {
                    try {
                        PedestrianRequest request = new PedestrianRequest(
                                junctionId, parts[1], Integer.parseInt(parts[2]),
                                System.currentTimeMillis());
                        sendTcp(request.toPacketString());
                    } catch (Exception ex) {
                        System.out.println("Usage: ped NORTH 4 (direction and count)");
                    }
                } else if ("emergency".equals(command) && parts.length == 3) {
                    try {
                        EmergencyRequest request = new EmergencyRequest(
                                junctionId, parts[1], parts[2],
                                System.currentTimeMillis());
                        sendTcp(request.toPacketString());
                    } catch (Exception ex) {
                        System.out.println("Usage: emergency NORTH AMBULANCE");
                    }
                } else if ("quit".equals(command)) {
                    running = false;
                    break;
                } else {
                    System.out.println("Unknown command. Type help.");
                }
            }

            running = false;
            try { socket.close(); } catch (Exception ignored) { }
            udpClient.close();
            System.out.println(junctionId + " client stopped.");
        } catch (Exception ex) {
            System.out.println("[CLIENT] Could not connect: " + ex.getMessage());
            System.out.println("Start TrafficCenter first and check the server IP/port.");
            ex.printStackTrace();
        }
    }

    private static void sendTcp(String message) {
        synchronized (tcpOut) {
            tcpOut.println(message);
        }
        System.out.println("[TCP] Sent: " + message);
    }

    private static void displayCommand(SignalCommand command) {
        try {
            System.out.println();
            System.out.println("========== SIGNAL CHANGE: " + junctionId + " ==========");
            System.out.println("Received: " + command.toPacketString());

            System.out.println("YELLOW: current vehicle phase changing...");
            Thread.sleep(1000);
            System.out.println("ALL RED: safety clearance...");
            Thread.sleep(1000);

            if ("PEDESTRIAN".equals(command.getPhaseType())) {
                System.out.println("ALL VEHICLE SIGNALS: RED");
                System.out.println("PEDESTRIAN WALK: " + command.getPhaseName());
            } else {
                System.out.println("GREEN: " + command.getPhaseName());
                System.out.println("Other approaches: RED");
            }

            if ("EMERGENCY".equals(command.getMode())) {
                System.out.println("EMERGENCY PRIORITY: " + command.getPriorityVehicle());
            }
            System.out.println("Duration: " + command.getDurationSeconds() + " seconds");
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
