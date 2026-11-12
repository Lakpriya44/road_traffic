package smartgrid;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/** Sends TrafficUpdate messages to the server using UDP. */
public class UDPClient {
    private final DatagramSocket socket;
    private final InetAddress serverAddress;
    private final int serverPort;

    public UDPClient(String host, int port) throws Exception {
        this.serverAddress = InetAddress.getByName(host);
        this.serverPort = port;
        this.socket = new DatagramSocket(); // OS selects an available client port
    }

    public synchronized void send(TrafficUpdate update) throws Exception {
        byte[] data = update.toPacketString().getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
                data, data.length, serverAddress, serverPort);
        socket.send(packet);
    }

    public void close() {
        socket.close();
    }
}
