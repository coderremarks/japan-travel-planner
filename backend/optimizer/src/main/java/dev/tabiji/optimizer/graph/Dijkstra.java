package dev.tabiji.optimizer.graph;

import java.util.Arrays;

/**
 * Dijkstra's single-source shortest-path algorithm.
 *
 * <p>Complexity: {@code O((V + E) log V)} time with the indexed binary heap, {@code O(V)} extra
 * memory. Requires non-negative weights, which {@link WeightedDigraph.Builder} guarantees.
 *
 * <p>Why Dijkstra and not A*: the planner needs <em>one-to-many</em> distances (from each origin
 * station to every other station) to fill a travel matrix. A* only pays off for single-pair queries
 * where a good heuristic prunes the search towards one goal; for one-to-all queries it degenerates to
 * Dijkstra with extra heuristic evaluations.
 */
public final class Dijkstra {

    private Dijkstra() {}

    public static ShortestPathTree shortestPaths(WeightedDigraph graph, int source) {
        int n = graph.nodeCount();
        if (source < 0 || source >= n) {
            throw new IndexOutOfBoundsException("source " + source + " outside [0, " + n + ")");
        }
        double[] distance = new double[n];
        int[] predecessor = new int[n];
        boolean[] settled = new boolean[n];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        Arrays.fill(predecessor, -1);

        IndexedMinHeap heap = new IndexedMinHeap(n);
        distance[source] = 0.0;
        heap.insertOrDecrease(source, 0.0);

        while (!heap.isEmpty()) {
            int u = heap.pollMin();
            settled[u] = true;
            double du = distance[u];
            for (int e = graph.firstEdge(u), end = graph.endEdge(u); e < end; e++) {
                int v = graph.target(e);
                if (settled[v]) {
                    continue;
                }
                double candidate = du + graph.weight(e);
                if (candidate < distance[v]) {
                    distance[v] = candidate;
                    predecessor[v] = u;
                    heap.insertOrDecrease(v, candidate);
                }
            }
        }
        return new ShortestPathTree(source, distance, predecessor);
    }
}
