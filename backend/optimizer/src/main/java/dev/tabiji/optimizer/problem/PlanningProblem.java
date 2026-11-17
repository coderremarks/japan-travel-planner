package dev.tabiji.optimizer.problem;

import dev.tabiji.optimizer.travel.TravelMatrix;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Complete, validated input of one optimisation run. All constraints that the solver treats as hard:
 * opening windows, each day's available time, {@code maxVisitsPerDay}, and {@code budgetYen} (admissions
 * plus transport, summed over the whole trip).
 */
public record PlanningProblem(
        List<Candidate> candidates, List<TripDay> days, TravelMatrix travel, int maxVisitsPerDay, long budgetYen) {

    public static final long UNLIMITED_BUDGET = Long.MAX_VALUE;

    public PlanningProblem {
        candidates = List.copyOf(candidates);
        days = List.copyOf(days);
        Objects.requireNonNull(travel, "travel");
        if (days.isEmpty()) {
            throw new IllegalArgumentException("a trip needs at least one day");
        }
        if (maxVisitsPerDay < 1) {
            throw new IllegalArgumentException("maxVisitsPerDay must be >= 1");
        }
        if (budgetYen < 0) {
            throw new IllegalArgumentException("budgetYen must be >= 0");
        }
        for (TripDay day : days) {
            checkLocation(day.startLocation(), travel);
            checkLocation(day.endLocation(), travel);
        }
        Set<String> ids = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (!ids.add(candidate.id())) {
                throw new IllegalArgumentException("duplicate candidate id " + candidate.id());
            }
            checkLocation(candidate.location(), travel);
            if (candidate.openingHoursByDay().size() != days.size()) {
                throw new IllegalArgumentException("candidate " + candidate.id()
                        + " must define opening hours for each of the " + days.size() + " days");
            }
        }
    }

    private static void checkLocation(int location, TravelMatrix travel) {
        if (location >= travel.size()) {
            throw new IllegalArgumentException(
                    "location " + location + " is outside the travel matrix of size " + travel.size());
        }
    }
}
