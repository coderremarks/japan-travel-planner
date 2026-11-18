package dev.tabiji.bench;

import dev.tabiji.optimizer.graph.Dijkstra;
import dev.tabiji.optimizer.graph.ShortestPathTree;
import dev.tabiji.optimizer.graph.WeightedDigraph;
import java.util.SplittableRandom;
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
 * One-to-all Dijkstra on square grid graphs with random positive weights (a road-network-like
 * topology: average degree 4). Expected growth is {@code O((V + E) log V)}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class DijkstraBenchmark {

    @Param({"1024", "16384", "262144"})
    int nodes;

    private WeightedDigraph graph;

    @Setup
    public void setUp() {
        int side = (int) Math.sqrt(nodes);
        SplittableRandom random = new SplittableRandom(7);
        WeightedDigraph.Builder builder = WeightedDigraph.builder(side * side);
        for (int row = 0; row < side; row++) {
            for (int col = 0; col < side; col++) {
                int node = row * side + col;
                if (col + 1 < side) {
                    builder.addUndirectedEdge(node, node + 1, 1 + random.nextInt(10));
                }
                if (row + 1 < side) {
                    builder.addUndirectedEdge(node, node + side, 1 + random.nextInt(10));
                }
            }
        }
        graph = builder.build();
    }

    @Benchmark
    public ShortestPathTree oneToAll() {
        return Dijkstra.shortestPaths(graph, 0);
    }
}
