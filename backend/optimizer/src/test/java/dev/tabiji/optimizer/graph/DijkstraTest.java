package dev.tabiji.optimizer.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DijkstraTest {

    /**
     * <pre>
     *   0 --4--> 1 --1--> 3
     *   |        ^        ^
     *   1        2        |
     *   v        |        |
     *   2 -------+---5----+
     * </pre>
     */
    private static WeightedDigraph sampleGraph() {
        return WeightedDigraph.builder(5)
                .addEdge(0, 1, 4)
                .addEdge(0, 2, 1)
                .addEdge(2, 1, 2)
                .addEdge(1, 3, 1)
                .addEdge(2, 3, 5)
                .build();
    }

    @Test
    void findsShortestDistancesAndPaths() {
        ShortestPathTree tree = Dijkstra.shortestPaths(sampleGraph(), 0);

        assertThat(tree.distanceTo(0)).isZero();
        assertThat(tree.distanceTo(1)).isEqualTo(3.0); // 0 -> 2 -> 1 beats the direct edge of 4
        assertThat(tree.distanceTo(3)).isEqualTo(4.0);
        assertThat(tree.pathTo(3)).containsExactly(0, 2, 1, 3);
        assertThat(tree.pathTo(0)).containsExactly(0);
    }

    @Test
    void reportsUnreachableNodes() {
        ShortestPathTree tree = Dijkstra.shortestPaths(sampleGraph(), 0);

        assertThat(tree.hasPathTo(4)).isFalse();
        assertThat(tree.distanceTo(4)).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(tree.pathTo(4)).isEmpty();
    }

    @Test
    void edgesAreDirected() {
        ShortestPathTree fromThree = Dijkstra.shortestPaths(sampleGraph(), 3);
        assertThat(fromThree.hasPathTo(0)).isFalse();
    }

    @Test
    void handlesZeroWeightEdgesAndParallelEdges() {
        WeightedDigraph graph = WeightedDigraph.builder(3)
                .addEdge(0, 1, 0)
                .addEdge(1, 2, 7)
                .addEdge(1, 2, 3)
                .build();

        ShortestPathTree tree = Dijkstra.shortestPaths(graph, 0);
        assertThat(tree.distanceTo(2)).isEqualTo(3.0);
        assertThat(graph.directEdgeWeight(1, 2)).isEqualTo(3.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsWeightsThatWouldBreakDijkstra(double weight) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WeightedDigraph.builder(2).addEdge(0, 1, weight));
    }

    @Test
    void rejectsUnknownNodes() {
        assertThatThrownBy(() -> WeightedDigraph.builder(2).addEdge(0, 2, 1.0))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> Dijkstra.shortestPaths(sampleGraph(), 9))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    /** Property test: on random graphs Dijkstra must agree with Floyd–Warshall, an independent algorithm. */
    @Test
    void agreesWithFloydWarshallOnRandomGraphs() {
        SplittableRandom random = new SplittableRandom(7);
        for (int instance = 0; instance < 200; instance++) {
            int n = 2 + random.nextInt(25);
            int edges = random.nextInt(n * 4);
            WeightedDigraph.Builder builder = WeightedDigraph.builder(n);
            double[][] reference = new double[n][n];
            for (double[] row : reference) {
                Arrays.fill(row, Double.POSITIVE_INFINITY);
            }
            for (int i = 0; i < n; i++) {
                reference[i][i] = 0.0;
            }
            for (int e = 0; e < edges; e++) {
                int from = random.nextInt(n);
                int to = random.nextInt(n);
                double weight = random.nextInt(50);
                builder.addEdge(from, to, weight);
                reference[from][to] = Math.min(reference[from][to], weight);
            }
            floydWarshall(reference);
            WeightedDigraph graph = builder.build();
            for (int source = 0; source < n; source++) {
                ShortestPathTree tree = Dijkstra.shortestPaths(graph, source);
                for (int target = 0; target < n; target++) {
                    assertThat(tree.distanceTo(target))
                            .as("instance %d, %d -> %d", instance, source, target)
                            .isEqualTo(reference[source][target]);
                    assertPathIsConsistent(graph, tree, target);
                }
            }
        }
    }

    private static void floydWarshall(double[][] d) {
        int n = d.length;
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (d[i][k] + d[k][j] < d[i][j]) {
                        d[i][j] = d[i][k] + d[k][j];
                    }
                }
            }
        }
    }

    /** The reconstructed path must use real edges and add up to the reported distance. */
    private static void assertPathIsConsistent(WeightedDigraph graph, ShortestPathTree tree, int target) {
        int[] path = tree.pathTo(target);
        if (!tree.hasPathTo(target)) {
            assertThat(path).isEmpty();
            return;
        }
        assertThat(path[0]).isEqualTo(tree.source());
        assertThat(path[path.length - 1]).isEqualTo(target);
        double length = 0.0;
        for (int i = 1; i < path.length; i++) {
            length += graph.directEdgeWeight(path[i - 1], path[i]);
        }
        assertThat(length).isCloseTo(tree.distanceTo(target), within(1e-9));
    }

    @Test
    void indexedHeapReturnsKeysInSortedOrderWithDecreaseKey() {
        SplittableRandom random = new SplittableRandom(11);
        int n = 500;
        IndexedMinHeap heap = new IndexedMinHeap(n);
        double[] keys = new double[n];
        for (int node = 0; node < n; node++) {
            keys[node] = random.nextInt(1_000);
            heap.insertOrDecrease(node, keys[node]);
        }
        for (int i = 0; i < 300; i++) {
            int node = random.nextInt(n);
            double lower = keys[node] - random.nextInt(100);
            if (heap.insertOrDecrease(node, lower)) {
                keys[node] = lower;
            }
            assertThat(heap.insertOrDecrease(node, keys[node] + 1)).isFalse();
        }
        assertThat(heap.size()).isEqualTo(n);
        double previous = Double.NEGATIVE_INFINITY;
        int previousNode = -1;
        while (!heap.isEmpty()) {
            int node = heap.pollMin();
            assertThat(heap.contains(node)).isFalse();
            assertThat(keys[node]).isGreaterThanOrEqualTo(previous);
            if (keys[node] == previous) {
                assertThat(node).as("ties are broken by node index").isGreaterThan(previousNode);
            }
            previous = keys[node];
            previousNode = node;
        }
        assertThatThrownBy(heap::pollMin).isInstanceOf(java.util.NoSuchElementException.class);
    }
}
