package dev.tabiji.optimizer.solver;

import dev.tabiji.optimizer.problem.Candidate;
import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.problem.TripDay;
import dev.tabiji.optimizer.solution.DaySchedule;
import dev.tabiji.optimizer.solution.PlanStatus;
import dev.tabiji.optimizer.solution.PlanTotals;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solution.ScheduledVisit;
import dev.tabiji.optimizer.solution.UnscheduledCandidate;
import dev.tabiji.optimizer.travel.Leg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Independent checker for {@link PlanningResult}s. It re-derives every time and total from the problem
 * using none of the solver's code, so a bug in the solver cannot hide itself. Used by property-based
 * tests over thousands of random instances and, in the application, as a last line of defence before a
 * plan is shown to a traveller.
 */
public final class PlanValidator {

    private PlanValidator() {}

    /** Human-readable descriptions of every violated constraint; empty if the plan is valid. */
    public static List<String> violations(PlanningProblem problem, PlanningResult result) {
        List<String> violations = new ArrayList<>();
        Map<String, Candidate> candidates = new HashMap<>();
        problem.candidates().forEach(candidate -> candidates.put(candidate.id(), candidate));
        if (result.days().size() != problem.days().size()) {
            violations.add("expected " + problem.days().size() + " days but got "
                    + result.days().size());
            return violations;
        }

        Set<String> scheduled = new HashSet<>();
        Totals totals = new Totals();
        for (int d = 0; d < problem.days().size(); d++) {
            checkDay(problem, d, result.days().get(d), candidates, scheduled, totals, violations);
        }

        Set<String> unscheduled = new HashSet<>();
        for (UnscheduledCandidate entry : result.unscheduled()) {
            if (!candidates.containsKey(entry.candidateId())) {
                violations.add("unknown unscheduled candidate " + entry.candidateId());
            } else if (scheduled.contains(entry.candidateId())) {
                violations.add(entry.candidateId() + " is both scheduled and unscheduled");
            } else if (!unscheduled.add(entry.candidateId())) {
                violations.add(entry.candidateId() + " is listed as unscheduled twice");
            }
        }
        for (Candidate candidate : problem.candidates()) {
            if (!scheduled.contains(candidate.id()) && !unscheduled.contains(candidate.id())) {
                violations.add(candidate.id() + " is neither scheduled nor reported as unscheduled");
            }
        }

        long totalCost = totals.transportCostYen + totals.admissionYen;
        if (totalCost > problem.budgetYen()) {
            violations.add("total cost " + totalCost + " exceeds budget " + problem.budgetYen());
        }
        PlanTotals reported = result.totals();
        PlanTotals expected = new PlanTotals(
                totals.scheduledCount,
                totals.mandatoryScheduled,
                (int) problem.candidates().stream().filter(Candidate::mandatory).count(),
                totals.score,
                totals.travelMinutes,
                totals.transportCostYen,
                totals.admissionYen);
        if (!reported.equals(expected)) {
            violations.add("reported totals " + reported + " differ from recomputed " + expected);
        }
        PlanStatus expectedStatus = expected.mandatoryScheduled() < expected.mandatoryTotal()
                ? PlanStatus.INFEASIBLE
                : result.unscheduled().isEmpty() ? PlanStatus.COMPLETE : PlanStatus.PARTIAL;
        if (result.status() != expectedStatus) {
            violations.add("status " + result.status() + " should be " + expectedStatus);
        }
        return violations;
    }

