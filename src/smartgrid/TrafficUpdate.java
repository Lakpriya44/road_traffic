package smartgrid;

/**
 * Complete traffic snapshot for one junction. Sent from a junction to the
 * Traffic Center over UDP.
 */
public class TrafficUpdate {
    private final String junctionId;
    private final long timestamp;
    private final ApproachData north;
    private final ApproachData east;
    private final ApproachData south;
    private final ApproachData west;

    public TrafficUpdate(String junctionId, long timestamp,
            ApproachData north, ApproachData east,
            ApproachData south, ApproachData west) {

        if (junctionId == null || !junctionId.matches("J0[1-3]")) {
            throw new IllegalArgumentException("Junction ID must be J01, J02 or J03");
        }
        if (north == null || east == null || south == null || west == null) {
            throw new IllegalArgumentException("All four approaches are required");
        }
        if (!"NORTH".equals(north.getDirection())
                || !"EAST".equals(east.getDirection())
                || !"SOUTH".equals(south.getDirection())
                || !"WEST".equals(west.getDirection())) {
            throw new IllegalArgumentException("Approaches must be supplied in N,E,S,W order");
        }

        this.junctionId = junctionId;
        this.timestamp = timestamp;
        this.north = north;
        this.east = east;
        this.south = south;
        this.west = west;
    }

    public String getJunctionId() { return junctionId; }
    public long getTimestamp() { return timestamp; }
    public ApproachData getNorth() { return north; }
    public ApproachData getEast() { return east; }
    public ApproachData getSouth() { return south; }
    public ApproachData getWest() { return west; }

    public ApproachData getApproach(String direction) {
        if ("NORTH".equalsIgnoreCase(direction)) return north;
        if ("EAST".equalsIgnoreCase(direction)) return east;
        if ("SOUTH".equalsIgnoreCase(direction)) return south;
        if ("WEST".equalsIgnoreCase(direction)) return west;
        throw new IllegalArgumentException("Unknown direction: " + direction);
    }

    // TRAFFIC|J01|timestamp|NORTH,l,s,r,q,w|EAST,...|SOUTH,...|WEST,...
    public String toPacketString() {
        return "TRAFFIC|" + junctionId + "|" + timestamp + "|"
                + north.toPacketField() + "|" + east.toPacketField() + "|"
                + south.toPacketField() + "|" + west.toPacketField();
    }

    public static TrafficUpdate fromPacketString(String message) {
        String[] values = message.split("\\|", -1);
        if (values.length != 7 || !"TRAFFIC".equals(values[0])) {
            throw new IllegalArgumentException("Invalid TRAFFIC packet");
        }
        return new TrafficUpdate(values[1], Long.parseLong(values[2]),
                ApproachData.fromPacketField(values[3]),
                ApproachData.fromPacketField(values[4]),
                ApproachData.fromPacketField(values[5]),
                ApproachData.fromPacketField(values[6]));
    }
}
