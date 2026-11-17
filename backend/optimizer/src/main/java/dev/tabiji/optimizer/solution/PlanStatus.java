package dev.tabiji.optimizer.solution;

/** Overall outcome of an optimisation run. */
public enum PlanStatus {
    /** Every candidate is scheduled. */
    COMPLETE,
    /** Every must-visit is scheduled; some optional candidates are not. */
    PARTIAL,
    /** At least one must-visit could not be scheduled; the plan is a best effort. */
    INFEASIBLE
}
