package dev.tabiji.optimizer.graph;

import java.util.Arrays;

/** Result of a single-source shortest-path computation: distances and a predecessor tree. */
public final class ShortestPathTree {

    private final int source;
    private final double[] distance;
    private final int[] predecessor;

    ShortestPathTree(int source, double[] distance, int[] predecessor) {
        this.source = source;
        this.distance = distance;
        this.predecessor = predecessor;
    }

    public int source() {
        return source;
    }

    /** Shortest distance from the source, or {@code +Infinity} when {@code node} is unreachable. */
    public double distanceTo(int node) {
        return distance[node];
    }

    public boolean hasPathTo(int node) {
        return distance[node] != Double.POSITIVE_INFINITY;
    }

    /**
     * Nodes on the shortest path from the source to {@code node}, both inclusive; an empty array when
     * {@code node} is unreachable. Runs in {@code O(path length)}.
     */
    public int[] pathTo(int node) {
        if (!hasPathTo(node)) {
            return new int[0];
        }
        int length = 1;
        for (int v = node; v != source; v = predecessor[v]) {
            length++;
        }
        int[] path = new int[length];
        int v = node;
        for (int i = length - 1; i >= 0; i--) {
            path[i] = v;
            v = predecessor[v];
        }
        return path;
    }

    /** Defensive copy of all distances; intended for tests and diagnostics. */
    public double[] distances() {
        return Arrays.copyOf(distance, distance.length);
    }
}
