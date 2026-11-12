package smartgrid;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared latest traffic data and pending requests.
 * synchronized methods protect shared data used by server threads.
 */
public class TrafficStore {
    private final Map<String, TrafficUpdate> updates = new HashMap<String, TrafficUpdate>();
    private final Map<String, PedestrianRequest> pedestrians = new HashMap<String, PedestrianRequest>();
    private final Map<String, EmergencyRequest> emergencies = new HashMap<String, EmergencyRequest>();

    public synchronized void updateTraffic(TrafficUpdate update) {
        updates.put(update.getJunctionId(), update);
    }

    public synchronized TrafficUpdate getTraffic(String junctionId) {
        return updates.get(junctionId);
    }

    public synchronized void addPedestrianRequest(PedestrianRequest request) {
        pedestrians.put(request.getJunctionId(), request);
    }

    public synchronized PedestrianRequest takePedestrianRequest(String junctionId) {
        return pedestrians.remove(junctionId);
    }

    public synchronized void addEmergencyRequest(EmergencyRequest request) {
        emergencies.put(request.getJunctionId(), request);
    }

    public synchronized EmergencyRequest takeEmergencyRequest(String junctionId) {
        return emergencies.remove(junctionId);
    }
}
