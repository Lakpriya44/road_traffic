package smartgrid;

/** Shared configuration for the three-junction academic simulation. */
public final class SmartGridConfig {
    public static final int TCP_PORT = 6655;
    public static final int UDP_PORT = 4442;
    public static final int DASHBOARD_PORT = 8080;
    public static final int TCP_CONNECT_TIMEOUT_MS = 5000;
    public static final int TCP_HANDSHAKE_TIMEOUT_MS = 10000;
    public static final int TCP_HEARTBEAT_TIMEOUT_MS = 30000;
    public static final int CLIENT_HEARTBEAT_INTERVAL_MS = 10000;
    public static final int TRAFFIC_UPDATE_INTERVAL_MS = 3000;
    public static final int TRAFFIC_STALE_AFTER_MS = 10000;
    public static final int REQUEST_QUEUE_LIMIT = 10;
    public static final int REQUEST_MAX_AGE_MS = 120000;
    public static final int MAX_TCP_LINE_LENGTH = 2048;
    public static final int SIGNAL_CLEARANCE_SECONDS = 2;

    private SmartGridConfig() { }

    public static boolean isValidJunctionId(String id) {
        return "J01".equals(id) || "J02".equals(id) || "J03".equals(id);
    }
}
