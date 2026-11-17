package dev.tabiji.optimizer.graph;

import java.util.Arrays;

/**
 * An immutable directed graph with non-negative edge weights, stored in compressed sparse row (CSR)
 * form: the outgoing edges of node {@code u} occupy indices {@code [offsets[u], offsets[u + 1])} of
 * {@code targets} and {@code weights}.
 *
 * <p>CSR keeps all edges in three flat arrays, which is cache-friendly and avoids one object per
 * edge; for the road/rail-sized graphs this engine targets that matters more than mutability.
 */
public final class WeightedDigraph {

    private final int nodeCount;
    private final int[] offsets;
    private final int[] targets;
    private final double[] weights;

    private WeightedDigraph(int nodeCount, int[] offsets, int[] targets, double[] weights) {
        this.nodeCount = nodeCount;
        this.offsets = offsets;
        this.targets = targets;
        this.weights = weights;
    }

    public static Builder builder(int nodeCount) {
        return new Builder(nodeCount);
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int edgeCount() {
        return targets.length;
    }

    /** Index of the first outgoing edge of {@code node}. */
    int firstEdge(int node) {
        return offsets[node];
    }

    /** One past the index of the last outgoing edge of {@code node}. */
    int endEdge(int node) {
        return offsets[node + 1];
    }

    int target(int edge) {
        return targets[edge];
    }

    double weight(int edge) {
        return weights[edge];
    }

    /** Weight of the cheapest direct edge {@code from -> to}, or {@code +Infinity} if there is none. */
    public double directEdgeWeight(int from, int to) {
        checkNode(from, nodeCount);
        checkNode(to, nodeCount);
        double best = Double.POSITIVE_INFINITY;
        for (int e = offsets[from]; e < offsets[from + 1]; e++) {
            if (targets[e] == to) {
                best = Math.min(best, weights[e]);
            }
        }
        return best;
    }

    private static void checkNode(int node, int nodeCount) {
        if (node < 0 || node >= nodeCount) {
            throw new IndexOutOfBoundsException("node " + node + " outside [0, " + nodeCount + ")");
        }
    }

    /** Collects edges and produces the CSR arrays with a counting sort in {@code O(V + E)}. */
    public static final class Builder {
        private final int nodeCount;
        private int[] from = new int[16];
        private int[] to = new int[16];
        private double[] weight = new double[16];
        private int size;

        private Builder(int nodeCount) {
            if (nodeCount < 0) {
                throw new IllegalArgumentException("nodeCount must be >= 0: " + nodeCount);
            }
            this.nodeCount = nodeCount;
        }

        /**
         * Adds the directed edge {@code from -> to}. Dijkstra's correctness depends on non-negative
         * weights, so negative, NaN and infinite weights are rejected here rather than silently
         * producing wrong shortest paths later.
         */
        public Builder addEdge(int fromNode, int toNode, double edgeWeight) {
            checkNode(fromNode, nodeCount);
            checkNode(toNode, nodeCount);
            if (!(edgeWeight >= 0.0) || Double.isInfinite(edgeWeight)) {
                throw new IllegalArgumentException("edge weight must be finite and >= 0: " + edgeWeight);
            }
            if (size == from.length) {
                int newCapacity = size * 2;
                from = Arrays.copyOf(from, newCapacity);
                to = Arrays.copyOf(to, newCapacity);
                weight = Arrays.copyOf(weight, newCapacity);
            }
            from[size] = fromNode;
            to[size] = toNode;
            weight[size] = edgeWeight;
            size++;
            return this;
        }

        /** Adds edges in both directions with the same weight. */
        public Builder addUndirectedEdge(int a, int b, double edgeWeight) {
            addEdge(a, b, edgeWeight);
            return addEdge(b, a, edgeWeight);
        }

        public WeightedDigraph build() {
            int[] offsets = new int[nodeCount + 1];
            for (int i = 0; i < size; i++) {
                offsets[from[i] + 1]++;
            }
            for (int node = 0; node < nodeCount; node++) {
                offsets[node + 1] += offsets[node];
            }
            int[] cursor = Arrays.copyOf(offsets, nodeCount);
            int[] targets = new int[size];
            double[] weights = new double[size];
            for (int i = 0; i < size; i++) {
                int slot = cursor[from[i]]++;
                targets[slot] = to[i];
                weights[slot] = weight[i];
            }
            return new WeightedDigraph(nodeCount, offsets, targets, weights);
        }
    }
}
