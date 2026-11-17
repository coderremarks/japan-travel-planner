package dev.tabiji.optimizer.solver;

import dev.tabiji.optimizer.solution.DaySchedule;
import dev.tabiji.optimizer.solution.ScheduledVisit;
import dev.tabiji.optimizer.travel.Leg;
import dev.tabiji.optimizer.travel.TravelMatrix;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulates a day route forward in time: travel, wait for opening if early, visit, continue. A route is
 * feasible when every visit fits an opening window, the traveller reaches the end location before the
 * day's available time ends, and the daily visit limit holds. {@code O(route length)}.
 */
final class RouteEvaluator {

    private final ProblemIndex p;

    RouteEvaluator(ProblemIndex problemIndex) {
        this.p = problemIndex;
    }

    RouteEvaluation evaluate(int day, int[] route) {
        if (route.length > p.maxVisitsPerDay) {
            return RouteEvaluation.INFEASIBLE;
        }
        return simulate(day, route, null);
    }

    /** Same as {@link #evaluate} but without the daily visit limit; used only to explain results. */
    RouteEvaluation evaluateIgnoringDailyLimit(int day, int[] route) {
        return simulate(day, route, null);
    }

    DaySchedule describe(int day, int[] route) {
        List<ScheduledVisit> visits = new ArrayList<>(route.length);
        RouteEvaluation evaluation = simulate(day, route, visits);
        if (!evaluation.feasible()) {
            throw new IllegalStateException("cannot describe an infeasible route for day " + day);
        }
        int lastLocation = route.length == 0 ? p.dayStartLocation[day] : p.location[route[route.length - 1]];
        Leg returnLeg = p.travel.requireLeg(lastLocation, p.dayEndLocation[day]);
        return new DaySchedule(
                day,
                p.dayStart[day],
                visits,
                returnLeg,
                evaluation.returnArrivalMinute(),
                evaluation.travelMinutes(),
                evaluation.transportCostYen(),
                evaluation.admissionYen());
    }

    private RouteEvaluation simulate(int day, int[] route, List<ScheduledVisit> sink) {
        TravelMatrix travel = p.travel;
        int time = p.dayStart[day];
        int here = p.dayStartLocation[day];
        int travelMinutes = 0;
        long transportCost = 0;
        long admission = 0;
        long score = 0;
        int mandatoryCount = 0;
        for (int candidate : route) {
            int next = p.location[candidate];
            if (!travel.isReachable(here, next)) {
                return RouteEvaluation.INFEASIBLE;
            }
            Leg leg = travel.requireLeg(here, next);
            int arrival = time + leg.minutes();
            int start = p.earliestStart(candidate, day, arrival);
            if (start < 0) {
                return RouteEvaluation.INFEASIBLE;
            }
            int end = start + p.visitMinutes[candidate];
            if (end > p.dayEnd[day]) {
                return RouteEvaluation.INFEASIBLE;
            }
            if (sink != null) {
                sink.add(new ScheduledVisit(p.id(candidate), arrival, start, end, leg));
            }
            travelMinutes += leg.minutes();
            transportCost += leg.costYen();
            admission += p.admissionYen[candidate];
            score += p.score[candidate];
            if (p.mandatory[candidate]) {
                mandatoryCount++;
            }
            time = end;
            here = next;
        }
        int home = p.dayEndLocation[day];
        if (!travel.isReachable(here, home)) {
            return RouteEvaluation.INFEASIBLE;
        }
        Leg back = travel.requireLeg(here, home);
        int returnArrival = time + back.minutes();
        if (returnArrival > p.dayEnd[day]) {
            return RouteEvaluation.INFEASIBLE;
        }
        travelMinutes += back.minutes();
        transportCost += back.costYen();
        return new RouteEvaluation(true, travelMinutes, transportCost, admission, score, mandatoryCount, returnArrival);
    }
}
