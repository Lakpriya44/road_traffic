package smartgrid;

/** Dependency-free packet round-trip and malformed-input tests. */
public class PacketTest {
    public static void main(String[] args) {
        long now = System.currentTimeMillis();
        TrafficUpdate traffic = new TrafficUpdate("J01", now,
                new ApproachData("NORTH", 5, 12, 3, 15, 30),
                new ApproachData("EAST", 4, 10, 2, 8, 20),
                new ApproachData("SOUTH", 6, 14, 4, 18, 35),
                new ApproachData("WEST", 3, 8, 2, 9, 15));
        TrafficUpdate trafficCopy = TrafficUpdate.fromPacketString(traffic.toPacketString());
        check(traffic.toPacketString().equals(trafficCopy.toPacketString()),
                "TrafficUpdate round-trip");

        PedestrianRequest pedestrian = new PedestrianRequest("J01", "NORTH", 4, now);
        PedestrianRequest pedestrianCopy =
                PedestrianRequest.fromPacketString(pedestrian.toPacketString());
        check(pedestrian.toPacketString().equals(pedestrianCopy.toPacketString()),
                "PedestrianRequest round-trip");

        EmergencyRequest emergency = new EmergencyRequest(
                "J02", "NORTH", "AMBULANCE", now);
        EmergencyRequest emergencyCopy =
                EmergencyRequest.fromPacketString(emergency.toPacketString());
        check(emergency.toPacketString().equals(emergencyCopy.toPacketString()),
                "EmergencyRequest round-trip");

        SignalCommand signal = new SignalCommand(
                "J01", "VEHICLE", "NORTH", 15, "NORMAL", "NONE", now);
        SignalCommand signalCopy = SignalCommand.fromPacketString(signal.toPacketString());
        check(signal.toPacketString().equals(signalCopy.toPacketString()),
                "SignalCommand round-trip");

        expectFailure(new Runnable() {
            public void run() { TrafficUpdate.fromPacketString("TRAFFIC|J01|broken"); }
        }, "malformed traffic packet");
        expectFailure(new Runnable() {
            public void run() { PedestrianRequest.fromPacketString("PED_REQUEST|J01|NORTH|0|1"); }
        }, "invalid pedestrian count");
        expectFailure(new Runnable() {
            public void run() { EmergencyRequest.fromPacketString("EMERGENCY_REQUEST|J09|NORTH|AMBULANCE|1"); }
        }, "invalid junction ID");
        expectFailure(new Runnable() {
            public void run() { new ApproachData("NORTH", 10001, 0, 0, 0, 0); }
        }, "excessive traffic count");
        expectFailure(new Runnable() {
            public void run() { new SignalCommand("J01", "VEHICLE", "NORTH", 90, "NORMAL", "NONE", now); }
        }, "excessive signal duration");

        System.out.println("All packet tests passed (4 round-trips + 5 invalid-input tests).");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError("FAILED: " + label);
        System.out.println("[PASS] " + label);
    }

    private static void expectFailure(Runnable test, String label) {
        try {
            test.run();
            throw new AssertionError("FAILED: expected exception for " + label);
        } catch (IllegalArgumentException expected) {
            System.out.println("[PASS] " + label);
        }
    }
}
