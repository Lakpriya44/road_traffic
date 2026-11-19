package smartgrid;

/** Simple educational adaptive controller for a three-junction corridor. */
public class TrafficController {
    private static final int MIN_GREEN = 8;
    private static final int MAX_GREEN = 25;
    private static final int CONGESTION_THRESHOLD = 20;

    /**
     * Approach names mean the side from which vehicles ARRIVE at a junction.
     * Eastbound corridor: J01 WEST -> J02 WEST -> J03 WEST.
     * Westbound corridor: J03 EAST -> J02 EAST -> J01 EAST.
     */
    public SignalCommand decide(TrafficUpdate update, TrafficStore store,
            String avoidDirection) {
        String[] directions = {"NORTH", "EAST", "SOUTH", "WEST"};
        double[] scores = new double[directions.length];
        double[] penalties = new double[directions.length];
        int bestIndex = 0;

        for (int i = 0; i < directions.length; i++) {
            ApproachData approach = update.getApproach(directions[i]);
            double score = approach.getTotalVehicles()
                    + (2.0 * approach.getQueueLength())
                    + (approach.getWaitingTimeSeconds() / 10.0);

            int downstreamQueue = getDownstreamQueue(
                    update.getJunctionId(), directions[i], store);
            if (downstreamQueue > CONGESTION_THRESHOLD) {
                penalties[i] = Math.min(20.0,
                        (downstreamQueue - CONGESTION_THRESHOLD) * 0.8);
                score -= penalties[i];
            }
            scores[i] = score;
            if (score > scores[bestIndex] || i == 0) bestIndex = i;
        }

        // Simple anti-starvation rule: after two consecutive selections,
        // prefer the best other approach when it has some demand.
        int naturalBest = bestIndex;
        boolean antiStarvation = false;
        if (avoidDirection != null && directions[bestIndex].equals(avoidDirection)) {
            int alternative = -1;
            for (int i = 0; i < directions.length; i++) {
                if (directions[i].equals(avoidDirection)) continue;
                if (alternative < 0 || scores[i] > scores[alternative]) alternative = i;
            }
            if (alternative >= 0 && scores[alternative] > 0) {
                bestIndex = alternative;
                antiStarvation = true;
            }
        }

        String bestDirection = directions[bestIndex];
        double bestScore = scores[bestIndex];
        int duration = MIN_GREEN + (int) (Math.max(0.0, bestScore) / 5.0);
        duration = Math.max(MIN_GREEN, Math.min(MAX_GREEN, duration));

        System.out.println("[DECISION] " + update.getJunctionId()
                + " selected=" + bestDirection
                + " score=" + String.format("%.1f", bestScore)
                + " green=" + duration + "s");

        publishDecision(update.getJunctionId(), directions, scores, penalties,
                naturalBest, bestIndex, antiStarvation, avoidDirection, duration);

        return new SignalCommand(update.getJunctionId(), "VEHICLE",
                bestDirection, duration, "NORMAL", "NONE",
                System.currentTimeMillis());
    }

    /**
     * Dashboard-only description of the decision just taken (does not affect control).
     * DECISION|J01|NORMAL|NORTH|score|green|antiStarvation(0/1)|N:score:penalty;E:..;S:..;W:..|reason
     */
    private void publishDecision(String id, String[] dirs, double[] scores, double[] penalties,
            int natural, int chosen, boolean antiStarvation, String avoid, int duration) {
        StringBuilder table = new StringBuilder();
        for (int i = 0; i < dirs.length; i++) {
            if (i > 0) table.append(';');
            table.append(dirs[i].charAt(0)).append(':')
                    .append(String.format("%.1f", scores[i])).append(':')
                    .append(String.format("%.1f", penalties[i]));
        }
        StringBuilder why = new StringBuilder();
        if (antiStarvation) {
            why.append(dirs[natural]).append(" had the highest score but was already served twice in a row, ")
                    .append("so the anti-starvation rule chose ").append(dirs[chosen])
                    .append(" (next best demand).");
        } else {
            int runnerUp = -1;
            for (int i = 0; i < dirs.length; i++) {
                if (i != chosen && (runnerUp < 0 || scores[i] > scores[runnerUp])) runnerUp = i;
            }
            why.append(dirs[chosen]).append(" has the highest adjusted demand score (")
                    .append(String.format("%.1f", scores[chosen])).append(" vs ")
                    .append(dirs[runnerUp]).append(' ').append(String.format("%.1f", scores[runnerUp]))
                    .append(").");
        }
        if (penalties[chosen] > 0) {
            why.append(" A downstream congestion penalty of -")
                    .append(String.format("%.1f", penalties[chosen])).append(" was applied.");
        }
        DashboardHub.publish("decision", "DECISION|" + id + "|NORMAL|" + dirs[chosen] + "|"
                + String.format("%.1f", scores[chosen]) + "|" + duration + "|"
                + (antiStarvation ? 1 : 0) + "|" + table + "|" + why);
    }

    private int getDownstreamQueue(String junctionId, String direction, TrafficStore store) {
        String neighbourId = null;
        String neighbourApproach = null;

        // Side-of-arrival convention: eastbound traffic arrives at the next
        // junction from WEST; westbound traffic arrives from EAST.
        if ("J01".equals(junctionId) && "WEST".equals(direction)) {
            neighbourId = "J02"; neighbourApproach = "WEST";
        } else if ("J02".equals(junctionId) && "WEST".equals(direction)) {
            neighbourId = "J03"; neighbourApproach = "WEST";
        } else if ("J03".equals(junctionId) && "EAST".equals(direction)) {
            neighbourId = "J02"; neighbourApproach = "EAST";
        } else if ("J02".equals(junctionId) && "EAST".equals(direction)) {
            neighbourId = "J01"; neighbourApproach = "EAST";
        }

        if (neighbourId == null) return 0;
        TrafficUpdate neighbour = store.getTraffic(neighbourId);
        if (neighbour == null) return 0;
        return neighbour.getApproach(neighbourApproach).getQueueLength();
    }

    public SignalCommand pedestrianCommand(PedestrianRequest request) {
        int duration = 8 + Math.min(12, (request.getWaitingPedestrians() + 1) / 2);
        return new SignalCommand(request.getJunctionId(), "PEDESTRIAN",
                request.getCrossingDirection(), duration, "NORMAL", "NONE",
                System.currentTimeMillis());
    }

    public SignalCommand emergencyCommand(EmergencyRequest request) {
        return new SignalCommand(request.getJunctionId(), "VEHICLE",
                request.getDirection(), 15, "EMERGENCY",
                request.getVehicleType(), System.currentTimeMillis());
    }
}
