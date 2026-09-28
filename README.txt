CS214 ASSIGNMENT 2 - 0/1 KNAPSACK: DYNAMIC PROGRAMMING vs GENETIC ALGORITHM
==========================================================================
This project solves the 0/1 knapsack problem two ways, on the benchmark
files p01-p08, and compares them:
  - an exact solver (dynamic programming, DP)
  - a non-deterministic solver (genetic algorithm, GA)


HOW TO COMPILE AND RUN
==========================================================================
Needs a JDK (17 or newer). Open a terminal in this folder (the one that
contains the "src" and "benchmarks" folders), then:

    mkdir class
    javac -d class src/*.java
    java -cp class Main

(On Windows PowerShell, use  mkdir class  and  javac -d class (Get-ChildItem src\*.java)
if the wildcard is not accepted.)

Main shows a menu:
    1) Solve one benchmark with DP
    2) Solve one benchmark with GA
    3) Check DP against KNOWN_OPTIMA on all benchmarks
    4) Run the full experiment (30 GA runs per benchmark, stats, graphs, report)

The full experiment can also be run directly:   java -cp class ExperimentRunner
While it runs, open results/live_graph.html in a browser; it refreshes every
second and shows the current GA-vs-DP graph.

If the biggest benchmark (p08) ever fails with "OutOfMemoryError", run with
more memory:   java -Xmx2g -cp class Main
(the DP table for p08 has about 150 million cells).


FILES (all in src/)
==========================================================================
KnapsackInstance.java        [Part 1]  Holds one problem: capacity, item
                                       weights, item values, known optimum.

KpFileParser.java            [Part 1]  Reads a .kp file into a KnapsackInstance.

Dpsolver.java                [Part 1]  The exact DP solver. Returns the optimal
                                       profit, which items are packed, the NFC
                                       (table cells computed) and the "staircase"
                                       data used by the graph.

GeneticAlgorithmSolver.java  [Part 2]  The GA. Binary chromosomes, tournament
                                       selection, one-point crossover, bit-flip
                                       mutation, elitism, and a REPAIR operator
                                       for overweight chromosomes (drops the
                                       packed item with the worst profit/weight
                                       ratio until the chromosome fits).

GAMain.java                  [Part 2]  Member 2's own demo of the GA.

ExperimentRunner.java        [Part 3]  Runs DP once and the GA 30 times on each
                                       benchmark; writes the statistics, graphs
                                       and report into results/. It calls the
                                       solvers above.

Main.java                              The menu that ties everything together.

benchmarks/                  p01.kp - p08.kp and KNOWN_OPTIMA.txt.
results/                     Output of the last full experiment:
    empirical_results.csv      one row per benchmark: SR, best/mean/worst GA
                               profit, mean NFC, DP profit and NFC, times
    run_details.csv            every single run (seed, profit, success, NFC)
    trajectories.csv           the curve points used in the graphs
    graphs/p01.svg ... p08.svg profit-vs-NFC graph for each benchmark
    live_graph.html / .svg     the live graph page
    REPORT.md                  results table, graphs and method description


WHAT THE NUMBERS MEAN
==========================================================================
NFC  Number of Function Calls, used as the machine-independent "amount of work".
     DP: table cells computed = n x (capacity + 1).
     GA: chromosome fitness evaluations.
SR   Success rate: fraction of the 30 GA runs that reached the known optimum.

The DP line on each graph is a staircase: after each item is processed it
shows the best profit at full capacity using the items so far, and it only
reaches the optimum after the last item. The GA line is the mean best-so-far
profit over the 30 runs.


RESULTS OF THE LAST RUN (30 GA runs per benchmark)
==========================================================================
DP matched KNOWN_OPTIMA on all 8 benchmarks.

    Benchmark   n   Capacity    GA success rate   Mean GA NFC   DP NFC
    p01        10        165        100%                202          1,660
    p02         5         26        100%                118            135
    p03         6        190        100%                125          1,146
    p04         7         50        100%                118            357
    p05         8        104         73%                733            840
    p06         7        170        100%                121          1,197
    p07        15        750         77%              1,842         11,265
    p08        24  6,404,180         67%              4,714    153,700,344

On small problems DP needs about as much or less work than the GA. On p08 the
capacity is huge, so DP needs about 150 million cell computations against a
few thousand GA evaluations - but the GA only reaches the exact optimum in
about two thirds of the runs. That trade-off (exact but capacity-dependent vs
fast but not guaranteed) is the point of the comparison.

(GA success rates and NFC are reproducible: each run uses a fixed seed derived
from the benchmark and the run number. Times in milliseconds vary by machine.)


CHANGES MADE WHEN THE THREE PARTS WERE COMBINED
==========================================================================
Dpsolver.java              added the staircase history for the graph; made
                           runAllBenchmarksDp() and printSingleFileResults()
                           public so Main can call them. The DP logic itself is
                           unchanged.
GeneticAlgorithmSolver.java  the default random seed was based on the clock in
                           milliseconds, so runs started together could share a
                           seed; it is now unique per run.
GAMain.java                p08 added to the demo.
ExperimentRunner.java      previously contained its own GA (penalty function,
                           greedy seed) and its own DP. It now calls
                           Dpsolver and GeneticAlgorithmSolver, so the report
                           describes the code that was actually written by each
                           member. DP is drawn as a staircase. Report text
                           updated (repair operator, DP table).


NOTES FOR THE WRITE-UP
==========================================================================
- The GA stops early when it reaches the known optimum or stops improving for
  35 generations, so its NFC differs from run to run.
- On the graphs the GA line starts very close to the optimum, because a random
  population of 60+ chromosomes with repair is already good on these small
  problems. Its climb is small but is recorded in results/trajectories.csv.
- Reference for DP: standard bottom-up (tabulation) dynamic programming for
  0/1 knapsack. The approach was studied from the YouTube video
  “0/1 Knapsack using Dynamic Programming”
  (https://www.youtube.com/watch?v=qxWu-SeAqe4) and online explanations
  of the logic were used to clarify the steps. The final implementation
  and verification against the published optima were completed by the group.


ADDITIONAL HARD INSTANCES
==========================================================================
In addition to the standard P01–P08 benchmarks, five larger “hard”
instances were generated using a faithful re-implementation of David
Pisinger’s genhard generator (http://hjemmesider.diku.dk/~pisinger/codes.html):

  hard30u300.kp   – n=30, uncorrelated
  hard40u100.kp   – n=40, uncorrelated (larger capacity)
  hard40w150.kp   – n=40, weakly correlated
  hard40s1.kp     – n=40, strongly correlated
  hard50u200.kp   – n=50, uncorrelated (larger capacity)

Optimal profits for these instances were computed with the exact DP
solver and stored in the OPTIMAL field of each .kp file. The full
experiment (option 4) automatically includes every .kp file found in
the benchmarks/ folder, so these instances appear in the statistical
tables and graphs alongside P01–P08.