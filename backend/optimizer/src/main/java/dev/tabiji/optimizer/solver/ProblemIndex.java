package dev.tabiji.optimizer.solver;

import dev.tabiji.optimizer.problem.Candidate;
import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.problem.TripDay;
import dev.tabiji.optimizer.travel.TravelMatrix;
import java.util.List;

/**
 * Array-of-primitives view of a {@link PlanningProblem} for the solver's inner loops. Candidates and
 * days are addressed by their index in the problem's lists, which also makes iteration order — and
 * therefore the result — deterministic.
 */
final class ProblemIndex {

    final PlanningProblem problem;
    final int candidateCount;
    final int dayCount;
    final int maxVisitsPerDay;
    final long budgetYen;
    final int mandatoryTotal;
    final TravelMatrix travel;

    final int[] location;
    final int[] visitMinutes;
    final int[] admissionYen;
    final int[] score;
    final boolean[] mandatory;
    /** {@code windows[candidate][day]} — sorted, non-overlapping opening windows. */
    final TimeWindow[][][] windows;

    final int[] dayStart;
    final int[] dayEnd;
    final int[] dayStartLocation;
    final int[] dayEndLocation;

    ProblemIndex(PlanningProblem problem) {
        this.problem = problem;
        this.travel = problem.travel();
        this.maxVisitsPerDay = problem.maxVisitsPerDay();
        this.budgetYen = problem.budgetYen();
        List<Candidate> candidates = problem.candidates();
        List<TripDay> days = problem.days();
        this.candidateCount = candidates.size();
        this.dayCount = days.size();

        location = new int[candidateCount];
        visitMinutes = new int[candidateCount];
        admissionYen = new int[candidateCount];
        score = new int[candidateCount];
        mandatory = new boolean[candidateCount];
        windows = new TimeWindow[candidateCount][dayCount][];
        int mandatoryCount = 0;
        for (int c = 0; c < candidateCount; c++) {
            Candidate candidate = candidates.get(c);
            location[c] = candidate.location();
            visitMinutes[c] = candidate.visitMinutes();
            admissionYen[c] = candidate.admissionYen();
            score[c] = candidate.score();
            mandatory[c] = candidate.mandatory();
            if (candidate.mandatory()) {
                mandatoryCount++;
            }
            for (int d = 0; d < dayCount; d++) {
                windows[c][d] = candidate.openingHoursByDay().get(d).toArray(TimeWindow[]::new);
            }
        }
        mandatoryTotal = mandatoryCount;

        dayStart = new int[dayCount];
        dayEnd = new int[dayCount];
        dayStartLocation = new int[dayCount];
        dayEndLocation = new int[dayCount];
        for (int d = 0; d < dayCount; d++) {
            TripDay day = days.get(d);
            dayStart[d] = day.availableTime().startMinute();
            dayEnd[d] = day.availableTime().endMinute();
            dayStartLocation[d] = day.startLocation();
            dayEndLocation[d] = day.endLocation();
        }
    }

    String id(int candidate) {
        return problem.candidates().get(candidate).id();
    }

    /**
     * Earliest start time {@code >= arrival} at which the whole visit fits inside one opening window on
     * {@code day}, or {@code -1}. Monotone in {@code arrival}: arriving later never allows starting
     * earlier, which is what makes the forward schedule simulation exact.
     */
    int earliestStart(int candidate, int day, int arrival) {
        int duration = visitMinutes[candidate];
        for (TimeWindow window : windows[candidate][day]) {
            int start = Math.max(arrival, window.startMinute());
            if (start + duration <= window.endMinute()) {
                return start;
            }
        }
        return -1;
    }
}
