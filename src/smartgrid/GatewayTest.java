package smartgrid;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/** Unit tests for the dashboard-to-server request bridge (no network needed). */
public class GatewayTest {
    private static int failures = 0;

    private static void check(String name, boolean condition) {
        System.out.println((condition ? "[PASS] " : "[FAIL] ") + name);
        if (!condition) failures++;
    }

    public static void main(String[] args) throws Exception {
        TrafficStore store = new TrafficStore();
        Map<String, BlockingQueue<String>> clients = new ConcurrentHashMap<>();
        BlockingQueue<String> j01Outbox = new LinkedBlockingQueue<>();
        clients.put("J01", j01Outbox);
        DashboardGateway.attach(store, clients);
        store.registerJunction("J01", InetAddress.getLoopbackAddress());

        DashboardGateway.Result r = DashboardGateway.submitPedestrian("J01", "NORTH", "4");
        check("valid pedestrian request accepted (HTTP 200, QUEUED)", r.ok && r.status == 200 && "QUEUED".equals(r.code));
        PedestrianRequest taken = store.takePedestrianRequest("J01");
        check("accepted request is in the real pedestrian queue with correct values",
                taken != null && "NORTH".equals(taken.getCrossingDirection()) && taken.getWaitingPedestrians() == 4);
        check("response id equals the request timestamp", taken != null && r.id == taken.getTimestamp());
        check("junction console was told about the dashboard request", j01Outbox.peek() != null && j01Outbox.peek().startsWith("INFO|"));

        r = DashboardGateway.submitPedestrian("J01", "NORTH", "0");
        check("pedestrian count 0 rejected as invalid (HTTP 400)", !r.ok && r.status == 400 && "INVALID".equals(r.code));
        r = DashboardGateway.submitPedestrian("J01", "NORTH", "abc");
        check("non-numeric pedestrian count rejected (HTTP 400)", !r.ok && r.status == 400);
        r = DashboardGateway.submitPedestrian("J01", "UP", "2");
        check("invalid crossing direction rejected (HTTP 400)", !r.ok && r.status == 400);
        r = DashboardGateway.submitPedestrian("J09", "NORTH", "2");
        check("unknown junction rejected (HTTP 400)", !r.ok && r.status == 400);
        r = DashboardGateway.submitPedestrian("J02", "NORTH", "2");
        check("valid but OFFLINE junction rejected (HTTP 409 OFFLINE), nothing queued",
                !r.ok && r.status == 409 && "OFFLINE".equals(r.code) && store.takePedestrianRequest("J02") == null);

        r = DashboardGateway.submitEmergency("J01", "EAST", "AMBULANCE");
        check("valid emergency request accepted", r.ok && r.status == 200);
        EmergencyRequest em = store.takeEmergencyRequest("J01");
        check("accepted request is in the real emergency queue",
                em != null && "EAST".equals(em.getDirection()) && "AMBULANCE".equals(em.getVehicleType()));
        r = DashboardGateway.submitEmergency("J01", "EAST", "TANK");
        check("invalid emergency vehicle type rejected (HTTP 400)", !r.ok && r.status == 400);

        int accepted = 0;
        for (int i = 0; i < SmartGridConfig.REQUEST_QUEUE_LIMIT + 3; i++) {
            if (DashboardGateway.submitPedestrian("J01", "WEST", "1").ok) accepted++;
        }
        check("queue limit enforced: exactly " + SmartGridConfig.REQUEST_QUEUE_LIMIT + " accepted", accepted == SmartGridConfig.REQUEST_QUEUE_LIMIT);
        r = DashboardGateway.submitPedestrian("J01", "WEST", "1");
        check("request into a full queue answered with HTTP 409 QUEUE_FULL", !r.ok && r.status == 409 && "QUEUE_FULL".equals(r.code));

        String snap = store.requestSnapshot("J01");
        check("request snapshot lists the pending queue (REQSTATE format)", snap.startsWith("REQSTATE|J01|WEST*1*"));

        System.out.println(failures == 0 ? "All gateway tests passed." : failures + " gateway test(s) FAILED.");
        if (failures != 0) System.exit(1);
    }
}
