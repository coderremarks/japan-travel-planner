package dev.tabiji.optimizer.graph;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Binary min-heap over node indices {@code 0..capacity-1} with a {@code decrease-key} operation.
 *
 * <p>Compared with {@link java.util.PriorityQueue} plus "lazy deletion", the indexed heap holds each
 * node at most once ({@code O(V)} memory instead of {@code O(E)}), never boxes, and makes
 * decrease-key {@code O(log V)}. Ties are broken by node index so the extraction order — and therefore
 * every shortest-path tree — is deterministic.
 */
final class IndexedMinHeap {

    private static final int ABSENT = -1;

    private final int[] heap;
    private final int[] positionOf;
    private final double[] keyOf;
    private int size;

    IndexedMinHeap(int capacity) {
        heap = new int[capacity];
        positionOf = new int[capacity];
        keyOf = new double[capacity];
        Arrays.fill(positionOf, ABSENT);
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    boolean contains(int node) {
        return positionOf[node] != ABSENT;
    }

    double keyOf(int node) {
        return keyOf[node];
    }

    /**
     * Inserts {@code node} with {@code key}, or lowers its key if it is already present with a larger
     * one. Returns {@code true} if the heap changed.
     */
    boolean insertOrDecrease(int node, double key) {
        int position = positionOf[node];
        if (position == ABSENT) {
            heap[size] = node;
            positionOf[node] = size;
            keyOf[node] = key;
            siftUp(size++);
            return true;
        }
        if (key < keyOf[node]) {
            keyOf[node] = key;
            siftUp(position);
            return true;
        }
        return false;
    }

    int pollMin() {
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        int min = heap[0];
        size--;
        if (size > 0) {
            moveTo(heap[size], 0);
            siftDown(0);
        }
        positionOf[min] = ABSENT;
        return min;
    }

    private void siftUp(int position) {
        int node = heap[position];
        while (position > 0) {
            int parentPosition = (position - 1) >>> 1;
            int parent = heap[parentPosition];
            if (!less(node, parent)) {
                break;
            }
            moveTo(parent, position);
            position = parentPosition;
        }
        moveTo(node, position);
    }

    private void siftDown(int position) {
        int node = heap[position];
        int half = size >>> 1;
        while (position < half) {
            int childPosition = 2 * position + 1;
            int child = heap[childPosition];
            int rightPosition = childPosition + 1;
            if (rightPosition < size && less(heap[rightPosition], child)) {
                childPosition = rightPosition;
                child = heap[rightPosition];
            }
            if (!less(child, node)) {
                break;
            }
            moveTo(child, position);
            position = childPosition;
        }
        moveTo(node, position);
    }

    private boolean less(int a, int b) {
        int byKey = Double.compare(keyOf[a], keyOf[b]);
        return byKey < 0 || (byKey == 0 && a < b);
    }

    private void moveTo(int node, int position) {
        heap[position] = node;
        positionOf[node] = position;
    }
}
