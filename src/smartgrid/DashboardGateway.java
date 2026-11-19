package smartgrid;

import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Bridges dashboard HTTP requests into the existing request-handling system.
 *
 * The browser never talks to the TCP protocol. The HTTP handler in DashboardHub
 * calls this class, which builds the SAME PedestrianRequest / EmergencyRequest
 * objects the TCP path uses (so the same validation applies) and queues them
 * in the SAME TrafficStore queues that TrafficDecisionThread already serves.
 */
public final class DashboardGateway {
    /** Outcome returned to the browser. status is the HTTP status code. */
    public static final class Result {
        public final int status;
        public final boolean ok;
        public final String code;     // QUEUED, INVALID, OFFLINE, QUEUE_FULL, UNAVAILABLE
        public final String message;
        public final long id;         // request timestamp; matches REQSTATE entries (0 when rejected)
        Result(int status, boolean ok, String code, String message) { this(status, ok, code, message, 0L); }
        Result(int status, boolean ok, String code, String message, long id) {
            this.status = status; this.ok = ok; this.code = code; this.message = message; this.id = id;
        }
    }

    private static volatile TrafficStore store;
    private static volatile Map<String, BlockingQueue<String>> clients;

    private DashboardGateway() { }

    public static void attach(TrafficStore trafficStore, Map<String, BlockingQueue<String>> clientMap) {
        store = trafficStore;
        clients = clientMap;
    }

    public static Result submitPedestrian(String junction, String direction, String countText) {
        TrafficStore s = store;
        if (s == null) return new Result(503, false, "UNAVAILABLE", "Request gateway is not attached to the traffic store");
        String id = norm(junction);
        PedestrianRequest request;
        try {
            int count = Integer.parseInt(countText == null ? "" : countText.trim());
            request = new PedestrianRequest(id, direction, count, System.currentTimeMillis());
        } catch (NumberFormatException ex) {
            return reject(400, "INVALID", "PED", id, "Pedestrian count must be a whole number");
        } catch (IllegalArgumentException ex) {
            return reject(400, "INVALID", "PED", id, ex.getMessage());
        }
        if (!s.isRegistered(id)) {
            return reject(409, "OFFLINE", "PED", id, id + " is offline; the request was not queued");
        }
        if (!s.addPedestrianRequest(request)) {
            // addPedestrianRequest returns false for "offline" (race) or "queue full".
            String why = s.isRegistered(id) ? "Pedestrian request queue is full" : id + " went offline";
            return reject(409, s.isRegistered(id) ? "QUEUE_FULL" : "OFFLINE", "PED", id, why);
        }
        DashboardHub.publish("ped", request.toPacketString());
        DashboardHub.publish("ack", "ACK|PED|" + id + "|OK|QUEUED|" + request.getCrossingDirection()
                + " x" + request.getWaitingPedestrians());
        DashboardHub.publishRequestState(s, id);
        System.out.println("[REQUEST] Pedestrian request queued for " + id + " (via dashboard)");
        tellJunction(id, "INFO|Dashboard pedestrian request queued: "
                + request.getCrossingDirection() + " x" + request.getWaitingPedestrians());
        return new Result(200, true, "QUEUED", "Pedestrian request queued for " + id
                + " (" + request.getCrossingDirection() + ", " + request.getWaitingPedestrians() + " waiting)", request.getTimestamp());
    }

    public static Result submitEmergency(String junction, String direction, String vehicle) {
        TrafficStore s = store;
        if (s == null) return new Result(503, false, "UNAVAILABLE", "Request gateway is not attached to the traffic store");
        String id = norm(junction);
        EmergencyRequest request;
        try {
            request = new EmergencyRequest(id, direction, vehicle, System.currentTimeMillis());
        } catch (IllegalArgumentException ex) {
            return reject(400, "INVALID", "EMERG", id, ex.getMessage());
        }
        if (!s.isRegistered(id)) {
            return reject(409, "OFFLINE", "EMERG", id, id + " is offline; the request was not queued");
        }
        if (!s.addEmergencyRequest(request)) {
            String why = s.isRegistered(id) ? "Emergency request queue is full" : id + " went offline";
            return reject(409, s.isRegistered(id) ? "QUEUE_FULL" : "OFFLINE", "EMERG", id, why);
        }
        DashboardHub.publish("emerg", request.toPacketString());
        DashboardHub.publish("ack", "ACK|EMERG|" + id + "|OK|QUEUED|" + request.getVehicleType()
                + " from " + request.getDirection());
        DashboardHub.publishRequestState(s, id);
        System.out.println("[REQUEST] Emergency request queued for " + id + " (via dashboard)");
        tellJunction(id, "INFO|Dashboard emergency request queued: "
                + request.getVehicleType() + " from " + request.getDirection());
        return new Result(200, true, "QUEUED", request.getVehicleType() + " request queued for "
                + id + " (arriving from " + request.getDirection() + ")", request.getTimestamp());
    }

    private static Result reject(int status, String code, String type, String id, String message) {
        String safe = message == null ? "Invalid request" : message;
        DashboardHub.publish("ack", "ACK|" + type + "|" + (id.length() == 0 ? "-" : id)
                + "|ERROR|" + safe.replace('|', '/'));
        System.out.println("[REQUEST] Dashboard request rejected: " + safe);
        return new Result(status, false, code, safe);
    }

    /** Lets the junction's console show that its dashboard-originated request was accepted. */
    private static void tellJunction(String id, String line) {
        Map<String, BlockingQueue<String>> c = clients;
        if (c == null) return;
        BlockingQueue<String> outbox = c.get(id);
        if (outbox != null) outbox.offer(line);   // best effort; the request is already queued
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
