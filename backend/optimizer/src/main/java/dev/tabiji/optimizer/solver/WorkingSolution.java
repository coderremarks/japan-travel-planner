package dev.tabiji.optimizer.solver;

import java.util.Arrays;

/**
 * Mutable search state: one route per day plus incrementally maintained totals. Every route held here is
 * feasible and the total cost never exceeds the budget — the search only applies changes that keep
 * those invariants (checked again independently by {@link PlanValidator} in tests).
 */
final class WorkingSolution {

    private final ProblemIndex p;
    private final int[][] routes;
    private final RouteEvaluation[] evaluations;
    private final int[] dayOf;
    private int mandatoryScheduled;
    private long score;
    private long travelMinutes;
    private long costYen;

    private WorkingSolution(ProblemIndex p, int[][] routes, RouteEvaluation[] evaluations, int[] dayOf) {
        this.p = p;
        this.routes = routes;
        this.evaluations = evaluations;
        this.dayOf = dayOf;
    }

    /** All days empty. Fails if a day cannot even connect its start and end locations in time. */
    static WorkingSolution empty(ProblemIndex p, RouteEvaluator evaluator) {
        int[] dayOf = new int[p.candidateCount];
        Arrays.fill(dayOf, -1);
        WorkingSolution solution =
                new WorkingSolution(p, new int[p.dayCount][], new RouteEvaluation[p.dayCount], dayOf);
        for (int d = 0; d < p.dayCount; d++) {
            RouteEvaluation evaluation = evaluator.evaluate(d, Routes.EMPTY);
            if (!evaluation.feasible()) {
                throw new IllegalArgumentException(
                        "day " + d + ": the start and end locations cannot be connected within the available time");
            }
            solution.routes[d] = Routes.EMPTY;
            solution.evaluations[d] = evaluation;
            solution.travelMinutes += evaluation.travelMinutes();
            solution.costYen += evaluation.costYen();
        }
        if (solution.costYen > p.budgetYen) {
            throw new IllegalArgumentException(
                    "the budget is below the unavoidable cost of travelling between the days' start and end locations");
        }
        return solution;
    }

    WorkingSolution copy() {
        WorkingSolution copy = new WorkingSolution(p, routes.clone(), evaluations.clone(), dayOf.clone());
        copy.mandatoryScheduled = mandatoryScheduled;
        copy.score = score;
        copy.travelMinutes = travelMinutes;
        copy.costYen = costYen;
        return copy;
    }

    /** The route of {@code day}. Callers must treat the array as read-only (see {@link Routes}). */
    int[] route(int day) {
        return routes[day];
    }

    RouteEvaluation evaluation(int day) {
        return evaluations[day];
    }

    int dayOf(int candidate) {
        return dayOf[candidate];
    }

    boolean isScheduled(int candidate) {
        return dayOf[candidate] >= 0;
    }

    long costYen() {
        return costYen;
    }

    int scheduledCount() {
        int count = 0;
        for (int[] route : routes) {
            count += route.length;
        }
        return count;
    }

    Objective objective() {
        return new Objective(mandatoryScheduled, score, travelMinutes, costYen);
    }

    /** Objective after replacing the route of {@code day} — without applying the change. */
    Objective objectiveWith(int day, RouteEvaluation replacement) {
        RouteEvaluation old = evaluations[day];
        return new Objective(
                mandatoryScheduled - old.mandatoryCount() + replacement.mandatoryCount(),
                score - old.score() + replacement.score(),
                travelMinutes - old.travelMinutes() + replacement.travelMinutes(),
                costYen - old.costYen() + replacement.costYen());
    }

    /** Objective after replacing the routes of two different days — without applying the change. */
    Objective objectiveWith(int dayA, RouteEvaluation replacementA, int dayB, RouteEvaluation replacementB) {
        RouteEvaluation oldA = evaluations[dayA];
        RouteEvaluation oldB = evaluations[dayB];
        return new Objective(
                mandatoryScheduled
                        - oldA.mandatoryCount()
                        - oldB.mandatoryCount()
                        + replacementA.mandatoryCount()
                        + replacementB.mandatoryCount(),
                score - oldA.score() - oldB.score() + replacementA.score() + replacementB.score(),
                travelMinutes
                        - oldA.travelMinutes()
                        - oldB.travelMinutes()
                        + replacementA.travelMinutes()
                        + replacementB.travelMinutes(),
                costYen - oldA.costYen() - oldB.costYen() + replacementA.costYen() + replacementB.costYen());
    }

    boolean withinBudget(Objective objective) {
        return objective.costYen() <= p.budgetYen;
    }

    void replace(int day, int[] route, RouteEvaluation evaluation) {
        if (!evaluation.feasible()) {
            throw new IllegalArgumentException("refusing to store an infeasible route");
        }
        for (int candidate : routes[day]) {
            if (dayOf[candidate] == day) {
                dayOf[candidate] = -1;
            }
        }
        for (int candidate : route) {
            dayOf[candidate] = day;
        }
        RouteEvaluation old = evaluations[day];
        mandatoryScheduled += evaluation.mandatoryCount() - old.mandatoryCount();
        score += evaluation.score() - old.score();
        travelMinutes += evaluation.travelMinutes() - old.travelMinutes();
        costYen += evaluation.costYen() - old.costYen();
        routes[day] = route;
        evaluations[day] = evaluation;
    }
}
