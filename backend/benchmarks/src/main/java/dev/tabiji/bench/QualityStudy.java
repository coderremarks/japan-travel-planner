package dev.tabiji.bench;

import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.solution.PlanTotals;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solver.ItineraryOptimizer;
import dev.tabiji.optimizer.solver.PlanValidator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Solution-quality study, printed as Markdown for {@code docs/benchmarks.md}.
 *
 * <ol>
 *   <li>Optimality: heuristic vs the exhaustive solver on small random instances.</li>
 *   <li>Value: heuristic vs the traveller's own order ("keep my order") on realistic sizes.</li>
 * </ol>
 *
 * Run: {@code java -cp benchmarks/target/benchmarks.jar dev.tabiji.bench.QualityStudy}
 */
public final class QualityStudy {

    private static final Comparator<PlanTotals> OBJECTIVE = Comparator.comparingInt(PlanTotals::mandatoryScheduled)
            .thenComparingLong(PlanTotals::score)
            .thenComparing(PlanTotals::travelMinutes, Comparator.reverseOrder())
            .thenComparing(PlanTotals::totalCostYen, Comparator.reverseOrder());

    private QualityStudy() {}

    public static void main(String[] args) {
        ItineraryOptimizer optimizer = new ItineraryOptimizer();
        optimality(optimizer, 1_000);
        valueOverOwnOrder(optimizer);
    }

    private static void optimality(ItineraryOptimizer optimizer, int instances) {
        int optimal = 0;
        int invalid = 0;
        double[] scoreGaps = new double[instances];
        double[] travelGaps = new double[instances];
        for (int i = 0; i < instances; i++) {
            long seed = 10_000L + i;
            PlanningProblem problem = BenchmarkProblems.generate(seed, 3 + i % 6, 1 + i % 3);
            PlanningResult heuristic = optimizer.optimize(problem, Strategy.OPTIMIZE);
            PlanningResult exact = optimizer.optimize(problem, Strategy.EXACT);
            if (!PlanValidator.violations(problem, heuristic).isEmpty()) {
                invalid++;
            }
            if (OBJECTIVE.compare(heuristic.totals(), exact.totals()) == 0) {
                optimal++;
            }
            long exactScore = Math.max(1, exact.totals().score());
            scoreGaps[i] = 100.0 * (exact.totals().score() - heuristic.totals().score()) / exactScore;
            boolean sameScore = exact.totals().score() == heuristic.totals().score();
            travelGaps[i] = sameScore && exact.totals().travelMinutes() > 0
                    ? 100.0
                            * (heuristic.totals().travelMinutes()
                                    - exact.totals().travelMinutes())
                            / exact.totals().travelMinutes()
                    : 0.0;
        }
        System.out.println("## Heuristic vs exact optimum (" + instances + " instances, 3–8 places, 1–3 days)");
        System.out.println();
        System.out.println("| Metric | Value |");
        System.out.println("| --- | --- |");
        System.out.printf(
                Locale.ROOT,
                "| Same objective as the exact optimum | %d / %d (%.1f %%) |%n",
                optimal,
                instances,
                100.0 * optimal / instances);
        System.out.printf(Locale.ROOT, "| Plans violating a hard constraint | %d |%n", invalid);
        System.out.printf(
                Locale.ROOT,
                "| Mean score gap | %.2f %% |%n",
                Arrays.stream(scoreGaps).average().orElse(0));
        System.out.printf(
                Locale.ROOT,
                "| Worst score gap | %.1f %% |%n",
                Arrays.stream(scoreGaps).max().orElse(0));
        System.out.printf(
                Locale.ROOT,
                "| Mean extra travel at equal score | %.2f %% |%n",
                Arrays.stream(travelGaps).average().orElse(0));
        System.out.println();
    }

    private static void valueOverOwnOrder(ItineraryOptimizer optimizer) {
        System.out.println("## Optimized vs the traveller's own order (20 instances per size)");
        System.out.println();
        System.out.println(
                "| Places × days | Places scheduled (own order → optimized) | Travel minutes per scheduled place "
                        + "(own order → optimized) | Median runtime (ms) |");
        System.out.println("| --- | --- | --- | --- |");
        int[][] sizes = {{10, 3}, {25, 3}, {25, 7}, {50, 7}, {50, 14}};
        for (int[] size : sizes) {
            double keptPlaces = 0;
            double optimizedPlaces = 0;
            double keptTravelPerPlace = 0;
            double optimizedTravelPerPlace = 0;
            long[] runtimes = new long[20];
            for (int i = 0; i < 20; i++) {
                PlanningProblem problem = BenchmarkProblems.generate(50_000L + i, size[0], size[1]);
                PlanningResult kept = optimizer.optimize(problem, Strategy.KEEP_ORDER);
                long started = System.nanoTime();
                PlanningResult optimized = optimizer.optimize(problem, Strategy.OPTIMIZE);
                runtimes[i] = (System.nanoTime() - started) / 1_000_000;
                keptPlaces += kept.totals().scheduledCount();
                optimizedPlaces += optimized.totals().scheduledCount();
                keptTravelPerPlace += perPlace(kept.totals());
                optimizedTravelPerPlace += perPlace(optimized.totals());
            }
            Arrays.sort(runtimes);
            System.out.printf(
                    Locale.ROOT,
                    "| %d × %d | %.1f → %.1f | %.1f → %.1f | %d |%n",
                    size[0],
                    size[1],
                    keptPlaces / 20,
                    optimizedPlaces / 20,
                    keptTravelPerPlace / 20,
                    optimizedTravelPerPlace / 20,
                    runtimes[10]);
        }
        System.out.println();
    }

    private static double perPlace(PlanTotals totals) {
        return totals.scheduledCount() == 0 ? 0 : (double) totals.travelMinutes() / totals.scheduledCount();
    }
}
