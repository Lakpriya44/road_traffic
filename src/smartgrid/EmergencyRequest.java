package smartgrid;

/** Emergency vehicle request sent from a junction to the Traffic Center over TCP. */
public class EmergencyRequest {
    private final String junctionId;
    private final String direction;
    private final String vehicleType;
    private final long timestamp;

    public EmergencyRequest(String junctionId, String direction,
            String vehicleType, long timestamp) {
        if (!SmartGridConfig.isValidJunctionId(junctionId)) {
            throw new IllegalArgumentException("Junction ID must be J01, J02 or J03");
        }
        String d = direction == null ? "" : direction.trim().toUpperCase();
        String type = vehicleType == null ? "" : vehicleType.trim().toUpperCase();
        if (!"NORTH".equals(d) && !"EAST".equals(d)
                && !"SOUTH".equals(d) && !"WEST".equals(d)) {
            throw new IllegalArgumentException("Invalid emergency direction");
        }
        if (!"AMBULANCE".equals(type) && !"FIRE_TRUCK".equals(type)
                && !"POLICE".equals(type)) {
            throw new IllegalArgumentException("Vehicle must be AMBULANCE, FIRE_TRUCK or POLICE");
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException("Timestamp must be positive");
        }
        this.junctionId = junctionId;
        this.direction = d;
        this.vehicleType = type;
        this.timestamp = timestamp;
    }

    public String getJunctionId() { return junctionId; }
    public String getDirection() { return direction; }
    public String getVehicleType() { return vehicleType; }
    public long getTimestamp() { return timestamp; }

    public String toPacketString() {
        return "EMERGENCY_REQUEST|" + junctionId + "|" + direction
                + "|" + vehicleType + "|" + timestamp;
    }

    public static EmergencyRequest fromPacketString(String message) {
        String[] v = message.split("\\|", -1);
        if (v.length != 5 || !"EMERGENCY_REQUEST".equals(v[0])) {
            throw new IllegalArgumentException("Invalid emergency request");
        }
        return new EmergencyRequest(v[1], v[2], v[3], Long.parseLong(v[4]));
    }
}
