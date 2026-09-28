import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Runs the full experiment: the exact DP solver (Dpsolver, Part 1) and the genetic algorithm
 * (GeneticAlgorithmSolver, Part 2) on every benchmark, repeats the GA many times, and writes the
 * statistics, graphs and report. It calls the shared solvers instead of having its own copies.
 */
public class ExperimentRunner {
    // GA settings used for every benchmark (same as Member 2's GAMain demo).
    private static final int MIN_POPULATION = 60;
    private static final int POPULATION_PER_ITEM = 8;
    private static final int MAX_GENERATIONS = 150;
    private static final double MUTATION_RATE = 0.03;
    private static final int STAGNATION_LIMIT = 35;
    private static final long BASE_SEED = 20260927L;

    /** One point in either the DP staircase or GA convergence curve. */
    private record Point(long evaluations, double profit) {}

    /** Detailed record written for each individual benchmark run. */
    private record RunRecord(String problem, int runNumber, long seed, int finalProfit,
                             int optimum, boolean success, long evaluations, double milliseconds) {}

    /** Aggregate and curve data used to create tables and graphs for one problem. */
    private record ProblemResult(String name, KnapsackInstance instance, int[] runProfits,
                                long averageEvaluations, double averageMillis,
                                List<Point> gaCurve, List<Point> dpCurve,
                                long dpEvaluations, double dpMillis, List<RunRecord> runRecords) {}

