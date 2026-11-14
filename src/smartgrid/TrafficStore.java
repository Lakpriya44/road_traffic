package smartgrid;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Thread-safe latest traffic snapshots and bounded request queues. */
public class TrafficStore {
    private static final class TimedValue<T> {
        final T value;
        final long receivedAt;
        TimedValue(T value) {
            this.value = value;
            this.receivedAt = System.currentTimeMillis();
        }
    }

    private final Map<String, TimedValue<TrafficUpdate>> updates =
            new HashMap<String, TimedValue<TrafficUpdate>>();
    private final Map<String, InetAddress> registeredAddresses =
            new HashMap<String, InetAddress>();
    private final Map<String, ArrayDeque<TimedValue<PedestrianRequest>>> pedestrians =
            new HashMap<String, ArrayDeque<TimedValue<PedestrianRequest>>>();
    private final Map<String, ArrayDeque<TimedValue<EmergencyRequest>>> emergencies =
            new HashMap<String, ArrayDeque<TimedValue<EmergencyRequest>>>();

    public synchronized void registerJunction(String junctionId, InetAddress address) {
        registeredAddresses.put(junctionId, address);
        notifyAll();
    }

    /** Remove data and pending requests when the TCP session disconnects. */
    public synchronized void unregisterJunction(String junctionId) {
        registeredAddresses.remove(junctionId);
        updates.remove(junctionId);
        pedestrians.remove(junctionId);
        emergencies.remove(junctionId);
        notifyAll();
    }

    /** Accept UDP data only from the IP address registered on the TCP session. */
    public synchronized boolean updateTraffic(TrafficUpdate update, InetAddress sourceAddress) {
        InetAddress registered = registeredAddresses.get(update.getJunctionId());
        if (registered == null || !registered.equals(sourceAddress)) {
            return false;
        }

        TimedValue<TrafficUpdate> previous = updates.get(update.getJunctionId());
        // Timestamp is used only to reject out-of-order packets from this same client.
        // Freshness is measured using this server's receive time, not laptop clocks.
        if (previous != null && update.getTimestamp() <= previous.value.getTimestamp()) {
            return false;
        }

        updates.put(update.getJunctionId(), new TimedValue<TrafficUpdate>(update));
        notifyAll();
        return true;
    }

    /** Returns null when no snapshot exists or the last one is older than 10 seconds. */
    public synchronized TrafficUpdate getTraffic(String junctionId) {
        TimedValue<TrafficUpdate> value = updates.get(junctionId);
        if (value == null) return null;
        if (System.currentTimeMillis() - value.receivedAt > SmartGridConfig.TRAFFIC_STALE_AFTER_MS) {
            updates.remove(junctionId);
            return null;
        }
        return value.value;
    }

    public synchronized boolean addPedestrianRequest(PedestrianRequest request) {
        if (!registeredAddresses.containsKey(request.getJunctionId())) return false;
        ArrayDeque<TimedValue<PedestrianRequest>> queue = pedestrians.get(request.getJunctionId());
        if (queue == null) {
            queue = new ArrayDeque<TimedValue<PedestrianRequest>>();
            pedestrians.put(request.getJunctionId(), queue);
        }
        if (queue.size() >= SmartGridConfig.REQUEST_QUEUE_LIMIT) return false;
        queue.addLast(new TimedValue<PedestrianRequest>(request));
        notifyAll();
        return true;
    }

    public synchronized PedestrianRequest takePedestrianRequest(String junctionId) {
        ArrayDeque<TimedValue<PedestrianRequest>> queue = pedestrians.get(junctionId);
        while (queue != null && !queue.isEmpty()) {
            TimedValue<PedestrianRequest> item = queue.removeFirst();
            if (System.currentTimeMillis() - item.receivedAt <= SmartGridConfig.REQUEST_MAX_AGE_MS) {
                return item.value;
            }
        }
        return null;
    }

    public synchronized boolean addEmergencyRequest(EmergencyRequest request) {
        if (!registeredAddresses.containsKey(request.getJunctionId())) return false;
        ArrayDeque<TimedValue<EmergencyRequest>> queue = emergencies.get(request.getJunctionId());
        if (queue == null) {
            queue = new ArrayDeque<TimedValue<EmergencyRequest>>();
            emergencies.put(request.getJunctionId(), queue);
        }
        if (queue.size() >= SmartGridConfig.REQUEST_QUEUE_LIMIT) return false;
        queue.addLast(new TimedValue<EmergencyRequest>(request));
        notifyAll();
        return true;
    }

    public synchronized EmergencyRequest takeEmergencyRequest(String junctionId) {
        ArrayDeque<TimedValue<EmergencyRequest>> queue = emergencies.get(junctionId);
        while (queue != null && !queue.isEmpty()) {
            TimedValue<EmergencyRequest> item = queue.removeFirst();
            if (System.currentTimeMillis() - item.receivedAt <= SmartGridConfig.REQUEST_MAX_AGE_MS) {
                return item.value;
            }
        }
        return null;
    }

    /** Wake the decision thread when new data or a request arrives. */
    public synchronized void awaitChange(long timeoutMillis) throws InterruptedException {
        wait(timeoutMillis);
    }
}
