package dev.tabiji.optimizer.solution;

import dev.tabiji.optimizer.problem.Strategy;
import java.util.Objects;

/**
 * How the plan was found. {@code elapsedMillis} is informational only; everything else is deterministic
 * for a given input and seed.
 *
 * @param constructionScheduled   visits after the greedy construction, before any improvement
 * @param constructionTravelMinutes travel minutes after the greedy construction
 * @param iterations              iterated-local-search iterations performed
 * @param improvingMoves          accepted local-search moves (across all iterations)
 * @param timeLimitReached        {@code true} if the safety time cap stopped the search early
 */
public record SolverStats(
        Strategy strategy,
        long seed,
        int constructionScheduled,
        int constructionTravelMinutes,
        int iterations,
        int improvingMoves,
        boolean timeLimitReached,
        long elapsedMillis) {

    public SolverStats {
        Objects.requireNonNull(strategy, "strategy");
    }
}
