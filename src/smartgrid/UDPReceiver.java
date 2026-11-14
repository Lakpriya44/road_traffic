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
        // Thread name is assigned in run() to avoid exposing this during construction.
    }

    public void run() {
        Thread.currentThread().setName("UDP-Traffic-Receiver");
        byte[] buffer = new byte[2048];
        System.out.println("[UDP] Listening for traffic updates on port " + socket.getLocalPort());

        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                if (packet.getLength() > SmartGridConfig.MAX_TCP_LINE_LENGTH) {
                    System.out.println("[UDP] Ignored oversized packet");
                    continue;
                }

                String message = new String(packet.getData(), packet.getOffset(),
                        packet.getLength(), StandardCharsets.UTF_8);
                TrafficUpdate update = TrafficUpdate.fromPacketString(message);
                boolean accepted = store.updateTraffic(update, packet.getAddress());
                if (!accepted) {
                    System.out.println("[UDP] Ignored unregistered sender or out-of-order packet for "
                            + update.getJunctionId());
                    continue;
                }

                System.out.println("[UDP] Received " + update.getJunctionId()
                        + " from " + packet.getAddress().getHostAddress()
                        + " | queues N/E/S/W="
                        + update.getNorth().getQueueLength() + "/"
                        + update.getEast().getQueueLength() + "/"
                        + update.getSouth().getQueueLength() + "/"
                        + update.getWest().getQueueLength());
            } catch (Exception ex) {
                if (!socket.isClosed()) {
                    System.out.println("[UDP] Ignored invalid packet: " + ex.getMessage());
                }
            }
        }
    }
}
