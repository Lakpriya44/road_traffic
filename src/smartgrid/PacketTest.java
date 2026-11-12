package smartgrid;

/** Run this first to test packet creation and parsing without networking. */
public class PacketTest {
    public static void main(String[] args) {
        ApproachData north = new ApproachData("NORTH", 5, 12, 3, 15, 30);
        ApproachData east  = new ApproachData("EAST", 4, 10, 2, 8, 20);
        ApproachData south = new ApproachData("SOUTH", 6, 14, 4, 18, 35);
        ApproachData west  = new ApproachData("WEST", 3, 8, 2, 9, 15);

        TrafficUpdate update = new TrafficUpdate("J01",
                System.currentTimeMillis(), north, east, south, west);
        String trafficText = update.toPacketString();
        System.out.println("UDP TRAFFIC PACKET:");
        System.out.println(trafficText);

        TrafficUpdate decoded = TrafficUpdate.fromPacketString(trafficText);
        System.out.println("Decoded junction: " + decoded.getJunctionId());
        System.out.println("North total vehicles: "
                + decoded.getNorth().getTotalVehicles());

        PedestrianRequest pedestrian = new PedestrianRequest(
                "J01", "NORTH", 4, System.currentTimeMillis());
        System.out.println("\nTCP PEDESTRIAN REQUEST:");
        System.out.println(pedestrian.toPacketString());
        System.out.println("Decoded: "
                + PedestrianRequest.fromPacketString(pedestrian.toPacketString())
                        .getWaitingPedestrians() + " pedestrians");

        EmergencyRequest emergency = new EmergencyRequest(
                "J02", "NORTH", "AMBULANCE", System.currentTimeMillis());
        System.out.println("\nTCP EMERGENCY REQUEST:");
        System.out.println(emergency.toPacketString());

        SignalCommand signal = new SignalCommand(
                "J01", "VEHICLE", "NORTH", 15, "NORMAL", "NONE",
                System.currentTimeMillis());
        System.out.println("\nTCP SIGNAL COMMAND:");
        System.out.println(signal.toPacketString());
        System.out.println("\nPacket test completed successfully.");
    }
}
