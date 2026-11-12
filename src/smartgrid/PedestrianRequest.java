package smartgrid;

/** A pedestrian button/request message sent to the Traffic Center over TCP. */
public class PedestrianRequest {
    private final String junctionId;
    private final String crossingDirection;
    private final int waitingPedestrians;
    private final long timestamp;

    public PedestrianRequest(String junctionId, String crossingDirection,
            int waitingPedestrians, long timestamp) {
        if (junctionId == null || !junctionId.matches("J0[1-3]")) {
            throw new IllegalArgumentException("Junction ID must be J01, J02 or J03");
        }
        String direction = crossingDirection == null ? "" : crossingDirection.trim().toUpperCase();
        if (!isDirection(direction)) {
            throw new IllegalArgumentException("Invalid crossing direction");
        }
        if (waitingPedestrians < 1) {
            throw new IllegalArgumentException("At least one pedestrian is required");
        }
        this.junctionId = junctionId;
        this.crossingDirection = direction;
        this.waitingPedestrians = waitingPedestrians;
        this.timestamp = timestamp;
    }

    private static boolean isDirection(String d) {
        return "NORTH".equals(d) || "EAST".equals(d)
                || "SOUTH".equals(d) || "WEST".equals(d);
    }

    public String getJunctionId() { return junctionId; }
    public String getCrossingDirection() { return crossingDirection; }
    public int getWaitingPedestrians() { return waitingPedestrians; }
    public long getTimestamp() { return timestamp; }

    public String toPacketString() {
        return "PED_REQUEST|" + junctionId + "|" + crossingDirection
                + "|" + waitingPedestrians + "|" + timestamp;
    }

    public static PedestrianRequest fromPacketString(String message) {
        String[] v = message.split("\\|", -1);
        if (v.length != 5 || !"PED_REQUEST".equals(v[0])) {
            throw new IllegalArgumentException("Invalid pedestrian request");
        }
        return new PedestrianRequest(v[1], v[2],
                Integer.parseInt(v[3]), Long.parseLong(v[4]));
    }
}
