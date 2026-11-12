package smartgrid;

import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Main Central Traffic Control Server. */
public class TrafficCenter {
    public static final int TCP_PORT = 6655;
    public static final int UDP_PORT = 4442;

    public static void main(String[] args) {
        TrafficStore store = new TrafficStore();
        Map<String, PrintWriter> clients = new ConcurrentHashMap<String, PrintWriter>();

        System.out.println("==============================================");
        System.out.println("       SMARTGRID TRAFFIC CONTROL CENTER");
        System.out.println("==============================================");

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT);
             DatagramSocket udpSocket = new DatagramSocket(UDP_PORT)) {

            System.out.println("[TCP] Listening on port " + TCP_PORT);
            UDPReceiver udpReceiver = new UDPReceiver(udpSocket, store);
            udpReceiver.start();

            TrafficDecisionThread decisionThread =
                    new TrafficDecisionThread(store, clients);
            decisionThread.start();

            System.out.println("[SERVER] Waiting for J01, J02 and J03...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                JunctionHandler handler =
                        new JunctionHandler(clientSocket, store, clients);
                handler.start();
            }
        } catch (Exception ex) {
            System.out.println("[SERVER] Server stopped: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
