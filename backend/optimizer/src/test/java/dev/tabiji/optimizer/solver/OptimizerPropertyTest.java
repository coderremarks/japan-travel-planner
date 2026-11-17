package dev.tabiji.optimizer.solver;

import static dev.tabiji.optimizer.testsupport.Fixtures.OBJECTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.testsupport.RandomProblems;
import org.junit.jupiter.api.Test;

/**
 * Property-based tests over seeded random instances (replayable: the seed is in every failure message).
 *
 * <ul>
 *   <li>No strategy ever violates a hard constraint (checked by the independent {@link PlanValidator}).</li>
 *   <li>The heuristic never beats the exact solver (validates the exact solver) and matches the optimum
 *       on the vast majority of small instances (measures the heuristic).</li>
 *   <li>Identical input produces an identical plan.</li>
 * </ul>
 */
class OptimizerPropertyTest {

    private final ItineraryOptimizer optimizer = new ItineraryOptimizer();

    @Test
    void plansNeverViolateHardConstraints() {
        for (long seed = 1; seed <= 600; seed++) {
            int candidates = 1 + (int) (seed % 30);
            int days = 1 + (int) (seed % 5);
            PlanningProblem problem = RandomProblems.generate(seed, candidates, days);
            for (Strategy strategy : new Strategy[] {Strategy.OPTIMIZE, Strategy.KEEP_ORDER}) {
                PlanningResult result = optimizer.optimize(problem, strategy);
                assertThat(PlanValidator.violations(problem, result))
                        .as("seed %d, %s", seed, strategy)
                        .isEmpty();
                assertThat(result.stats().timeLimitReached())
                        .as("seed %d", seed)
                        .isFalse();
            }
        }
    }

    @Test
    void heuristicMatchesTheExactOptimumOnSmallInstances() {
        int instances = 400;
        int optimal = 0;
        for (long seed = 1_000; seed < 1_000 + instances; seed++) {
            int candidates = 2 + (int) (seed % 6);
            int days = 1 + (int) (seed % 2);
            PlanningProblem problem = RandomProblems.generate(seed, candidates, days);

            PlanningResult heuristic = optimizer.optimize(problem, Strategy.OPTIMIZE);
            PlanningResult exact = optimizer.optimize(problem, Strategy.EXACT);

            assertThat(PlanValidator.violations(problem, exact))
                    .as("seed %d", seed)
                    .isEmpty();
            int comparison = OBJECTIVE.compare(heuristic.totals(), exact.totals());
            assertThat(comparison)
                    .as("seed %d: heuristic %s must not beat exact %s", seed, heuristic.totals(), exact.totals())
                    .isLessThanOrEqualTo(0);
            if (comparison == 0) {
                optimal++;
            }
        }
        double share = optimal / (double) instances;
        assertThat(share).as("share of instances solved to optimality").isGreaterThanOrEqualTo(0.95);
    }

    @Test
    void identicalInputYieldsAnIdenticalPlan() {
        for (long seed = 5_000; seed < 5_020; seed++) {
            PlanningProblem problem = RandomProblems.generate(seed, 35, 4);

            PlanningResult first = optimizer.optimize(problem, Strategy.OPTIMIZE);
            PlanningResult second = optimizer.optimize(problem, Strategy.OPTIMIZE);

            assertThat(second.days()).as("seed %d", seed).isEqualTo(first.days());
            assertThat(second.unscheduled()).isEqualTo(first.unscheduled());
            assertThat(second.totals()).isEqualTo(first.totals());
            assertThat(second.stats().iterations()).isEqualTo(first.stats().iterations());
        }
    }

    @Test
    void optimizerIsNeverWorseThanTheTravellersOwnOrder() {
        for (long seed = 7_000; seed < 7_150; seed++) {
            PlanningProblem problem = RandomProblems.generate(seed, 4 + (int) (seed % 20), 1 + (int) (seed % 4));

            PlanningResult optimized = optimizer.optimize(problem, Strategy.OPTIMIZE);
            PlanningResult kept = optimizer.optimize(problem, Strategy.KEEP_ORDER);

            assertThat(OBJECTIVE.compare(optimized.totals(), kept.totals()))
                    .as("seed %d", seed)
                    .isGreaterThanOrEqualTo(0);
        }
    }
}
