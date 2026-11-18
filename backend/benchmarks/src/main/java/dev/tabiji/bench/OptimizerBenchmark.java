package dev.tabiji.bench;

import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solver.ItineraryOptimizer;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * End-to-end optimisation time (construction + local search + iterated local search + polishing)
 * for realistic problem sizes. The API caps itineraries at 50 places and 14 days.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class OptimizerBenchmark {

    @Param({"10", "25", "50"})
    int candidates;

    @Param({"3", "7", "14"})
    int days;

    private PlanningProblem problem;
    private final ItineraryOptimizer optimizer = new ItineraryOptimizer();

    @Setup
    public void setUp() {
        problem = BenchmarkProblems.generate(42, candidates, days);
    }

    @Benchmark
    public PlanningResult optimize() {
        return optimizer.optimize(problem, Strategy.OPTIMIZE);
    }

    @Benchmark
    public PlanningResult keepOrderBaseline() {
        return optimizer.optimize(problem, Strategy.KEEP_ORDER);
    }
}