    public static void main(String[] args) throws Exception {
        // Optional arguments: benchmark directory, output directory, and runs per problem.
        Path benchmarkDir = args.length > 0 ? Path.of(args[0]) : Path.of("benchmarks");
        Path outputDir = args.length > 1 ? Path.of(args[1]) : Path.of("results");
        int runs = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        if (runs < 1) {
            throw new IllegalArgumentException("runs must be at least 1");
        }

        List<Path> benchmarkFiles;
        try (var paths = Files.list(benchmarkDir)) {
            benchmarkFiles = paths.filter(path -> path.toString().endsWith(".kp"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        if (benchmarkFiles.isEmpty()) {
            throw new IllegalArgumentException("No .kp benchmark files found in " + benchmarkDir);
        }

        // Create the output tree before running so each benchmark can write its graph immediately.
        Path graphDir = outputDir.resolve("graphs");
        Files.createDirectories(graphDir);
        Path liveGraph = outputDir.resolve("live_graph.svg");
        writeLiveDashboard(outputDir.resolve("live_graph.html"));
        List<ProblemResult> results = new ArrayList<>();
        for (int problemIndex = 0; problemIndex < benchmarkFiles.size(); problemIndex++) {
            Path file = benchmarkFiles.get(problemIndex);
            KnapsackInstance instance = KpFileParser.parse(file.toString());
            if (instance.knownOptimal == null) {
                throw new IllegalArgumentException("Missing OPTIMAL value in " + file);
            }
            System.out.printf(Locale.ROOT, "Running %s (%d items, %d runs)...%n",
                    file.getFileName(), instance.weights.length, runs);
            ProblemResult result = runProblem(file.getFileName().toString(), instance, runs,
                    problemIndex, liveGraph);
            results.add(result);
            writeGraph(graphDir.resolve(file.getFileName().toString().replace(".kp", ".svg")), result);
        }

        writeResultsCsv(outputDir.resolve("empirical_results.csv"), results, runs);
        writeRunDetailsCsv(outputDir.resolve("run_details.csv"), results);
        writeTrajectoryCsv(outputDir.resolve("trajectories.csv"), results);
        writeReport(outputDir.resolve("REPORT.md"), results, runs);
        System.out.println();
        System.out.println("Experiment completed successfully.");
        System.out.println("Completed " + (results.size() * runs) + " total GA runs ("
            + runs + " per benchmark across " + results.size() + " benchmarks).");
    }

    private static ProblemResult runProblem(String name, KnapsackInstance instance, int runs,
                                            int problemIndex, Path liveGraph) throws IOException {
        int[] finalProfits = new int[runs];
        long totalEvaluations = 0;
        long totalNanos = 0;
        List<RunRecord> runRecords = new ArrayList<>();
        List<List<long[]>> histories = new ArrayList<>();

        // Exact DP first (Part 1), so the live graph can show its staircase straight away.
        long dpStart = System.nanoTime();
        Dpsolver.Result dp = Dpsolver.solve(instance);
        double dpMillis = (System.nanoTime() - dpStart) / 1_000_000.0;
        if (dp.optimalProfit != instance.knownOptimal) {
            throw new IllegalStateException(name + ": DP returned " + dp.optimalProfit
                    + " but known optimum is " + instance.knownOptimal);
        }
        List<Point> dpCurve = new ArrayList<>();
        for (long[] step : dp.history) {
            dpCurve.add(new Point(step[0], step[1]));
        }

        // Each run gets its own deterministic seed, so the experiment can be reproduced.
        for (int run = 0; run < runs; run++) {
            long seed = BASE_SEED + problemIndex * 1_000_003L + run * 9_176L;
            long start = System.nanoTime();
            GeneticAlgorithmSolver.Result ga =
                    new GeneticAlgorithmSolver(instance, gaConfig(instance, seed)).solve();
            long elapsedNanos = System.nanoTime() - start;

            finalProfits[run] = (int) ga.bestProfit;
            totalEvaluations += ga.nfc;
            totalNanos += elapsedNanos;
            histories.add(ga.history);
            boolean success = finalProfits[run] == instance.knownOptimal;
            runRecords.add(new RunRecord(name, run + 1, seed, finalProfits[run],
                    instance.knownOptimal, success, ga.nfc, elapsedNanos / 1_000_000.0));
            System.out.printf(Locale.ROOT,
                    "  Run %d/%d complete: profit=%d, success=%s, NFC=%d, time=%.3f ms%n",
                    run + 1, runs, finalProfits[run], success, ga.nfc, elapsedNanos / 1_000_000.0);

            // Refresh the graph after every trial so it can be viewed while the study runs.
            ProblemResult liveResult = new ProblemResult(name, instance,
                    Arrays.copyOf(finalProfits, run + 1), totalEvaluations / (run + 1),
                    totalNanos / ((run + 1) * 1_000_000.0), meanCurve(histories), dpCurve,
                    dp.nfcounter, dpMillis, runRecords);
            writeGraph(liveGraph, liveResult);
        }
        System.out.printf(Locale.ROOT, "Completed %d/%d runs for %s.%n", runs, runs, name);

        return new ProblemResult(name, instance, finalProfits, totalEvaluations / runs,
                totalNanos / (runs * 1_000_000.0), meanCurve(histories), dpCurve,
                dp.nfcounter, dpMillis, runRecords);
    }

    /** GA settings for one benchmark: bigger populations for bigger problems. */
    private static GeneticAlgorithmSolver.Config gaConfig(KnapsackInstance instance, long seed) {
        GeneticAlgorithmSolver.Config config = new GeneticAlgorithmSolver.Config();
        config.populationSize = Math.max(MIN_POPULATION, instance.weights.length * POPULATION_PER_ITEM);
        config.maxGenerations = MAX_GENERATIONS;
        config.mutationRate = MUTATION_RATE;
        config.stagnationLimit = STAGNATION_LIMIT;
        config.seed = seed;
        return config;
    }

    /**
     * Mean best-so-far profit over all runs, generation by generation. Every run evaluates the
     * same number of chromosomes per generation, so generation g lines up at the same NFC in all
     * runs. A run that stopped early keeps its final value for the remaining generations.
     */
    private static List<Point> meanCurve(List<List<long[]>> histories) {
        List<long[]> longest = histories.get(0);
        for (List<long[]> history : histories) {
            if (history.size() > longest.size()) {
                longest = history;
            }
        }
        List<Point> curve = new ArrayList<>();
        for (int generation = 0; generation < longest.size(); generation++) {
            double sum = 0;
            for (List<long[]> history : histories) {
                sum += history.get(Math.min(generation, history.size() - 1))[1];
            }
            curve.add(new Point(longest.get(generation)[0], sum / histories.size()));
        }
        return curve;
    }

    private static void writeResultsCsv(Path path, List<ProblemResult> results, int runs) throws IOException {
        StringBuilder csv = new StringBuilder("problem,n,capacity,known_optimum,runs,success_rate,best_profit,mean_profit,worst_profit,mean_ga_nfc,mean_ga_ms,dp_profit,dp_nfc,dp_ms\n");
        for (ProblemResult result : results) {
            int best = Arrays.stream(result.runProfits()).max().orElse(0);
            int worst = Arrays.stream(result.runProfits()).min().orElse(0);
            double mean = Arrays.stream(result.runProfits()).average().orElse(0);
            long successes = Arrays.stream(result.runProfits())
                    .filter(profit -> profit == result.instance().knownOptimal).count();
            csv.append(String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%.4f,%d,%.3f,%d,%d,%.3f,%d,%d,%.3f%n",
                    result.name(), result.instance().weights.length, result.instance().capacity,
                    result.instance().knownOptimal, runs, (double) successes / runs, best, mean, worst,
                    result.averageEvaluations(), result.averageMillis(), result.instance().knownOptimal,
                    result.dpEvaluations(), result.dpMillis()));
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeRunDetailsCsv(Path path, List<ProblemResult> results) throws IOException {
        StringBuilder csv = new StringBuilder(
                "problem,run_number,seed,final_profit,known_optimum,success,nfc,milliseconds\n");
        for (ProblemResult result : results) {
            for (RunRecord run : result.runRecords()) {
                csv.append(String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%s,%d,%.3f%n",
                        run.problem(), run.runNumber(), run.seed(), run.finalProfit(), run.optimum(),
                        run.success(), run.evaluations(), run.milliseconds()));
            }
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeTrajectoryCsv(Path path, List<ProblemResult> results) throws IOException {
        StringBuilder csv = new StringBuilder("problem,algorithm,nfc,mean_best_profit\n");
        for (ProblemResult result : results) {
            for (Point point : result.dpCurve()) {
                csv.append(String.format(Locale.ROOT, "%s,DP,%d,%.3f%n",
                        result.name(), point.evaluations(), point.profit()));
            }
            for (Point point : result.gaCurve()) {
                csv.append(String.format(Locale.ROOT, "%s,GA,%d,%.3f%n",
                        result.name(), point.evaluations(), point.profit()));
            }
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeGraph(Path path, ProblemResult result) throws IOException {
        int width = 900;
        int height = 520;
        int left = 92;
        int right = 34;
        int top = 52;
        int bottom = 78;
        double plotWidth = width - left - right;
        double plotHeight = height - top - bottom;
        long maxX = Math.max(result.dpEvaluations(),
                result.gaCurve().get(result.gaCurve().size() - 1).evaluations());
        double maxProfit = result.instance().knownOptimal;
        double yMax = maxProfit * 1.08;
        double logMax = Math.log10(Math.max(10, maxX));
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ")
                .append(width).append(' ').append(height).append("\">\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#fbfaf7\"/>\n");
        svg.append("<text x=\"90\" y=\"30\" font-family=\"Georgia,serif\" font-size=\"22\" fill=\"#172b2a\">")
                .append(xml(result.name())).append(" | profit vs. NFC</text>\n");
        for (int tick = 0; tick <= 5; tick++) {
            double value = yMax * tick / 5.0;
            double y = top + plotHeight - plotHeight * tick / 5.0;
            svg.append(String.format(Locale.ROOT,
                    "<line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" stroke=\"#d9ddd7\"/>\n",
                    (double) left, y, (double) (width - right), y));
            svg.append(String.format(Locale.ROOT,
                    "<text x=\"%d\" y=\"%f\" text-anchor=\"end\" font-family=\"Arial,sans-serif\" font-size=\"12\" fill=\"#526260\">%.0f</text>\n",
                    left - 12, y + 4, value));
        }
        for (int exponent = 0; exponent <= Math.ceil(logMax); exponent++) {
            double xValue = Math.pow(10, exponent);
            if (xValue > maxX * 1.05) {
                continue;
            }
            double x = left + plotWidth * exponent / logMax;
            svg.append(String.format(Locale.ROOT,
                    "<line x1=\"%f\" y1=\"%d\" x2=\"%f\" y2=\"%f\" stroke=\"#e5e8e2\"/>\n",
                    x, top, x, (double) (height - bottom)));
            svg.append(String.format(Locale.ROOT,
                    "<text x=\"%f\" y=\"%d\" text-anchor=\"middle\" font-family=\"Arial,sans-serif\" font-size=\"12\" fill=\"#526260\">10^%d</text>\n",
                    x, height - bottom + 22, exponent));
        }
        svg.append(String.format(Locale.ROOT,
                "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#172b2a\" stroke-width=\"1.5\"/>\n",
                left, top, left, height - bottom));
        svg.append(String.format(Locale.ROOT,
                "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#172b2a\" stroke-width=\"1.5\"/>\n",
                left, height - bottom, width - right, height - bottom));
        svg.append(String.format(Locale.ROOT,
            "<text x=\"%f\" y=\"%d\" text-anchor=\"middle\" font-family=\"Arial,sans-serif\" font-size=\"14\" fill=\"#172b2a\">NFC (Number of Function Calls, logarithmic scale)</text>\n",
                left + plotWidth / 2, height - 18));
        svg.append(String.format(Locale.ROOT,
            "<text transform=\"translate(24 %f) rotate(-90)\" text-anchor=\"middle\" font-family=\"Arial,sans-serif\" font-size=\"14\" fill=\"#172b2a\">Profit (best feasible)</text>\n",
                top + plotHeight / 2));
        appendCurve(svg, result.dpCurve(), left, top, plotWidth, plotHeight, logMax, yMax,
                maxX, "#087f6b", "DP", true);
        appendCurve(svg, result.gaCurve(), left, top, plotWidth, plotHeight, logMax, yMax,
                maxX, "#d05b37", "GA mean", false);
        svg.append("<rect x=\"610\" y=\"58\" width=\"14\" height=\"4\" fill=\"#087f6b\"/><text x=\"632\" y=\"64\" font-family=\"Arial,sans-serif\" font-size=\"12\" fill=\"#172b2a\">Exact DP</text>");
        svg.append("<rect x=\"710\" y=\"58\" width=\"14\" height=\"4\" fill=\"#d05b37\"/><text x=\"732\" y=\"64\" font-family=\"Arial,sans-serif\" font-size=\"12\" fill=\"#172b2a\">GA mean over runs</text>");
        svg.append("</svg>\n");
        Files.writeString(path, svg, StandardCharsets.UTF_8);
    }

    private static void writeLiveDashboard(Path path) throws IOException {
        String html = "<!doctype html>\n"
                + "<html><head><meta charset=\"UTF-8\"><meta http-equiv=\"refresh\" content=\"1\">"
                + "<title>Live GA versus DP graph</title></head>\n"
                + "<body style=\"margin:0;background:#fbfaf7;text-align:center\">\n"
                + "<h2 style=\"font-family:Georgia,serif;color:#172b2a\">Live GA versus DP comparison</h2>\n"
                + "<p style=\"font-family:Arial,sans-serif;color:#526260\">This page refreshes while ExperimentRunner is running.</p>\n"
                + "<img src=\"live_graph.svg\" alt=\"Live profit versus NFC graph\" style=\"max-width:100%;height:auto\">\n"
                + "</body></html>\n";
        Files.writeString(path, html, StandardCharsets.UTF_8);
    }

    private static void appendCurve(StringBuilder svg, List<Point> points, int left, int top,
                                    double plotWidth, double plotHeight, double logMax, double yMax,
                                    long maxX, String color, String label, boolean step) {
        StringBuilder coordinates = new StringBuilder();
        Point previous = null;
        for (Point point : points) {
            double x = left + plotWidth * Math.log10(Math.max(1, point.evaluations())) / logMax;
            double y = top + plotHeight - plotHeight * point.profit() / yMax;
            if (step && previous != null) {
                // Staircase: hold the old profit until this step's NFC, then jump up.
                double oldY = top + plotHeight - plotHeight * previous.profit() / yMax;
                coordinates.append(String.format(Locale.ROOT, "%.2f,%.2f ", x, oldY));
            }
            coordinates.append(String.format(Locale.ROOT, "%.2f,%.2f ", x, y));
            previous = point;
        }
        svg.append("<polyline fill=\"none\" stroke=\"").append(color)
                .append("\" stroke-width=\"2.5\" stroke-linejoin=\"round\" points=\"")
                .append(coordinates).append("\" data-series=\"").append(label)
                .append("\" data-max-nfc=\"").append(maxX).append("\"/>\n");
    }

    private static void writeReport(Path path, List<ProblemResult> results, int runs) throws IOException {
        GeneticAlgorithmSolver.Config defaults = new GeneticAlgorithmSolver.Config();
        StringBuilder report = new StringBuilder();
        report.append("# Knapsack GA and DP Experiment Report\n\n")
                .append("## Summary\n\n")
                .append("A seeded genetic algorithm (GA) was run ").append(runs)
                .append(" times on each of ").append(results.size())
                .append(" benchmark instances. An exact 0/1 dynamic-programming (DP) solver verified the optimum and supplied the comparison curve. The GA's final profit statistics use its best feasible solution found during each run.\n\n")
                .append("## Results\n\n")
                .append("Success rate (SR) is the fraction of runs whose best feasible profit equals the benchmark optimum. GA NFC is the number of chromosome fitness evaluations per run. DP NFC counts table cells computed, n x (capacity + 1). Times are measured locally and are informational; NFC is the primary x-axis because it is less sensitive to machine load.\n\n")
                .append("| Problem | n | Capacity | Optimum | SR | Best | Mean | Worst | Mean GA NFC | Mean GA ms | DP NFC | DP ms |\n")
                .append("|:--|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|\n");
        for (ProblemResult result : results) {
            int best = Arrays.stream(result.runProfits()).max().orElse(0);
            int worst = Arrays.stream(result.runProfits()).min().orElse(0);
            double mean = Arrays.stream(result.runProfits()).average().orElse(0);
            long successes = Arrays.stream(result.runProfits())
                    .filter(profit -> profit == result.instance().knownOptimal).count();
            report.append(String.format(Locale.ROOT,
                    "| %s | %d | %d | %d | %.1f%% (%d/%d) | %d | %.2f | %d | %d | %.3f | %d | %.3f |%n",
                    result.name(), result.instance().weights.length, result.instance().capacity,
                    result.instance().knownOptimal, 100.0 * successes / runs, successes, runs,
                    best, mean, worst, result.averageEvaluations(), result.averageMillis(),
                    result.dpEvaluations(), result.dpMillis()));
        }
        report.append("\n## Individual Runs\n\n")
            .append("The aggregate table summarizes the 30 trials per problem. The complete run-by-run data, including every seed, final profit, success flag, NFC, and runtime, is available in [`run_details.csv`](run_details.csv).\n\n")
            .append("## Comparison Graphs\n\n")
                .append("Each graph plots best feasible profit against NFC on a logarithmic x-axis. The DP curve is a staircase: after each item is processed it shows the best profit achievable at full capacity with the items considered so far, and it reaches the optimum only once the last item has been considered. The GA curve is the mean best-so-far profit over all runs at each generation (a run that stops early keeps its final value).\n\n");
        for (ProblemResult result : results) {
            String graph = result.name().replace(".kp", ".svg");
            report.append("### ").append(result.name()).append("\n\n![Profit versus NFC for ")
                    .append(result.name()).append("](graphs/").append(graph).append(")\n\n");
        }
                report.append("The live dashboard is available at [`live_graph.html`](live_graph.html) while the experiment is running. It refreshes every second and displays the current GA progress against the DP staircase.\n\n");
        report.append("## Method\n\n")
                .append("The GA uses binary item-selection chromosomes (bit i = item i packed), tournament selection (size ")
                .append(defaults.tournamentSize).append("), one-point crossover (probability ").append(defaults.crossoverRate)
                .append("), bit-flip mutation (probability ").append(MUTATION_RATE).append(" per gene) and ")
                .append(defaults.elitismCount).append(" elite individuals. Infeasible (overweight) chromosomes are handled with a repair operator: while a chromosome is over capacity, the packed item with the lowest profit/weight ratio is dropped. The population is max(")
                .append(MIN_POPULATION).append(", ").append(POPULATION_PER_ITEM).append(" x n) chromosomes for at most ")
                .append(MAX_GENERATIONS).append(" generations. A run stops early if the known optimum is reached or the best profit has not improved for ")
                .append(STAGNATION_LIMIT).append(" generations, so NFC differs from run to run. Each run uses its own reproducible seed derived from the benchmark and run index.\n\n")
                .append("The DP baseline is the bottom-up dynamic programming table dp[i][w] (best profit using the first i items with capacity w), filled row by row and traced back to recover the packed items. It is exact, and costs O(n x W) time and memory, which is why it becomes slow when the capacity W is large (p08). The included instances are small benchmark cases; the results should not be generalized to larger or structurally different knapsack instances without additional experiments.\n\n")
                .append("Raw aggregate metrics are in [`empirical_results.csv`](empirical_results.csv), and plotted curve points are in [`trajectories.csv`](trajectories.csv).\n");
        Files.writeString(path, report, StandardCharsets.UTF_8);
    }

    private static String xml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}