    private static void checkDay(
            PlanningProblem problem,
            int d,
            DaySchedule schedule,
            Map<String, Candidate> candidates,
            Set<String> scheduled,
            Totals totals,
            List<String> violations) {
        TripDay day = problem.days().get(d);
        String prefix = "day " + d + ": ";
        if (schedule.dayIndex() != d) {
            violations.add(prefix + "wrong day index " + schedule.dayIndex());
        }
        if (schedule.visits().size() > problem.maxVisitsPerDay()) {
            violations.add(
                    prefix + schedule.visits().size() + " visits exceed the limit of " + problem.maxVisitsPerDay());
        }
        int time = day.availableTime().startMinute();
        if (schedule.departureMinute() != time) {
            violations.add(prefix + "departs at " + schedule.departureMinute() + " instead of " + time);
        }
        int here = day.startLocation();
        int dayTravel = 0;
        long dayTransport = 0;
        long dayAdmission = 0;
        for (ScheduledVisit visit : schedule.visits()) {
            Candidate candidate = candidates.get(visit.candidateId());
            if (candidate == null) {
                violations.add(prefix + "unknown candidate " + visit.candidateId());
                return;
            }
            if (!scheduled.add(candidate.id())) {
                violations.add(prefix + candidate.id() + " is scheduled more than once");
            }
            Optional<Leg> leg = problem.travel().leg(here, candidate.location());
            if (leg.isEmpty()) {
                violations.add(prefix + "no route to " + candidate.id());
                return;
            }
            if (!leg.get().equals(visit.travelFromPrevious())) {
                violations.add(prefix + "leg to " + candidate.id() + " does not match the travel matrix");
            }
            int arrival = time + leg.get().minutes();
            if (visit.arrivalMinute() != arrival) {
                violations.add(prefix + candidate.id() + " arrival " + visit.arrivalMinute() + " should be " + arrival);
            }
            if (visit.startMinute() < arrival) {
                violations.add(prefix + candidate.id() + " starts before arrival");
            }
            if (visit.endMinute() - visit.startMinute() != candidate.visitMinutes()) {
                violations.add(prefix + candidate.id() + " visit length differs from " + candidate.visitMinutes());
            }
            if (!fitsOpeningHours(candidate.openingHoursByDay().get(d), visit)) {
                violations.add(prefix + candidate.id() + " visited outside its opening hours");
            }
            time = visit.endMinute();
            here = candidate.location();
            dayTravel += leg.get().minutes();
            dayTransport += leg.get().costYen();
            dayAdmission += candidate.admissionYen();
            totals.scheduledCount++;
            totals.score += candidate.score();
            if (candidate.mandatory()) {
                totals.mandatoryScheduled++;
            }
        }
        Optional<Leg> back = problem.travel().leg(here, day.endLocation());
        if (back.isEmpty()) {
            violations.add(prefix + "no route back to the end location");
            return;
        }
        if (!back.get().equals(schedule.returnLeg())) {
            violations.add(prefix + "return leg does not match the travel matrix");
        }
        int returnArrival = time + back.get().minutes();
        if (schedule.returnArrivalMinute() != returnArrival) {
            violations.add(prefix + "return arrival " + schedule.returnArrivalMinute() + " should be " + returnArrival);
        }
        if (returnArrival > day.availableTime().endMinute()) {
            violations.add(prefix + "returns at " + returnArrival + ", after the day ends at "
                    + day.availableTime().endMinute());
        }
        dayTravel += back.get().minutes();
        dayTransport += back.get().costYen();
        if (schedule.travelMinutes() != dayTravel
                || schedule.transportCostYen() != dayTransport
                || schedule.admissionYen() != dayAdmission) {
            violations.add(prefix + "day totals do not match the visits");
        }
        totals.travelMinutes += dayTravel;
        totals.transportCostYen += dayTransport;
        totals.admissionYen += dayAdmission;
    }

    private static boolean fitsOpeningHours(List<TimeWindow> windows, ScheduledVisit visit) {
        for (TimeWindow window : windows) {
            if (window.startMinute() <= visit.startMinute() && visit.endMinute() <= window.endMinute()) {
                return true;
            }
        }
        return false;
    }

    private static final class Totals {
        int scheduledCount;
        int mandatoryScheduled;
        long score;
        int travelMinutes;
        long transportCostYen;
        long admissionYen;
    }
}
