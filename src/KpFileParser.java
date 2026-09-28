import java.nio.file.*;
import java.util.*;

public class KpFileParser {
    public static KnapsackInstance parse(String filePath) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(filePath));

        KnapsackInstance instance = new KnapsackInstance();
        ArrayList<Integer> weightList = new ArrayList<>();
        ArrayList<Integer> valueList = new ArrayList<>();

        boolean inItemSection = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals("EOF")) {
                break;
            }
            if (line.equals("ITEM_SECTION")) {
                inItemSection = true;
                continue;
            }

            if (!inItemSection) {
                if (line.startsWith("CAPACITY:")) {
                    String numberPart = line.substring(line.indexOf(':') + 1).trim();
                    instance.capacity = Integer.parseInt(numberPart);
                }
                if (line.startsWith("OPTIMAL:")) {
                    String numberPart = line.substring(line.indexOf(':') + 1).trim();
                    instance.knownOptimal = Integer.parseInt(numberPart);
                }
            } else {
                String[] parts = line.split("\\s+");
                int weight = Integer.parseInt(parts[1]);
                int profit = Integer.parseInt(parts[2]);
                weightList.add(weight);
                valueList.add(profit);
            }
        }

        // Convert the growable lists into fixed-size arrays
        instance.weights = new int[weightList.size()];
        instance.values = new int[valueList.size()];
        for (int i = 0; i < weightList.size(); i++) {
            instance.weights[i] = weightList.get(i);
            instance.values[i] = valueList.get(i);
        }

        return instance;
    }

    // Quick manual test
    public static void main(String[] args) throws Exception {
        String filepath = "benchmarks/p02.kp";
        KnapsackInstance instance = parse(filepath);
        System.out.println("Capacity: " + instance.capacity);
        System.out.println("Known optimal: " + instance.knownOptimal);
        for (int i = 0; i < instance.weights.length; i++) {
            System.out.println("Item " + (i + 1) + ": weight=" + instance.weights[i] + ", value=" + instance.values[i]);
        }
    }
}