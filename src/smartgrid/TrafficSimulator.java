package smartgrid;

import java.util.Random;

/** Creates simulated traffic sensor readings for one junction. */
public class TrafficSimulator {
    private final Random random = new Random();

    public TrafficUpdate generateRandom(String junctionId) {
        return new TrafficUpdate(junctionId, System.currentTimeMillis(),
                randomApproach("NORTH"), randomApproach("EAST"),
                randomApproach("SOUTH"), randomApproach("WEST"));
    }

    private ApproachData randomApproach(String direction) {
        int left = random.nextInt(11);
        int straight = random.nextInt(21);
        int right = random.nextInt(11);
        int queue = random.nextInt(31);
        int waiting = random.nextInt(61);
        return new ApproachData(direction, left, straight, right, queue, waiting);
    }

    public static TrafficUpdate readManual(String junctionId,
            java.util.Scanner scanner) {
        System.out.println("Enter values for each approach.");
        System.out.println("For each direction enter: LEFT STRAIGHT RIGHT QUEUE WAIT_SECONDS");

        ApproachData north = readApproach("NORTH", scanner);
        ApproachData east = readApproach("EAST", scanner);
        ApproachData south = readApproach("SOUTH", scanner);
        ApproachData west = readApproach("WEST", scanner);

        return new TrafficUpdate(junctionId, System.currentTimeMillis(),
                north, east, south, west);
    }

    private static ApproachData readApproach(String direction,
            java.util.Scanner scanner) {
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
                System.out.println("Please enter five whole numbers, all zero or greater.");
                if (scanner.hasNextLine()) scanner.nextLine();
            }
        }
    }
}
