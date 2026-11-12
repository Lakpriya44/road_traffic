package smartgrid;

/**
 * Simple educational adaptive controller.
 * The East-West road is the connected corridor:
 * J01 EAST -> J02 WEST; J02 EAST -> J03 WEST;
 * J03 WEST -> J02 EAST; J02 WEST -> J01 EAST.
 */
public class TrafficController {
    private static final int MIN_GREEN = 8;
    private static final int MAX_GREEN = 25;
    private static final int CONGESTION_THRESHOLD = 20;

    public SignalCommand decide(TrafficUpdate update, TrafficStore store) {
        String[] directions = {"NORTH", "EAST", "SOUTH", "WEST"};
        String bestDirection = "NORTH";
        double bestScore = -Double.MAX_VALUE;

        for (int i = 0; i < directions.length; i++) {
            ApproachData approach = update.getApproach(directions[i]);
            double score = approach.getTotalVehicles()
                    + (2.0 * approach.getQueueLength())
                    + (approach.getWaitingTimeSeconds() / 10.0);

            int downstreamQueue = getDownstreamQueue(
                    update.getJunctionId(), directions[i], store);

            // Avoid releasing more vehicles into a heavily queued neighbour.
            if (downstreamQueue > CONGESTION_THRESHOLD) {
                score -= Math.min(20.0, (downstreamQueue - CONGESTION_THRESHOLD) * 0.8);
            }

            System.out.println("[DECISION] " + update.getJunctionId()
                    + " " + directions[i] + " score=" + String.format("%.1f", score)
                    + " downstreamQueue=" + downstreamQueue);

            if (score > bestScore) {
                bestScore = score;
                bestDirection = directions[i];
            }
        }

        int duration = MIN_GREEN + (int) (Math.max(0.0, bestScore) / 5.0);
        if (duration > MAX_GREEN) duration = MAX_GREEN;
        if (duration < MIN_GREEN) duration = MIN_GREEN;

        return new SignalCommand(update.getJunctionId(), "VEHICLE",
                bestDirection, duration, "NORMAL", "NONE",
                System.currentTimeMillis());
    }

    private int getDownstreamQueue(String junctionId, String direction, TrafficStore store) {
        String neighbourId = null;
        String neighbourApproach = null;

        if ("J01".equals(junctionId) && "EAST".equals(direction)) {
            neighbourId = "J02"; neighbourApproach = "WEST";
        } else if ("J02".equals(junctionId) && "EAST".equals(direction)) {
            neighbourId = "J03"; neighbourApproach = "WEST";
        } else if ("J02".equals(junctionId) && "WEST".equals(direction)) {
            neighbourId = "J01"; neighbourApproach = "EAST";
        } else if ("J03".equals(junctionId) && "WEST".equals(direction)) {
            neighbourId = "J02"; neighbourApproach = "EAST";
        }

        if (neighbourId == null) return 0;
        TrafficUpdate neighbour = store.getTraffic(neighbourId);
        if (neighbour == null) return 0;
        return neighbour.getApproach(neighbourApproach).getQueueLength();
    }

    public SignalCommand pedestrianCommand(PedestrianRequest request) {
        return new SignalCommand(request.getJunctionId(), "PEDESTRIAN",
                request.getCrossingDirection(), 8, "NORMAL", "NONE",
                System.currentTimeMillis());
    }

    public SignalCommand emergencyCommand(EmergencyRequest request) {
        return new SignalCommand(request.getJunctionId(), "VEHICLE",
                request.getDirection(), 15, "EMERGENCY",
                request.getVehicleType(), System.currentTimeMillis());
    }
}
