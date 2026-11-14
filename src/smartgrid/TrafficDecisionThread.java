package smartgrid;

import java.util.concurrent.BlockingQueue;
import java.util.HashMap;
import java.util.Map;

/** Schedules one non-overlapping phase at a time for each connected junction. */
public class TrafficDecisionThread extends Thread {
    private final TrafficStore store;
    private final Map<String, BlockingQueue<String>> clients;
    private final TrafficController controller = new TrafficController();
    private final Map<String, Long> nextAllowedAt = new HashMap<String, Long>();
    private final Map<String, String> lastDirection = new HashMap<String, String>();
    private final Map<String, Integer> consecutivePicks = new HashMap<String, Integer>();
    private volatile boolean running = true;

    public TrafficDecisionThread(TrafficStore store, Map<String, BlockingQueue<String>> clients) {
        this.store = store;
        this.clients = clients;
        // Thread name is assigned in run() to avoid exposing this during construction.
    }

    public void stopDecisions() {
        running = false;
        interrupt();
    }

    public void run() {
        Thread.currentThread().setName("Traffic-Decision-Thread");
        String[] junctions = {"J01", "J02", "J03"};
        System.out.println("[DECISION] Decision thread started.");

        while (running) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < junctions.length; i++) {
                String id = junctions[i];
                BlockingQueue<String> outbox = clients.get(id);
                if (outbox == null) {
                    nextAllowedAt.remove(id);
                    lastDirection.remove(id);
                    consecutivePicks.remove(id);
                    continue;
                }

                Long allowedAt = nextAllowedAt.get(id);
                if (allowedAt != null && now < allowedAt.longValue()) continue;

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
                                String avoid = null;
                                Integer count = consecutivePicks.get(id);
                                if (count != null && count.intValue() >= 2) {
                                    avoid = lastDirection.get(id);
                                }
                                command = controller.decide(update, store, avoid);
                            }
                        }
                    }

                    if (command != null) {
                        if (!outbox.offer(command.toPacketString())) {
                            System.out.println("[DECISION] Outbound queue full for " + id
                                    + "; closing this junction session is recommended.");
                        } else {
                            System.out.println("[TCP] Queued: " + command.toPacketString());
                        }

                        // The client performs 1s yellow + 1s all-red before
                        // displaying the commanded green/walk duration.
                        long occupiedMs = (command.getDurationSeconds()
                                + SmartGridConfig.SIGNAL_CLEARANCE_SECONDS) * 1000L;
                        nextAllowedAt.put(id, now + occupiedMs);

                        if ("VEHICLE".equals(command.getPhaseType())
                                && "NORMAL".equals(command.getMode())) {
                            String previous = lastDirection.get(id);
                            if (command.getPhaseName().equals(previous)) {
                                Integer old = consecutivePicks.get(id);
                                consecutivePicks.put(id, old == null ? 2 : old + 1);
                            } else {
                                consecutivePicks.put(id, 1);
                            }
                            lastDirection.put(id, command.getPhaseName());
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("[DECISION] Error for " + id + ": " + ex.getMessage());
                }
            }

            try {
                store.awaitChange(250L);
            } catch (InterruptedException ex) {
                if (!running) break;
            }
        }
    }
}
