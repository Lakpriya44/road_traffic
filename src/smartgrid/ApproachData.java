package smartgrid;

/**
 * Traffic information for one road approach (NORTH, EAST, SOUTH or WEST).
 * Counts are movement-based: LEFT, STRAIGHT and RIGHT.
 */
public class ApproachData {
    private final String direction;
    private final int leftTurnVehicles;
    private final int straightVehicles;
    private final int rightTurnVehicles;
    private final int queueLength;
    private final int waitingTimeSeconds;

    public ApproachData(String direction, int leftTurnVehicles,
            int straightVehicles, int rightTurnVehicles,
            int queueLength, int waitingTimeSeconds) {

        if (direction == null) {
            throw new IllegalArgumentException("Direction is required");
        }

        String normalized = direction.trim().toUpperCase();
        if (!normalized.equals("NORTH") && !normalized.equals("EAST")
                && !normalized.equals("SOUTH") && !normalized.equals("WEST")) {
            throw new IllegalArgumentException("Invalid direction: " + direction);
        }

        if (leftTurnVehicles < 0 || straightVehicles < 0
                || rightTurnVehicles < 0 || queueLength < 0
                || waitingTimeSeconds < 0) {
            throw new IllegalArgumentException("Traffic values cannot be negative");
        }
        if (leftTurnVehicles > 10000 || straightVehicles > 10000
                || rightTurnVehicles > 10000 || queueLength > 10000
                || waitingTimeSeconds > 86400) {
            throw new IllegalArgumentException("Traffic value exceeds the allowed simulation limit");
        }

        this.direction = normalized;
        this.leftTurnVehicles = leftTurnVehicles;
        this.straightVehicles = straightVehicles;
        this.rightTurnVehicles = rightTurnVehicles;
        this.queueLength = queueLength;
        this.waitingTimeSeconds = waitingTimeSeconds;
    }

    public String getDirection() { return direction; }
    public int getLeftTurnVehicles() { return leftTurnVehicles; }
    public int getStraightVehicles() { return straightVehicles; }
    public int getRightTurnVehicles() { return rightTurnVehicles; }
    public int getQueueLength() { return queueLength; }
    public int getWaitingTimeSeconds() { return waitingTimeSeconds; }

    public int getTotalVehicles() {
        return leftTurnVehicles + straightVehicles + rightTurnVehicles;
    }

    // One approach is encoded as: DIRECTION,left,straight,right,queue,waiting
    public String toPacketField() {
        return direction + "," + leftTurnVehicles + "," + straightVehicles
                + "," + rightTurnVehicles + "," + queueLength + ","
                + waitingTimeSeconds;
    }

    public static ApproachData fromPacketField(String data) {
        String[] values = data.split(",", -1);
        if (values.length != 6) {
            throw new IllegalArgumentException("Invalid approach field: " + data);
        }
        return new ApproachData(values[0],
                Integer.parseInt(values[1]), Integer.parseInt(values[2]),
                Integer.parseInt(values[3]), Integer.parseInt(values[4]),
                Integer.parseInt(values[5]));
    }

    public String toString() {
        return direction + " [L=" + leftTurnVehicles
                + ", S=" + straightVehicles + ", R=" + rightTurnVehicles
                + ", Queue=" + queueLength + ", Wait=" + waitingTimeSeconds + "s]";
    }
}
