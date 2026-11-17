package dev.tabiji.optimizer.solution;

import java.util.List;
import java.util.Objects;

/** Output of the optimizer: the schedule, what was left out and why, and how it was computed. */
public record PlanningResult(
        PlanStatus status,
        List<DaySchedule> days,
        List<UnscheduledCandidate> unscheduled,
        PlanTotals totals,
        SolverStats stats) {

    public PlanningResult {
        Objects.requireNonNull(status, "status");
        days = List.copyOf(days);
        unscheduled = List.copyOf(unscheduled);
        Objects.requireNonNull(totals, "totals");
        Objects.requireNonNull(stats, "stats");
    }
}
