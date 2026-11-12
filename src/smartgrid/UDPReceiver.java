package smartgrid;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/** Dedicated thread that continuously receives UDP traffic snapshots. */
public class UDPReceiver extends Thread {
    private final DatagramSocket socket;
    private final TrafficStore store;

    public UDPReceiver(DatagramSocket socket, TrafficStore store) {
        this.socket = socket;
        this.store = store;
        setName("UDP-Traffic-Receiver");
    }

    public void run() {
        byte[] buffer = new byte[2048];
        System.out.println("[UDP] Listening for traffic updates on port " + socket.getLocalPort());

        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), packet.getOffset(),
                        packet.getLength(), StandardCharsets.UTF_8);
                TrafficUpdate update = TrafficUpdate.fromPacketString(message);
                store.updateTraffic(update);

                System.out.println("[UDP] Received from " + update.getJunctionId()
                        + " (" + packet.getAddress().getHostAddress() + "): "
                        + "N=" + update.getNorth().getTotalVehicles()
                        + " E=" + update.getEast().getTotalVehicles()
                        + " S=" + update.getSouth().getTotalVehicles()
                        + " W=" + update.getWest().getTotalVehicles());
            } catch (Exception ex) {
                if (!socket.isClosed()) {
                    System.out.println("[UDP] Ignored invalid packet: " + ex.getMessage());
                }
            }
        }
    }
}
