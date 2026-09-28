import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class GeneticAlgorithmSolver {

    // Configuration
    public static class Config {
        public int populationSize   = 100;
        public int maxGenerations   = 200;
        public double crossoverRate = 0.85;
        public double mutationRate  = 0.02;
        public int tournamentSize   = 3;
        public int elitismCount     = 2;
        /** Stop early if best has not improved for this many generations. */
        public int stagnationLimit  = 40;
        // Unique per Config (time alone repeats when runs start within the same millisecond).
        private static final java.util.concurrent.atomic.AtomicLong SEED_COUNTER =
                new java.util.concurrent.atomic.AtomicLong();
        public long seed            = System.nanoTime() + SEED_COUNTER.incrementAndGet();
    }

    // Result container
    public static class Result {
        public final long bestProfit;
        public final boolean[] bestChromosome;   // length n
        public final List<Integer> itemsPacked;  // 1-based indices
        public final long totalWeight;
        public final long nfc;                   // number of fitness evaluations
        public final List<long[]> history;       // (nfc, bestProfitSoFar) for graphing
        public final boolean reachedKnownOpt;

        public Result(long bestProfit, boolean[] bestChromosome,
                      List<Integer> itemsPacked, long totalWeight,
                      long nfc, List<long[]> history, boolean reachedKnownOpt) {
            this.bestProfit = bestProfit;
            this.bestChromosome = bestChromosome;
            this.itemsPacked = itemsPacked;
            this.totalWeight = totalWeight;
            this.nfc = nfc;
            this.history = history;
            this.reachedKnownOpt = reachedKnownOpt;
        }
    }

    // Fields
    private final KnapsackInstance instance;
    private final Config config;
    private final Random rng;
    private final double[] ratio;   // profit / weight for each item (used by repair)

    public GeneticAlgorithmSolver(KnapsackInstance instance, Config config) {
        this.instance = instance;
        this.config = config;
        this.rng = new Random(config.seed);

        int n = instance.weights.length;
        this.ratio = new double[n];
        for (int i = 0; i < n; i++) {
            ratio[i] = (instance.weights[i] == 0)
                    ? Double.MAX_VALUE
                    : (double) instance.values[i] / instance.weights[i];
        }
    }

    // Public solve entry points
    public Result solve() {
        Integer known = instance.knownOptimal;
        return run(known != null ? known.longValue() : null);
    }

    // Core GA loop
    private Result run(Long knownOpt) {
        int n = instance.weights.length;
        int popSize = config.populationSize;

        boolean[][] population = new boolean[popSize][n];
        long[] fitness = new long[popSize];

        // ---- initialise population randomly, then repair ----
        for (int i = 0; i < popSize; i++) {
            for (int j = 0; j < n; j++) {
                population[i][j] = rng.nextBoolean();
            }
            repair(population[i]);
            fitness[i] = evaluate(population[i]);
        }

        long nfc = popSize;
        long bestProfit = Long.MIN_VALUE;
        boolean[] bestChrom = null;
        for (int i = 0; i < popSize; i++) {
            if (fitness[i] > bestProfit) {
                bestProfit = fitness[i];
                bestChrom = population[i].clone();
            }
        }

        List<long[]> history = new ArrayList<>();
        history.add(new long[]{nfc, bestProfit});

        int gensWithoutImprove = 0;

        for (int gen = 0; gen < config.maxGenerations; gen++) {
            boolean[][] nextPop = new boolean[popSize][n];
            long[] nextFit = new long[popSize];

            // Elitism – copy the best individuals unchanged
            int[] order = argsortDescending(fitness);
            for (int e = 0; e < config.elitismCount && e < popSize; e++) {
                nextPop[e] = population[order[e]].clone();
                nextFit[e] = fitness[order[e]];
            }

            int filled = config.elitismCount;
            while (filled < popSize) {
                boolean[] parent1 = tournamentSelect(population, fitness);
                boolean[] parent2 = tournamentSelect(population, fitness);

                boolean[] child1 = parent1.clone();
                boolean[] child2 = parent2.clone();

                if (rng.nextDouble() < config.crossoverRate) {
                    singlePointCrossover(child1, child2);
                }

                mutate(child1);
                mutate(child2);

                repair(child1);
                repair(child2);

                nextPop[filled] = child1;
                nextFit[filled] = evaluate(child1);
                nfc++;
                filled++;
                if (filled >= popSize) break;

                nextPop[filled] = child2;
                nextFit[filled] = evaluate(child2);
                nfc++;
                filled++;
            }

            population = nextPop;
            fitness = nextFit;

            // Track global best
            long genBest = Long.MIN_VALUE;
            int genBestIdx = 0;
            for (int i = 0; i < popSize; i++) {
                if (fitness[i] > genBest) {
                    genBest = fitness[i];
                    genBestIdx = i;
                }
            }

            if (genBest > bestProfit) {
                bestProfit = genBest;
                bestChrom = population[genBestIdx].clone();
                gensWithoutImprove = 0;
            } else {
                gensWithoutImprove++;
            }

            history.add(new long[]{nfc, bestProfit});

            // Early termination
            if (knownOpt != null && bestProfit >= knownOpt) break;
            if (gensWithoutImprove >= config.stagnationLimit) break;
        }

        if (bestChrom == null) {
            bestChrom = new boolean[n];
        }

        List<Integer> items = selectedItems(bestChrom);
        long totalW = totalWeight(bestChrom);
        boolean reached = knownOpt != null && bestProfit >= knownOpt;

        return new Result(bestProfit, bestChrom, items, totalW, nfc, history, reached);
    }

    // Fitness evaluation (counts toward NFC)
    private long evaluate(boolean[] chrom) {
        long profit = 0;
        for (int i = 0; i < chrom.length; i++) {
            if (chrom[i]) profit += instance.values[i];
        }
        return profit;
    }

    // REPAIR OPERATOR
    // While overweight, drop the selected item with the lowest profit/weight
    // ratio. Guarantees a feasible chromosome.
    private void repair(boolean[] chrom) {
        long weight = totalWeight(chrom);
        if (weight <= instance.capacity) return;

        // Collect selected indices, sorted by ratio ascending (worst first)
        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < chrom.length; i++) {
            if (chrom[i]) selected.add(i);
        }
        selected.sort((a, b) -> Double.compare(ratio[a], ratio[b]));

        for (int idx : selected) {
            if (weight <= instance.capacity) break;
            chrom[idx] = false;
            weight -= instance.weights[idx];
        }
    }

    // Selection – tournament of size tournamentSize
    private boolean[] tournamentSelect(boolean[][] pop, long[] fit) {
        int best = rng.nextInt(pop.length);
        for (int t = 1; t < config.tournamentSize; t++) {
            int cand = rng.nextInt(pop.length);
            if (fit[cand] > fit[best]) best = cand;
        }
        return pop[best];
    }

    // Crossover – single point
    private void singlePointCrossover(boolean[] a, boolean[] b) {
        int point = rng.nextInt(a.length);
        for (int i = point; i < a.length; i++) {
            boolean tmp = a[i];
            a[i] = b[i];
            b[i] = tmp;
        }
    }

    // Mutation – bit-flip
    private void mutate(boolean[] chrom) {
        for (int i = 0; i < chrom.length; i++) {
            if (rng.nextDouble() < config.mutationRate) {
                chrom[i] = !chrom[i];
            }
        }
    }

    // Helpers
    private long totalWeight(boolean[] chrom) {
        long w = 0;
        for (int i = 0; i < chrom.length; i++) {
            if (chrom[i]) w += instance.weights[i];
        }
        return w;
    }

    private List<Integer> selectedItems(boolean[] chrom) {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < chrom.length; i++) {
            if (chrom[i]) items.add(i + 1);   // 1-based
        }
        return items;
    }

    private static int[] argsortDescending(long[] arr) {
        Integer[] idx = new Integer[arr.length];
        for (int i = 0; i < arr.length; i++) idx[i] = i;
        Arrays.sort(idx, (i, j) -> Long.compare(arr[j], arr[i]));
        int[] out = new int[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = idx[i];
        return out;
    }

    // Pretty-print helper for demos
    public static void printResult(KnapsackInstance inst, Result r) {
        System.out.println("=== Genetic Algorithm Result ===");
        System.out.println("Best profit     : " + r.bestProfit);
        if (inst.knownOptimal != null) {
            System.out.println("Known optimum   : " + inst.knownOptimal);
            System.out.println("Reached optimum : " + (r.reachedKnownOpt ? "YES" : "NO"));
            System.out.println("Gap             : " + (inst.knownOptimal - r.bestProfit));
        }
        System.out.println("Weight used     : " + r.totalWeight + " / " + inst.capacity);
        System.out.println("Items packed    : " + r.itemsPacked);
        System.out.println("NFC (evals)     : " + r.nfc);
    }
}
