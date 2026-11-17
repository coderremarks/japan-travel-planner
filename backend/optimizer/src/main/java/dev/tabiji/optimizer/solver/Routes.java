package dev.tabiji.optimizer.solver;

/**
 * Pure functions that build modified copies of a route ({@code int[]} of candidate indices). Routes are
 * never mutated in place, so a solution can share route arrays with its copies safely.
 */
final class Routes {

    static final int[] EMPTY = new int[0];

    private Routes() {}

    static int[] insertAt(int[] route, int position, int candidate) {
        int[] result = new int[route.length + 1];
        System.arraycopy(route, 0, result, 0, position);
        result[position] = candidate;
        System.arraycopy(route, position, result, position + 1, route.length - position);
        return result;
    }

    static int[] removeAt(int[] route, int position) {
        int[] result = new int[route.length - 1];
        System.arraycopy(route, 0, result, 0, position);
        System.arraycopy(route, position + 1, result, position, route.length - position - 1);
        return result;
    }

    static int[] replaceAt(int[] route, int position, int candidate) {
        int[] result = route.clone();
        result[position] = candidate;
        return result;
    }

    /** Reverses the segment {@code [from, to]} (both inclusive) — the 2-opt move. */
    static int[] reverse(int[] route, int from, int to) {
        int[] result = route.clone();
        for (int i = from, j = to; i < j; i++, j--) {
            int tmp = result[i];
            result[i] = result[j];
            result[j] = tmp;
        }
        return result;
    }

    /** Moves the element at {@code from} so that it ends up at index {@code to} — the or-opt move. */
    static int[] move(int[] route, int from, int to) {
        return insertAt(removeAt(route, from), to, route[from]);
    }

    static int indexOf(int[] route, int candidate) {
        for (int i = 0; i < route.length; i++) {
            if (route[i] == candidate) {
                return i;
            }
        }
        return -1;
    }
}
