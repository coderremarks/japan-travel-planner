package dev.tabiji.optimizer.testsupport;

import dev.tabiji.optimizer.problem.Candidate;
import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.problem.TripDay;
import dev.tabiji.optimizer.solution.PlanTotals;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solution.ScheduledVisit;
import dev.tabiji.optimizer.solution.UnscheduledCandidate;
import dev.tabiji.optimizer.solution.UnscheduledReason;
import dev.tabiji.optimizer.travel.Leg;
import dev.tabiji.optimizer.travel.LegSource;
import dev.tabiji.optimizer.travel.TravelMatrix;
import dev.tabiji.optimizer.travel.TravelMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Small builders that keep the scenario tests readable. Location 0 is always the hotel. */
public final class Fixtures {

    public static final TimeWindow NINE_TO_SIX = TimeWindow.between(9, 0, 18, 0);

    private Fixtures() {}

    /** Matrix where every pair is connected with the given minutes and cost. */
    public static TravelMatrix uniformMatrix(int size, int minutes, int costYen) {
        TravelMatrix.Builder builder = TravelMatrix.builder(size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i != j) {
                    builder.set(i, j, leg(minutes, costYen));
                }
            }
        }
        return builder.build();
    }

    /** Points on a line (km from the hotel); travel takes {@code minutesPerKm} per km, at no cost. */
    public static TravelMatrix lineMatrix(double[] kilometres, int minutesPerKm) {
        TravelMatrix.Builder builder = TravelMatrix.builder(kilometres.length);
        for (int i = 0; i < kilometres.length; i++) {
            for (int j = 0; j < kilometres.length; j++) {
                if (i != j) {
                    int minutes = (int) Math.round(Math.abs(kilometres[i] - kilometres[j]) * minutesPerKm);
                    builder.set(i, j, leg(minutes, 0));
                }
            }
        }
        return builder.build();
    }

    public static Leg leg(int minutes, int costYen) {
        return Leg.estimate(TravelMode.TRANSIT, minutes, costYen, minutes * 400, LegSource.DISTANCE_MODEL);
    }

    public static List<List<TimeWindow>> everyDay(int days, TimeWindow... windows) {
        List<List<TimeWindow>> result = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            result.add(List.of(windows));
        }
        return result;
    }

    public static Candidate optional(
            String id, int location, int visitMinutes, int score, List<List<TimeWindow>> hours) {
        return new Candidate(id, location, visitMinutes, 0, score, false, hours);
    }

    public static Candidate mustVisit(String id, int location, int visitMinutes, List<List<TimeWindow>> hours) {
        return new Candidate(id, location, visitMinutes, 0, 1, true, hours);
    }

    public static PlanningProblem problem(
            List<Candidate> candidates,
            int days,
            TimeWindow dayWindow,
            TravelMatrix matrix,
            int maxVisits,
            long budget) {
        List<TripDay> tripDays = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            tripDays.add(new TripDay(dayWindow, 0, 0));
        }
        return new PlanningProblem(candidates, tripDays, matrix, maxVisits, budget);
    }

    public static Optional<UnscheduledReason> reasonFor(PlanningResult result, String id) {
        return result.unscheduled().stream()
                .filter(entry -> entry.candidateId().equals(id))
                .map(UnscheduledCandidate::reason)
                .findFirst();
    }

    /** Day index on which {@code id} is visited, or -1. */
    public static int dayOf(PlanningResult result, String id) {
        for (var day : result.days()) {
            for (ScheduledVisit visit : day.visits()) {
                if (visit.candidateId().equals(id)) {
                    return day.dayIndex();
                }
            }
        }
        return -1;
    }

    public static List<String> visitOrder(PlanningResult result, int day) {
        return result.days().get(day).visits().stream()
                .map(ScheduledVisit::candidateId)
                .toList();
    }

    /** Same lexicographic order the optimizer uses: greater is better. */
    public static final Comparator<PlanTotals> OBJECTIVE = Comparator.comparingInt(PlanTotals::mandatoryScheduled)
            .thenComparingLong(PlanTotals::score)
            .thenComparing(PlanTotals::travelMinutes, Comparator.reverseOrder())
            .thenComparing(PlanTotals::totalCostYen, Comparator.reverseOrder());
}
