package dev.tabiji.optimizer.solver;

import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.solver.FeasibilityAnalyzer.CandidateAnalysis;
import dev.tabiji.optimizer.solver.InsertionSearch.Insertion;
import dev.tabiji.optimizer.solver.InsertionSearch.Limits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Descent local search over neighbourhoods ordered by the objective level they improve:
 *
 * <ol>
 *   <li><b>insert must-visit</b> — schedule a missing must-visit directly;</li>
 *   <li><b>repair must-visit</b> — make room for it by dropping one or two optional visits, or by moving
 *       another must-visit to a different day;</li>
 *   <li><b>insert optional</b> — best multi-resource ratio (see {@link #insertBestRatio});</li>
 *   <li><b>replace</b> — swap a scheduled optional place for an unscheduled one that scores higher, or
 *       scores the same but saves travel or money;</li>
 *   <li><b>2-opt</b> and <b>or-opt</b> (chains of 1–3 visits) — reorder within a day;</li>
 *   <li><b>relocate</b> and <b>swap</b> — move or exchange visits between days.</li>
 * </ol>
 *
 * Each step applies the first strictly improving move it finds and the loop restarts from the top, so
 * higher-priority improvements are always tried first. Every accepted move strictly increases the
 * lexicographic {@link Objective}, which guarantees termination; {@link #MAX_MOVES} is a safety net.
 */
final class LocalSearch {

    static final int MAX_MOVES = 20_000;
    /** Days with at most this many visits are re-ordered by exhaustive search during polishing (7! = 5,040). */
    static final int EXACT_REORDER_MAX_VISITS = 7;

    private static final int MAX_OR_OPT_CHAIN = 3;
    private static final double NOISE = 0.3;

    private final ProblemIndex p;
    private final RouteEvaluator evaluator;
    private final InsertionSearch insertion;
    private final CandidateAnalysis[] analysis;
    private final int[] mandatoryOrder;

    LocalSearch(ProblemIndex p, RouteEvaluator evaluator, InsertionSearch insertion, CandidateAnalysis[] analysis) {
        this.p = p;
        this.evaluator = evaluator;
        this.insertion = insertion;
        this.analysis = analysis;
        this.mandatoryOrder = hardestMandatoryFirst(p, analysis);
    }

    /** Feasible must-visits ordered by fewest feasible days, then shortest total opening time. */
    private static int[] hardestMandatoryFirst(ProblemIndex p, CandidateAnalysis[] analysis) {
        List<Integer> mandatory = new ArrayList<>();
        for (int c = 0; c < p.candidateCount; c++) {
            if (p.mandatory[c] && analysis[c].isFeasibleAlone()) {
                mandatory.add(c);
            }
        }
        mandatory.sort(Comparator.<Integer>comparingInt(c -> analysis[c].feasibleDayCount())
                .thenComparingInt(c -> totalOpenMinutes(p, c))
                .thenComparingInt(c -> c));
        return mandatory.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int totalOpenMinutes(ProblemIndex p, int candidate) {
        int total = 0;
        for (int d = 0; d < p.dayCount; d++) {
            for (var window : p.windows[candidate][d]) {
                total += window.lengthMinutes();
            }
        }
        return total;
    }

    int[] mandatoryOrder() {
        return mandatoryOrder.clone();
    }

    /** Deterministic descent; returns the number of applied moves. */
    int improve(WorkingSolution s) {
        return improve(s, null);
    }

    /**
     * Descent in which optional insertions are chosen with multiplicative noise on their priority
     * (GRASP-style randomisation) when {@code noise} is non-null. Only the <em>choice</em> among improving
     * insertions is randomised; every applied move still improves the objective.
     */
    int improve(WorkingSolution s, SplittableRandom noise) {
        int moves = 0;
        while (moves < MAX_MOVES
                && (insertMandatory(s)
                        || repairMandatory(s)
                        || insertBestRatio(s, noise)
                        || replace(s)
                        || twoOpt(s)
                        || orOpt(s)
                        || relocate(s)
                        || swap(s))) {
            moves++;
        }
        return moves;
    }

    /** Final polishing: exact re-ordering of short days, then descent again, until nothing changes. */
    int polish(WorkingSolution s) {
        int moves = 0;
        for (int round = 0; round < 5; round++) {
            int reordered = reorderExactly(s);
            int improved = improve(s);
            moves += reordered + improved;
            if (reordered + improved == 0) {
                break;
            }
        }
        return moves;
    }

    boolean insertMandatory(WorkingSolution s) {
        for (int m : mandatoryOrder) {
            if (s.isScheduled(m)) {
                continue;
            }
            Insertion best = insertion.best(s, m, analysis[m].feasibleDays(), Limits.STRICT);
            if (best != null) {
                s.replace(best.day(), best.route(), best.evaluation());
                return true;
            }
        }
        return false;
    }

    /** Makes room for a missing must-visit; any such move raises the top objective level. */
    boolean repairMandatory(WorkingSolution s) {
        for (int m : mandatoryOrder) {
            if (!s.isScheduled(m)
                    && (dropOptionalsFor(s, m, 1) || dropOptionalsFor(s, m, 2) || moveMandatoryAsideFor(s, m))) {
                return true;
            }
        }
        return false;
    }

    private boolean dropOptionalsFor(WorkingSolution s, int m, int dropCount) {
        Insertion best = null;
        long bestLostScore = Long.MAX_VALUE;
        for (int day = 0; day < p.dayCount; day++) {
            if (!analysis[m].feasibleDays()[day]) {
                continue;
            }
            int[] route = s.route(day);
            for (int i = 0; i < route.length; i++) {
                if (p.mandatory[route[i]]) {
                    continue;
                }
                if (dropCount == 1) {
                    long lost = p.score[route[i]];
                    if (lost < bestLostScore) {
                        Insertion candidate = insertion.bestOnDay(s, m, day, Routes.removeAt(route, i), Limits.STRICT);
                        if (candidate != null) {
                            best = candidate;
                            bestLostScore = lost;
                        }
                    }
                    continue;
                }
                for (int j = i + 1; j < route.length; j++) {
                    if (p.mandatory[route[j]]) {
                        continue;
                    }
                    long lost = (long) p.score[route[i]] + p.score[route[j]];
                    if (lost < bestLostScore) {
                        int[] reduced = Routes.removeAt(Routes.removeAt(route, j), i);
                        Insertion candidate = insertion.bestOnDay(s, m, day, reduced, Limits.STRICT);
                        if (candidate != null) {
                            best = candidate;
                            bestLostScore = lost;
                        }
                    }
                }
            }
        }
        if (best == null) {
            return false;
        }
        s.replace(best.day(), best.route(), best.evaluation());
        return true;
    }

    private boolean moveMandatoryAsideFor(WorkingSolution s, int m) {
        for (int day = 0; day < p.dayCount; day++) {
            if (!analysis[m].feasibleDays()[day]) {
                continue;
            }
            int[] route = s.route(day);
            for (int i = 0; i < route.length; i++) {
                int other = route[i];
                if (!p.mandatory[other]) {
                    continue;
                }
                Insertion withM = insertion.bestOnDay(s, m, day, Routes.removeAt(route, i), Limits.IGNORE_BUDGET);
                if (withM == null) {
                    continue;
                }
                for (int target = 0; target < p.dayCount; target++) {
                    if (target == day || !analysis[other].feasibleDays()[target]) {
                        continue;
                    }
                    Insertion moved = insertion.bestOnDay(s, other, target, s.route(target), Limits.IGNORE_BUDGET);
                    if (moved == null) {
                        continue;
                    }
                    Objective after = s.objectiveWith(day, withM.evaluation(), target, moved.evaluation());
                    if (s.withinBudget(after) && after.isBetterThan(s.objective())) {
                        s.replace(day, withM.route(), withM.evaluation());
                        s.replace(target, moved.route(), moved.evaluation());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Inserts the optional candidate with the highest priority
     * {@code score² / (timeShare + costShare)}, where {@code timeShare} is the fraction of the trip's
     * remaining free time the insertion consumes and {@code costShare} the fraction of the remaining
     * budget. This is the classic multi-resource knapsack ratio: with an unlimited budget it reduces to
     * the time-based ratio of Vansteenwegen et al.; with a tight budget it stops a single expensive place
     * from crowding out several cheaper ones.
     */
    boolean insertBestRatio(WorkingSolution s, SplittableRandom noise) {
        long remainingTime = 0;
        for (int day = 0; day < p.dayCount; day++) {
            remainingTime += p.dayEnd[day] - s.evaluation(day).returnArrivalMinute();
        }
        boolean budgetLimited = p.budgetYen != PlanningProblem.UNLIMITED_BUDGET;
        long remainingBudget = budgetLimited ? p.budgetYen - s.costYen() : Long.MAX_VALUE;

        Insertion best = null;
        double bestPriority = Double.NEGATIVE_INFINITY;
        for (int c = 0; c < p.candidateCount; c++) {
            if (p.mandatory[c] || s.isScheduled(c) || !analysis[c].isFeasibleAlone()) {
                continue;
            }
            Insertion option = insertion.best(s, c, analysis[c].feasibleDays(), Limits.STRICT);
            if (option == null) {
                continue;
            }
            double timeShare = Math.max(1, option.addedDuration()) / (double) Math.max(1, remainingTime);
            double costShare = 0.0;
            if (budgetLimited) {
                long addedCost = option.evaluation().costYen()
                        - s.evaluation(option.day()).costYen();
                costShare = Math.max(0, addedCost) / (double) Math.max(1, remainingBudget);
            }
            double score = p.score[c];
            double priority = score * score / (timeShare + costShare);
            if (noise != null) {
                priority *= 1.0 + NOISE * (2.0 * noise.nextDouble() - 1.0);
            }
            int byPriority = Double.compare(priority, bestPriority);
            if (byPriority > 0 || (byPriority == 0 && best != null && option.addedTravel() < best.addedTravel())) {
                best = option;
                bestPriority = priority;
            }
        }
        if (best == null) {
            return false;
        }
        s.replace(best.day(), best.route(), best.evaluation());
        return true;
    }

    /**
     * Replaces a scheduled optional place {@code v} by an unscheduled one {@code u} with
     * {@code score(u) >= score(v)}, if the objective improves: higher score, or equal score with less
     * travel or cost. Covers same-day replacement and dropping {@code v} on another day to free budget.
     */
    boolean replace(WorkingSolution s) {
        Objective current = s.objective();
        for (int u = 0; u < p.candidateCount; u++) {
            if (p.mandatory[u] || s.isScheduled(u) || !analysis[u].isFeasibleAlone()) {
                continue;
            }
            if (replaceOnSameDay(s, u, current) || replaceOnOtherDay(s, u, current)) {
                return true;
            }
        }
        return false;
    }

    private boolean replaceOnSameDay(WorkingSolution s, int u, Objective current) {
        for (int day = 0; day < p.dayCount; day++) {
            if (!analysis[u].feasibleDays()[day]) {
                continue;
            }
            int[] route = s.route(day);
            for (int i = 0; i < route.length; i++) {
                int v = route[i];
                if (p.mandatory[v] || p.score[v] > p.score[u]) {
                    continue;
                }
                Insertion option = insertion.bestOnDay(s, u, day, Routes.removeAt(route, i), Limits.STRICT);
                if (option != null && s.objectiveWith(day, option.evaluation()).isBetterThan(current)) {
                    s.replace(day, option.route(), option.evaluation());
                    return true;
                }
            }
        }
        return false;
    }

    private boolean replaceOnOtherDay(WorkingSolution s, int u, Objective current) {
        Insertion withU = insertion.best(s, u, analysis[u].feasibleDays(), Limits.IGNORE_BUDGET);
        if (withU == null) {
            return false;
        }
        for (int day = 0; day < p.dayCount; day++) {
            if (day == withU.day()) {
                continue;
            }
            int[] route = s.route(day);
            for (int i = 0; i < route.length; i++) {
                int v = route[i];
                if (p.mandatory[v] || p.score[v] > p.score[u]) {
                    continue;
                }
                int[] reduced = Routes.removeAt(route, i);
                RouteEvaluation reducedEvaluation = evaluator.evaluate(day, reduced);
                if (!reducedEvaluation.feasible()) {
                    continue;
                }
                Objective after = s.objectiveWith(withU.day(), withU.evaluation(), day, reducedEvaluation);
                if (s.withinBudget(after) && after.isBetterThan(current)) {
                    s.replace(withU.day(), withU.route(), withU.evaluation());
                    s.replace(day, reduced, reducedEvaluation);
                    return true;
                }
            }
        }
        return false;
    }

    boolean twoOpt(WorkingSolution s) {
        Objective current = s.objective();
        for (int day = 0; day < p.dayCount; day++) {
            int[] route = s.route(day);
            for (int i = 0; i < route.length - 1; i++) {
                for (int j = i + 1; j < route.length; j++) {
                    if (tryReplace(s, day, Routes.reverse(route, i, j), current)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Moves a chain of 1–3 consecutive visits to another position of the same day. */
    boolean orOpt(WorkingSolution s) {
        Objective current = s.objective();
        for (int day = 0; day < p.dayCount; day++) {
            int[] route = s.route(day);
            for (int length = 1; length <= Math.min(MAX_OR_OPT_CHAIN, route.length - 1); length++) {
                for (int from = 0; from + length <= route.length; from++) {
                    for (int to = 0; to <= route.length - length; to++) {
                        if (to != from && tryReplace(s, day, moveChain(route, from, length, to), current)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static int[] moveChain(int[] route, int from, int length, int to) {
        int[] rest = new int[route.length - length];
        System.arraycopy(route, 0, rest, 0, from);
        System.arraycopy(route, from + length, rest, from, route.length - from - length);
        int[] result = new int[route.length];
        System.arraycopy(rest, 0, result, 0, to);
        System.arraycopy(route, from, result, to, length);
        System.arraycopy(rest, to, result, to + length, rest.length - to);
        return result;
    }

    private boolean tryReplace(WorkingSolution s, int day, int[] route, Objective current) {
        RouteEvaluation evaluation = evaluator.evaluate(day, route);
        if (!evaluation.feasible()) {
            return false;
        }
        Objective after = s.objectiveWith(day, evaluation);
        if (s.withinBudget(after) && after.isBetterThan(current)) {
            s.replace(day, route, evaluation);
            return true;
        }
        return false;
    }

    /** Moves one visit to another day, at its best position there. */
    boolean relocate(WorkingSolution s) {
        Objective current = s.objective();
        for (int from = 0; from < p.dayCount; from++) {
            int[] source = s.route(from);
            for (int i = 0; i < source.length; i++) {
                int candidate = source[i];
                int[] reduced = Routes.removeAt(source, i);
                RouteEvaluation reducedEvaluation = null;
                for (int to = 0; to < p.dayCount; to++) {
                    if (to == from
                            || !analysis[candidate].feasibleDays()[to]
                            || s.route(to).length >= p.maxVisitsPerDay) {
                        continue;
                    }
                    if (reducedEvaluation == null) {
                        reducedEvaluation = evaluator.evaluate(from, reduced);
                        if (!reducedEvaluation.feasible()) {
                            break;
                        }
                    }
                    int[] target = s.route(to);
                    for (int position = 0; position <= target.length; position++) {
                        int[] extended = Routes.insertAt(target, position, candidate);
                        RouteEvaluation extendedEvaluation = evaluator.evaluate(to, extended);
                        if (!extendedEvaluation.feasible()) {
                            continue;
                        }
                        Objective after = s.objectiveWith(from, reducedEvaluation, to, extendedEvaluation);
                        if (s.withinBudget(after) && after.isBetterThan(current)) {
                            s.replace(from, reduced, reducedEvaluation);
                            s.replace(to, extended, extendedEvaluation);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Exchanges two visits between two days, each placed at its best position in the other day. */
    boolean swap(WorkingSolution s) {
        Objective current = s.objective();
        for (int a = 0; a < p.dayCount; a++) {
            for (int b = a + 1; b < p.dayCount; b++) {
                int[] routeA = s.route(a);
                int[] routeB = s.route(b);
                for (int i = 0; i < routeA.length; i++) {
                    for (int j = 0; j < routeB.length; j++) {
                        int x = routeA[i];
                        int y = routeB[j];
                        if (!analysis[y].feasibleDays()[a] || !analysis[x].feasibleDays()[b]) {
                            continue;
                        }
                        Insertion yIntoA =
                                insertion.bestOnDay(s, y, a, Routes.removeAt(routeA, i), Limits.IGNORE_BUDGET);
                        if (yIntoA == null) {
                            continue;
                        }
                        Insertion xIntoB =
                                insertion.bestOnDay(s, x, b, Routes.removeAt(routeB, j), Limits.IGNORE_BUDGET);
                        if (xIntoB == null) {
                            continue;
                        }
                        Objective after = s.objectiveWith(a, yIntoA.evaluation(), b, xIntoB.evaluation());
                        if (s.withinBudget(after) && after.isBetterThan(current)) {
                            s.replace(a, yIntoA.route(), yIntoA.evaluation());
                            s.replace(b, xIntoB.route(), xIntoB.evaluation());
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Re-orders every day with at most {@link #EXACT_REORDER_MAX_VISITS} visits optimally (exhaustive
     * permutation search; an exact TSP with time windows for that day). Returns the number of improved days.
     */
    int reorderExactly(WorkingSolution s) {
        int improvedDays = 0;
        for (int day = 0; day < p.dayCount; day++) {
            int[] route = s.route(day);
            if (route.length < 3 || route.length > EXACT_REORDER_MAX_VISITS) {
                continue;
            }
            BestOrder best = new BestOrder(s.evaluation(day), null);
            permute(day, route.clone(), 0, best);
            if (best.route != null) {
                Objective after = s.objectiveWith(day, best.evaluation);
                if (s.withinBudget(after) && after.isBetterThan(s.objective())) {
                    s.replace(day, best.route, best.evaluation);
                    improvedDays++;
                }
            }
        }
        return improvedDays;
    }

    private void permute(int day, int[] route, int k, BestOrder best) {
        if (k == route.length) {
            RouteEvaluation evaluation = evaluator.evaluate(day, route);
            if (evaluation.feasible()
                    && (evaluation.travelMinutes() < best.evaluation.travelMinutes()
                            || (evaluation.travelMinutes() == best.evaluation.travelMinutes()
                                    && evaluation.costYen() < best.evaluation.costYen()))) {
                best.evaluation = evaluation;
                best.route = route.clone();
            }
            return;
        }
        for (int i = k; i < route.length; i++) {
            swapElements(route, k, i);
            permute(day, route, k + 1, best);
            swapElements(route, k, i);
        }
    }

    private static void swapElements(int[] route, int i, int j) {
        int tmp = route[i];
        route[i] = route[j];
        route[j] = tmp;
    }

    private static final class BestOrder {
        RouteEvaluation evaluation;
        int[] route;

        BestOrder(RouteEvaluation evaluation, int[] route) {
            this.evaluation = evaluation;
            this.route = route;
        }
    }
}
