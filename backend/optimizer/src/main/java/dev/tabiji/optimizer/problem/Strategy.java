package dev.tabiji.optimizer.problem;

/** How a plan should be produced. */
public enum Strategy {
    /** Heuristic optimisation: construction, local search and iterated local search. */
    OPTIMIZE,
    /** Respect the given candidate order, filling days one after another (the "do it by hand" baseline). */
    KEEP_ORDER,
    /** Exhaustive search; exponential, only for small problems (verification and benchmarks). */
    EXACT
}
