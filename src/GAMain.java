public class GAMain {

    public static void main(String[] args) throws Exception {

        //Hardcoded small test case
        System.out.println("################################################");
        System.out.println("# STEP 1: Hardcoded tiny instance (n=4)        #");
        System.out.println("################################################");
        runHardcodedTest();

        //Real parser + benchmark files
        if (args.length == 0 || (args.length == 1 && args[0].equals("--all"))) {
            System.out.println();
            System.out.println("################################################");
            System.out.println("# STEP 2: All benchmark files                  #");
            System.out.println("################################################");
            String[] files = {
                "benchmarks/p01.kp", "benchmarks/p02.kp", "benchmarks/p03.kp",
                "benchmarks/p04.kp", "benchmarks/p05.kp", "benchmarks/p06.kp",
                "benchmarks/p07.kp", "benchmarks/p08.kp"
            };
            for (String f : files) {
                runFile(f);
            }
        } else {
            for (String f : args) {
                if (!f.equals("--all")) runFile(f);
            }
        }
    }

    // Hardcoded case: 4 items, capacity 5
    private static void runHardcodedTest() {
        KnapsackInstance inst = new KnapsackInstance();
        inst.capacity = 5;
        inst.weights  = new int[]{2, 3, 4, 5};
        inst.values   = new int[]{3, 4, 5, 6};
        inst.knownOptimal = 7;

        GeneticAlgorithmSolver.Config cfg = new GeneticAlgorithmSolver.Config();
        cfg.populationSize = 40;
        cfg.maxGenerations = 50;
        cfg.mutationRate   = 0.05;
        cfg.seed           = 42L;

        GeneticAlgorithmSolver ga = new GeneticAlgorithmSolver(inst, cfg);
        GeneticAlgorithmSolver.Result r = ga.solve();

        GeneticAlgorithmSolver.printResult(inst, r);
        System.out.println("Hardcoded test " + (r.reachedKnownOpt ? "PASSED" : "FAILED"));
    }

    // Run GA on a real .kp file via Person parser
    private static void runFile(String path) {
        System.out.println();
        System.out.println("---------- " + path + " ----------");
        try {
            KnapsackInstance inst = KpFileParser.parse(path);
            System.out.println("n = " + inst.weights.length
                    + ", capacity = " + inst.capacity
                    + ", knownOpt = " + inst.knownOptimal);

            GeneticAlgorithmSolver.Config cfg = new GeneticAlgorithmSolver.Config();
            // Slightly larger population for bigger instances
            int n = inst.weights.length;
            cfg.populationSize = Math.max(60, n * 8);
            cfg.maxGenerations = 150;
            cfg.mutationRate   = 0.03;
            cfg.stagnationLimit = 35;
            cfg.seed           = 12345L;

            GeneticAlgorithmSolver ga = new GeneticAlgorithmSolver(inst, cfg);
            GeneticAlgorithmSolver.Result r = ga.solve();

            GeneticAlgorithmSolver.printResult(inst, r);
        } catch (Exception e) {
            System.err.println("Error loading " + path + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
