package smartgrid;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Main Central Traffic Control Server. */
public class TrafficCenter {
    public static final int TCP_PORT = SmartGridConfig.TCP_PORT;
    public static final int UDP_PORT = SmartGridConfig.UDP_PORT;

    public static void main(String[] args) {
        final TrafficStore store = new TrafficStore();
        final Map<String, BlockingQueue<String>> clients =
                new ConcurrentHashMap<String, BlockingQueue<String>>();
        final AtomicBoolean running = new AtomicBoolean(true);
        final ServerSocket serverSocket;
        final DatagramSocket udpSocket;
        final TrafficDecisionThread decisionThread;

        System.out.println("==============================================");
        System.out.println("       SMARTGRID TRAFFIC CONTROL CENTER");
        System.out.println("==============================================");

        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(TCP_PORT));
            udpSocket = new DatagramSocket(UDP_PORT);
        } catch (Exception ex) {
            System.out.println("[SERVER] Could not open ports: " + ex.getMessage());
            return;
        }

        UDPReceiver udpReceiver = new UDPReceiver(udpSocket, store);
        udpReceiver.setDaemon(true);
        decisionThread = new TrafficDecisionThread(store, clients);
        decisionThread.setDaemon(true);

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                running.set(false);
                decisionThread.stopDecisions();
                try { serverSocket.close(); } catch (Exception ignored) { }
                udpSocket.close();
            }
        }, "SmartGrid-Shutdown"));

        System.out.println("[TCP] Listening on port " + TCP_PORT);
        udpReceiver.start();
        decisionThread.start();
        System.out.println("[SERVER] Waiting for J01, J02 and J03...");

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                JunctionHandler handler = new JunctionHandler(clientSocket, store, clients);
                handler.setDaemon(true);
                handler.start();
            } catch (SocketException ex) {
                if (running.get()) {
                    System.out.println("[TCP] Accept error: " + ex.getMessage());
                }
                break;
            } catch (Exception ex) {
                if (running.get()) {
                    System.out.println("[TCP] Could not accept client: " + ex.getMessage());
                }
            }
        }

        running.set(false);
        decisionThread.stopDecisions();
        udpSocket.close();
        try { serverSocket.close(); } catch (Exception ignored) { }
        System.out.println("[SERVER] Traffic Center stopped.");
    }
}
