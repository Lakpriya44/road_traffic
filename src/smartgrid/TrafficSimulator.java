package smartgrid;

import java.util.Random;
import java.util.Scanner;

/** Creates simple stateful simulated arrivals and queues for one junction. */
public class TrafficSimulator {
    private final Random random = new Random();
    private final String[] directions = {"NORTH", "EAST", "SOUTH", "WEST"};
    private final int[] queues = {0, 0, 0, 0};
    private final int[] waitingSeconds = {0, 0, 0, 0};

    public synchronized TrafficUpdate generateRandom(String junctionId) {
        ApproachData[] approaches = new ApproachData[4];
        for (int i = 0; i < directions.length; i++) {
            // Average arrival is about 0.83 vehicles per approach per update (3 s),
            // roughly 0.28 vehicles/s. This is well below what one green phase
            // can clear, so queues rise and fall instead of saturating at the cap.
            int left = random.nextInt(6) == 0 ? 1 : 0;
            int straight = random.nextInt(2);
            int right = random.nextInt(6) == 0 ? 1 : 0;
            int arrivals = left + straight + right;
            queues[i] = Math.min(100, queues[i] + arrivals);
            waitingSeconds[i] = queues[i] == 0 ? 0 : Math.min(600, waitingSeconds[i] + 3);
            approaches[i] = new ApproachData(directions[i], left, straight,
                    right, queues[i], waitingSeconds[i]);
        }
        return new TrafficUpdate(junctionId, System.currentTimeMillis(),
                approaches[0], approaches[1], approaches[2], approaches[3]);
    }

    /** A completed vehicle green drains the selected simulated queue. */
    public synchronized void applyGreen(String direction, int durationSeconds) {
        for (int i = 0; i < directions.length; i++) {
            if (directions[i].equalsIgnoreCase(direction)) {
                int served = Math.max(1, durationSeconds * 2);
                queues[i] = Math.max(0, queues[i] - served);
                if (queues[i] == 0) waitingSeconds[i] = 0;
                else waitingSeconds[i] = Math.max(0, waitingSeconds[i] - durationSeconds);
                return;
            }
        }
    }

    public static TrafficUpdate readManual(String junctionId, Scanner scanner) {
        System.out.println("Enter values for each approach.");
        System.out.println("For each approach enter: LEFT STRAIGHT RIGHT QUEUE WAIT_SECONDS");
        ApproachData north = readApproach("NORTH", scanner);
        ApproachData east = readApproach("EAST", scanner);
        ApproachData south = readApproach("SOUTH", scanner);
        ApproachData west = readApproach("WEST", scanner);
        return new TrafficUpdate(junctionId, System.currentTimeMillis(),
                north, east, south, west);
    }

    private static ApproachData readApproach(String direction, Scanner scanner) {
        while (true) {
            System.out.print(direction + " (left straight right queue wait): ");
            try {
                int left = scanner.nextInt();
                int straight = scanner.nextInt();
                int right = scanner.nextInt();
                int queue = scanner.nextInt();
                int wait = scanner.nextInt();
                return new ApproachData(direction, left, straight, right, queue, wait);
            } catch (Exception ex) {
                System.out.println("Please enter five valid whole numbers within the allowed limits.");
                if (scanner.hasNextLine()) scanner.nextLine();
            }
        }
    }
}
