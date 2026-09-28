import java.util.ArrayList;
import java.util.List;

public class Dpsolver {

    // Holds both outputs of solve(): the optimal profit, and which items make it up.
    public static class Result {
        public int optimalProfit;
        public boolean[] itemsChosen; // itemsChosen[i] = true means item (i+1) was packed
        public long nfcounter;
        // Staircase data for the graph: {NFC so far, best profit at full capacity} after each item.
        public List<long[]> history = new ArrayList<>();

        public Result(int optimalProfit, boolean[] itemsChosen, long nfcounter) {
            this.optimalProfit = optimalProfit;
            this.itemsChosen = itemsChosen;
            this.nfcounter = nfcounter;
        }
    }

    public static Result solve(KnapsackInstance instance) {
        int n = instance.weights.length;
        int capacity = instance.capacity;
        long nfcounter = 0;

        int[][] dp = new int[n + 1][capacity + 1];

        List<long[]> history = new ArrayList<>();
        history.add(new long[]{0, 0}); // nothing computed yet, profit 0

        for (int i = 1; i <= n; i++) {
            int weight = instance.weights[i - 1];
            int value = instance.values[i - 1];

            for (int w = 0; w <= capacity; w++) {
                nfcounter++;
                if (weight > w) {
                    // item doesn't fit at this capacity -- skip it
                    dp[i][w] = dp[i - 1][w];
                } else {
                    int skip = dp[i - 1][w];
                    int take = value + dp[i - 1][w - weight];
                    dp[i][w] = Math.max(skip, take);
                }
            }
            // Row i finished: best profit using the first i items at full capacity (one staircase step).
            history.add(new long[]{nfcounter, dp[i][capacity]});
        }

        int optimalProfit = dp[n][capacity];

        // Traceback: walk from the bottom-right corner back up to row 0,
        // figuring out at each row whether that item was included.
        boolean[] itemsChosen = new boolean[n];
        int i = n;
        int w = capacity;

        while (i > 0) {
            if (dp[i][w] != dp[i - 1][w]) {
                // value changed -> item i was taken
                itemsChosen[i - 1] = true;
                w = w - instance.weights[i - 1];
            }
            // else: same value as row above -> item i was skipped, w unchanged
            i = i - 1;
        }

        Result result = new Result(optimalProfit, itemsChosen, nfcounter);
        result.history = history;
        return result;
    }

    public static void runAllBenchmarksDp() throws Exception {
    String[] files = {"p01.kp", "p02.kp", "p03.kp", "p04.kp", "p05.kp", "p06.kp", "p07.kp", "p08.kp"};

        for (String file : files) {
            KnapsackInstance instance = KpFileParser.parse("benchmarks/" + file);
            Result result = solve(instance);
            System.out.println();
            System.out.println("The file " + file );
            System.out.println("====================================");
            System.out.println("Calculated Optimal Value: " + result.optimalProfit);
            System.out.println("The Expected Optimal Value:" + instance.knownOptimal);
            System.out.print(".kp result -> ");

            if(result.optimalProfit == instance.knownOptimal){
                System.out.print("Matched");
            }
            else{
                System.out.print("MisMatched");
            }
            System.out.println();
        }
    }

    public static void printSingleFileResults(int filenum) throws Exception {

        String filename = "benchmarks/p0" + String.valueOf(filenum) + ".kp";
        KnapsackInstance instance = KpFileParser.parse(filename);
        Result result = solve(instance);

        System.out.println("Optimal profit: " + result.optimalProfit);
        System.out.println("Items packed: ");
        for (int i = 0; i < result.itemsChosen.length; i++) {
            if (result.itemsChosen[i]) {
                System.out.println("Item " + (i + 1));
            }
        }
        System.out.println();
    }

    public static void main(String[] args) throws Exception {
        printSingleFileResults(7);

        //runAllBenchmarksDp();//
    }
}