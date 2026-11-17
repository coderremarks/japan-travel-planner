package dev.tabiji.optimizer.solver;

/**
 * Finds the cheapest feasible way to insert one candidate into the current solution, over all days on
 * which it is feasible alone and all positions. "Cheapest" means the smallest increase of the day's
 * completion time (which accounts for travel <em>and</em> waiting), then the smallest added travel, then
 * the earliest day and position. {@code O(days × route length²)} per candidate.
 */
final class InsertionSearch {

    /** Which constraints to relax. Only {@link #STRICT} is used for building plans. */
    enum Limits {
        STRICT,
        IGNORE_DAILY_LIMIT,
        IGNORE_BUDGET
    }

    /**
     * @param addedDuration increase of the day's completion time (the "shift" of Vansteenwegen et al.)
     */
    record Insertion(
            int candidate, int day, int[] route, RouteEvaluation evaluation, int addedDuration, int addedTravel) {}

    private final ProblemIndex p;
    private final RouteEvaluator evaluator;

    InsertionSearch(ProblemIndex p, RouteEvaluator evaluator) {
        this.p = p;
        this.evaluator = evaluator;
    }

    /** Best insertion over all {@code allowedDays}, or {@code null} if there is none. */
    Insertion best(WorkingSolution solution, int candidate, boolean[] allowedDays, Limits limits) {
        Insertion best = null;
        for (int day = 0; day < p.dayCount; day++) {
            if (allowedDays[day]) {
                best = scanDay(solution, candidate, day, solution.route(day), limits, best);
            }
        }
        return best;
    }

    /** Best insertion on one day whose route is first replaced by {@code baseRoute}, or {@code null}. */
    Insertion bestOnDay(WorkingSolution solution, int candidate, int day, int[] baseRoute, Limits limits) {
        return scanDay(solution, candidate, day, baseRoute, limits, null);
    }

    private Insertion scanDay(
            WorkingSolution solution, int candidate, int day, int[] baseRoute, Limits limits, Insertion incumbent) {
        if (limits != Limits.IGNORE_DAILY_LIMIT && baseRoute.length >= p.maxVisitsPerDay) {
            return incumbent;
        }
        RouteEvaluation current = solution.evaluation(day);
        RouteEvaluation base = baseRoute == solution.route(day) ? current : evaluator.evaluate(day, baseRoute);
        if (!base.feasible()) {
            return incumbent;
        }
        Insertion best = incumbent;
        for (int position = 0; position <= baseRoute.length; position++) {
            int[] route = Routes.insertAt(baseRoute, position, candidate);
            RouteEvaluation evaluation = limits == Limits.IGNORE_DAILY_LIMIT
                    ? evaluator.evaluateIgnoringDailyLimit(day, route)
                    : evaluator.evaluate(day, route);
            if (!evaluation.feasible()) {
                continue;
            }
            if (limits != Limits.IGNORE_BUDGET) {
                long newCost = solution.costYen() - current.costYen() + evaluation.costYen();
                if (newCost > p.budgetYen) {
                    continue;
                }
            }
            int addedDuration = evaluation.returnArrivalMinute() - base.returnArrivalMinute();
            int addedTravel = evaluation.travelMinutes() - base.travelMinutes();
            if (best == null
                    || addedDuration < best.addedDuration()
                    || (addedDuration == best.addedDuration() && addedTravel < best.addedTravel())) {
                best = new Insertion(candidate, day, route, evaluation, addedDuration, addedTravel);
            }
        }
        return best;
    }
}
