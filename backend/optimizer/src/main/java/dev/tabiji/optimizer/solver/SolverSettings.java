package dev.tabiji.optimizer.solver;

import java.time.Duration;
import java.util.Objects;

/**
 * Tuning knobs of the iterated local search.
 *
 * @param seed                      seed of the perturbation RNG; fixed so identical input gives identical plans
 * @param maxIterations             upper bound on perturbation + repair iterations
 * @param restartAfterNonImproving  return to the best solution after this many iterations without a new best
 * @param stopAfterNonImproving     stop early after this many iterations without a new best
 * @param timeLimit                 safety cap on wall-clock time; reported in the stats when hit
 */
public record SolverSettings(
        long seed, int maxIterations, int restartAfterNonImproving, int stopAfterNonImproving, Duration timeLimit) {

    public static final SolverSettings DEFAULT = new SolverSettings(20_261_118L, 400, 40, 160, Duration.ofSeconds(3));

    public SolverSettings {
        Objects.requireNonNull(timeLimit, "timeLimit");
        if (maxIterations < 0 || restartAfterNonImproving < 1 || stopAfterNonImproving < 1) {
            throw new IllegalArgumentException("iteration limits must be positive");
        }
        if (timeLimit.isNegative() || timeLimit.isZero()) {
            throw new IllegalArgumentException("timeLimit must be positive");
        }
    }

    public SolverSettings withMaxIterations(int iterations) {
        return new SolverSettings(seed, iterations, restartAfterNonImproving, stopAfterNonImproving, timeLimit);
    }

    public SolverSettings withSeed(long newSeed) {
        return new SolverSettings(newSeed, maxIterations, restartAfterNonImproving, stopAfterNonImproving, timeLimit);
    }
}
