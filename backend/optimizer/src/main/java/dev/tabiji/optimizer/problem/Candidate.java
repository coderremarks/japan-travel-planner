package dev.tabiji.optimizer.problem;

import java.util.List;
import java.util.Objects;

/**
 * A place that may be scheduled.
 *
 * @param id                  caller-defined identifier, unique within a problem
 * @param location            index into the problem's travel matrix
 * @param visitMinutes        time spent on site
 * @param admissionYen        estimated entrance fee (counts towards the budget)
 * @param score               preference value; higher means more desirable (0 = never worth a detour)
 * @param mandatory           must-visit: the plan is infeasible without it
 * @param openingHoursByDay   for each trip day, the opening windows on that date (empty = closed). The
 *                            visit must fit entirely inside one window.
 */
public record Candidate(
        String id,
        int location,
        int visitMinutes,
        int admissionYen,
        int score,
        boolean mandatory,
        List<List<TimeWindow>> openingHoursByDay) {

    public Candidate {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("candidate id must not be blank");
        }
        if (location < 0) {
            throw new IllegalArgumentException("location must be >= 0");
        }
        if (visitMinutes < 1 || visitMinutes > TimeWindow.MINUTES_PER_DAY) {
            throw new IllegalArgumentException("visitMinutes must be within [1, 1440]");
        }
        if (admissionYen < 0 || score < 0) {
            throw new IllegalArgumentException("admissionYen and score must be >= 0");
        }
        if (!mandatory && score == 0) {
            // An optional place worth nothing would never be scheduled; reject it instead of silently ignoring it.
            throw new IllegalArgumentException("optional candidate " + id + " needs a score >= 1");
        }
        // List.copyOf makes the immutability explicit (also to static analysis); inner lists are immutable too.
        openingHoursByDay = List.copyOf(
                openingHoursByDay.stream().map(TimeWindow::normalize).toList());
    }
}
