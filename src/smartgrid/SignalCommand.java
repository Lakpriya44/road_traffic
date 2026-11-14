package smartgrid;

/** Signal instruction sent by the Traffic Center to a junction over TCP. */
public class SignalCommand {
    private final String junctionId;
    private final String phaseType;       // VEHICLE or PEDESTRIAN
    private final String phaseName;       // NORTH/EAST/SOUTH/WEST
    private final int durationSeconds;
    private final String mode;            // NORMAL or EMERGENCY
    private final String priorityVehicle; // NONE/AMBULANCE/FIRE_TRUCK/POLICE
    private final long timestamp;

    public SignalCommand(String junctionId, String phaseType, String phaseName,
            int durationSeconds, String mode, String priorityVehicle, long timestamp) {
        if (!SmartGridConfig.isValidJunctionId(junctionId)) {
            throw new IllegalArgumentException("Junction ID must be J01, J02 or J03");
        }
        String pt = phaseType == null ? "" : phaseType.trim().toUpperCase();
        String pn = phaseName == null ? "" : phaseName.trim().toUpperCase();
        String m = mode == null ? "" : mode.trim().toUpperCase();
        String pv = priorityVehicle == null ? "" : priorityVehicle.trim().toUpperCase();

        if (!"VEHICLE".equals(pt) && !"PEDESTRIAN".equals(pt)) {
            throw new IllegalArgumentException("Phase type must be VEHICLE or PEDESTRIAN");
        }
        if (!"NORTH".equals(pn) && !"EAST".equals(pn)
                && !"SOUTH".equals(pn) && !"WEST".equals(pn)) {
            throw new IllegalArgumentException("Invalid phase name");
        }
        if (durationSeconds < 1 || durationSeconds > 60) {
            throw new IllegalArgumentException("Duration must be between 1 and 60 seconds");
        }
        if (!"NORMAL".equals(m) && !"EMERGENCY".equals(m)) {
            throw new IllegalArgumentException("Mode must be NORMAL or EMERGENCY");
        }
        if (!"NONE".equals(pv) && !"AMBULANCE".equals(pv)
                && !"FIRE_TRUCK".equals(pv) && !"POLICE".equals(pv)) {
            throw new IllegalArgumentException("Invalid priority vehicle");
        }
        if ("NORMAL".equals(m) && !"NONE".equals(pv)) {
            throw new IllegalArgumentException("Normal command must use priority NONE");
        }
        if ("EMERGENCY".equals(m) && (!"VEHICLE".equals(pt) || "NONE".equals(pv))) {
            throw new IllegalArgumentException("Emergency command must be a vehicle phase with a priority vehicle");
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException("Timestamp must be positive");
        }

        this.junctionId = junctionId;
        this.phaseType = pt;
        this.phaseName = pn;
        this.durationSeconds = durationSeconds;
        this.mode = m;
        this.priorityVehicle = pv;
        this.timestamp = timestamp;
    }

    public String getJunctionId() { return junctionId; }
    public String getPhaseType() { return phaseType; }
    public String getPhaseName() { return phaseName; }
    public int getDurationSeconds() { return durationSeconds; }
    public String getMode() { return mode; }
    public String getPriorityVehicle() { return priorityVehicle; }
    public long getTimestamp() { return timestamp; }

    // SIGNAL_COMMAND|J01|VEHICLE|NORTH|15|EMERGENCY|AMBULANCE|timestamp
    public String toPacketString() {
        return "SIGNAL_COMMAND|" + junctionId + "|" + phaseType + "|"
                + phaseName + "|" + durationSeconds + "|" + mode + "|"
                + priorityVehicle + "|" + timestamp;
    }

    public static SignalCommand fromPacketString(String message) {
        String[] v = message.split("\\|", -1);
        if (v.length != 8 || !"SIGNAL_COMMAND".equals(v[0])) {
            throw new IllegalArgumentException("Invalid signal command");
        }
        return new SignalCommand(v[1], v[2], v[3], Integer.parseInt(v[4]),
                v[5], v[6], Long.parseLong(v[7]));
    }
}
