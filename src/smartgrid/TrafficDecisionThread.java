package smartgrid;

import java.io.PrintWriter;
import java.util.Map;

/** Periodically decides and sends commands without blocking TCP/UDP receivers. */
public class TrafficDecisionThread extends Thread {
    private final TrafficStore store;
    private final Map<String, PrintWriter> clients;
    private final TrafficController controller = new TrafficController();
    private volatile boolean running = true;

    public TrafficDecisionThread(TrafficStore store, Map<String, PrintWriter> clients) {
        this.store = store;
        this.clients = clients;
        setName("Traffic-Decision-Thread");
    }

    public void stopDecisions() {
        running = false;
        interrupt();
    }

    public void run() {
        String[] junctions = {"J01", "J02", "J03"};
        System.out.println("[DECISION] Decision thread started.");

        while (running) {
            for (int i = 0; i < junctions.length; i++) {
                String id = junctions[i];
                PrintWriter out;

                synchronized (clients) {
                    out = clients.get(id);
                }
                if (out == null) continue;

                try {
                    SignalCommand command = null;

                    EmergencyRequest emergency = store.takeEmergencyRequest(id);
                    if (emergency != null) {
                        command = controller.emergencyCommand(emergency);
                    } else {
                        PedestrianRequest pedestrian = store.takePedestrianRequest(id);
                        if (pedestrian != null) {
                            command = controller.pedestrianCommand(pedestrian);
                        } else {
                            TrafficUpdate update = store.getTraffic(id);
                            if (update != null) {
                                command = controller.decide(update, store);
                            }
                        }
                    }

                    if (command != null) {
                        synchronized (out) {
                            out.println(command.toPacketString());
                            if (out.checkError()) {
                                System.out.println("[DECISION] Could not send command to " + id);
                            } else {
                                System.out.println("[TCP] Sent: " + command.toPacketString());
                            }
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("[DECISION] Error for " + id + ": " + ex.getMessage());
                }
            }

            try {
                Thread.sleep(8000);
            } catch (InterruptedException ex) {
                if (!running) break;
            }
        }
    }
}
