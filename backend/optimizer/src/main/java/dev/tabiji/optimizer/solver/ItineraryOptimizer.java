package dev.tabiji.optimizer.solver;

import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solution.SolverStats;
import dev.tabiji.optimizer.solver.FeasibilityAnalyzer.CandidateAnalysis;
import java.util.Objects;

/**
 * Entry point of the engine. Stateless and thread-safe: every call builds its own working data.
 *
 * <pre>{@code
 * PlanningResult plan = new ItineraryOptimizer().optimize(problem, Strategy.OPTIMIZE);
 * }</pre>
 */
public final class ItineraryOptimizer {

    private final SolverSettings settings;

    public ItineraryOptimizer() {
        this(SolverSettings.DEFAULT);
    }

    public ItineraryOptimizer(SolverSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public SolverSettings settings() {
        return settings;
    }

    /**
     * Plans the trip.
     *
     * @throws IllegalArgumentException if a day cannot connect its start and end locations within its
     *     available time, if the budget is below that unavoidable travel cost, or if {@link Strategy#EXACT}
     *     is requested for a problem that is too large for exhaustive search
     */
    public PlanningResult optimize(PlanningProblem problem, Strategy strategy) {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(strategy, "strategy");
        long started = System.nanoTime();
        ProblemIndex index = new ProblemIndex(problem);
        RouteEvaluator evaluator = new RouteEvaluator(index);
        CandidateAnalysis[] analysis = FeasibilityAnalyzer.analyze(index, evaluator);
        InsertionSearch insertion = new InsertionSearch(index, evaluator);

        Search search = switch (strategy) {
            case OPTIMIZE -> {
                IteratedLocalSearch.Outcome outcome =
                        new IteratedLocalSearch(index, evaluator, insertion, analysis, settings).run(started);
                yield new Search(
                        outcome.best(),
                        outcome.constructionScheduled(),
                        outcome.constructionTravelMinutes(),
                        outcome.iterations(),
                        outcome.improvingMoves(),
                        outcome.timeLimitReached());
            }
            case KEEP_ORDER -> Search.direct(KeepOrderScheduler.schedule(index, evaluator, analysis));
            case EXACT -> Search.direct(ExhaustiveSolver.solve(index, evaluator, analysis));
        };

        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        SolverStats stats = new SolverStats(
                strategy,
                settings.seed(),
                search.constructionScheduled(),
                search.constructionTravelMinutes(),
                search.iterations(),
                search.improvingMoves(),
                search.timeLimitReached(),
                elapsedMillis);
        return ResultAssembler.assemble(index, evaluator, insertion, analysis, search.solution(), stats);
    }

    private record Search(
            WorkingSolution solution,
            int constructionScheduled,
            int constructionTravelMinutes,
            int iterations,
            int improvingMoves,
            boolean timeLimitReached) {

        static Search direct(WorkingSolution solution) {
            return new Search(
                    solution,
                    solution.scheduledCount(),
                    Math.toIntExact(solution.objective().travelMinutes()),
                    0,
                    0,
                    false);
        }
    }
}
