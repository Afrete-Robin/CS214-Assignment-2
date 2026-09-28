import java.util.Scanner;

/**
 * Menu that ties the whole project together:
 *   Part 1 - KpFileParser + Dpsolver (exact dynamic programming)
 *   Part 2 - GeneticAlgorithmSolver (non-deterministic)
 *   Part 3 - ExperimentRunner (30 runs per problem, statistics, graphs, report)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println();
            System.out.println("===== 0/1 Knapsack: DP vs GA =====");
            System.out.println("1) Solve one benchmark with DP");
            System.out.println("2) Solve one benchmark with GA");
            System.out.println("3) Check DP against KNOWN_OPTIMA on all benchmarks");
            System.out.println("4) Run the full experiment (30 GA runs per benchmark, stats + graphs + report)");
            System.out.println("0) Exit");
            System.out.print("Choose an option: ");

            String choice = scanner.next();

            if (choice.equals("1")) {
                KnapsackInstance instance = KpFileParser.parse(askForFile(scanner));
                Dpsolver.Result result = Dpsolver.solve(instance);
                System.out.println("Optimal profit: " + result.optimalProfit);
                System.out.println("NFC (table cells computed): " + result.nfcounter);
                System.out.print("Items packed:");
                for (int i = 0; i < result.itemsChosen.length; i++) {
                    if (result.itemsChosen[i]) {
                        System.out.print(" " + (i + 1));
                    }
                }
                System.out.println();

            } else if (choice.equals("2")) {
                KnapsackInstance instance = KpFileParser.parse(askForFile(scanner));
                GeneticAlgorithmSolver.Result result =
                        new GeneticAlgorithmSolver(instance, new GeneticAlgorithmSolver.Config()).solve();
                GeneticAlgorithmSolver.printResult(instance, result);

            } else if (choice.equals("3")) {
                Dpsolver.runAllBenchmarksDp();

            } else if (choice.equals("4")) {
                ExperimentRunner.main(new String[0]);
                System.out.println("Open results/live_graph.html or results/REPORT.md to see the output.");

            } else if (choice.equals("0")) {
                break;

            } else {
                System.out.println("Invalid option.");
            }
        }
    }

    // Asks for a benchmark number (1-8) and turns it into a path such as benchmarks/p03.kp.
    private static String askForFile(Scanner scanner) {
        System.out.print("Benchmark number (1-8): ");
        int number = scanner.nextInt();
        return "benchmarks/p" + String.format("%02d", number) + ".kp";
    }
}
