package dev.tabiji.optimizer.solver;

import dev.tabiji.optimizer.solver.FeasibilityAnalyzer.CandidateAnalysis;
import dev.tabiji.optimizer.solver.InsertionSearch.Insertion;
import dev.tabiji.optimizer.solver.InsertionSearch.Limits;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Iterated Local Search (Lourenço, Martin &amp; Stützle) specialised for the TOPTW, following the
 * structure of Vansteenwegen et al. (2009) with a GRASP-style randomised repair:
 *
 * <pre>
 *   s  = best of localSearch(greedyConstruction()), localSearch(travellersOwnOrder())
 *   s* = s
 *   repeat:
 *     s' = localSearch(perturb(s), noisy insertions)   // drop a random segment of optional visits per day,
 *                                                       // sometimes move a random visit to another day
 *     if s' >= s: s = s'                                // accept sideways moves to drift across plateaus
 *     if s' > s*: s* = s'
 *     after R non-improving iterations: s = s*          // restart from the best
 *   until maxIterations or S iterations without a new best
 *   polish(s*)                                          // exact re-ordering of short days + descent
 * </pre>
 *
 * Starting from the traveller's own order as well guarantees that "Optimize" is never worse than
 * "Keep my order". The RNGs are seeded, the search is bounded by iteration counts, and every loop
 * iterates in index order, so identical input yields an identical plan. The wall-clock limit only guards
 * against pathological inputs and is reported when it triggers.
 */
final class IteratedLocalSearch {

    record Outcome(
            WorkingSolution best,
            int constructionScheduled,
            int constructionTravelMinutes,
            int iterations,
            int improvingMoves,
            boolean timeLimitReached) {}

    /** Probability (as 1 in N) that a perturbation also moves one random visit to another day. */
    private static final int RELOCATION_SHAKE_ONE_IN = 3;

    private final ProblemIndex p;
    private final RouteEvaluator evaluator;
    private final InsertionSearch insertion;
    private final CandidateAnalysis[] analysis;
    private final LocalSearch localSearch;
    private final SolverSettings settings;

    IteratedLocalSearch(
            ProblemIndex p,
            RouteEvaluator evaluator,
            InsertionSearch insertion,
            CandidateAnalysis[] analysis,
            SolverSettings settings) {
        this.p = p;
        this.evaluator = evaluator;
        this.insertion = insertion;
        this.analysis = analysis;
        this.localSearch = new LocalSearch(p, evaluator, insertion, analysis);
        this.settings = settings;
    }

    Outcome run(long startedNanos) {
        WorkingSolution greedy = construct();
        int constructionScheduled = greedy.scheduledCount();
        int constructionTravel = Math.toIntExact(greedy.objective().travelMinutes());
        int moves = localSearch.improve(greedy);

        WorkingSolution ownOrder = KeepOrderScheduler.schedule(p, evaluator, analysis);
        moves += localSearch.improve(ownOrder);

        WorkingSolution current = ownOrder.objective().isBetterThan(greedy.objective()) ? ownOrder : greedy;
        WorkingSolution best = current.copy();

        SplittableRandom random = new SplittableRandom(settings.seed());
        long deadline = startedNanos + settings.timeLimit().toNanos();
        int iterations = 0;
        int sinceBest = 0;
        int sinceRestart = 0;
        boolean timeLimitReached = false;
        while (iterations < settings.maxIterations() && sinceBest < settings.stopAfterNonImproving()) {
            if (System.nanoTime() - deadline > 0) {
                timeLimitReached = true;
                break;
            }
            iterations++;
            WorkingSolution candidate = current.copy();
            perturb(candidate, random);
            moves += localSearch.improve(candidate, random);
            Objective objective = candidate.objective();
            if (objective.compareTo(current.objective()) >= 0) {
                current = candidate;
            }
            if (objective.isBetterThan(best.objective())) {
                best = candidate.copy();
                sinceBest = 0;
                sinceRestart = 0;
            } else {
                sinceBest++;
                if (++sinceRestart >= settings.restartAfterNonImproving()) {
                    current = best.copy();
                    sinceRestart = 0;
                }
            }
        }
        moves += localSearch.polish(best);
        return new Outcome(best, constructionScheduled, constructionTravel, iterations, moves, timeLimitReached);
    }

    /** Must-visits first (hardest first, cheapest insertion), then optional places by priority ratio. */
    private WorkingSolution construct() {
        WorkingSolution solution = WorkingSolution.empty(p, evaluator);
        for (int m : localSearch.mandatoryOrder()) {
            Insertion best = insertion.best(solution, m, analysis[m].feasibleDays(), Limits.STRICT);
            if (best != null) {
                solution.replace(best.day(), best.route(), best.evaluation());
            }
        }
        boolean inserted = true;
        while (inserted) {
            inserted = localSearch.insertBestRatio(solution, null);
        }
        return solution;
    }

    /**
     * Removes a random consecutive segment of each day's route (keeping must-visits) and occasionally
     * moves one random visit to another day, so that the following local search rebuilds differently.
     */
    private void perturb(WorkingSolution s, SplittableRandom random) {
        for (int day = 0; day < p.dayCount; day++) {
            int[] route = s.route(day);
            if (route.length == 0) {
                continue;
            }
            int length = 1 + random.nextInt(Math.max(1, (route.length + 1) / 2));
            int start = random.nextInt(route.length);
            int[] reduced = withoutOptionalSegment(route, start, length);
            if (reduced.length == route.length) {
                continue;
            }
            RouteEvaluation evaluation = evaluator.evaluate(day, reduced);
            if (evaluation.feasible() && s.withinBudget(s.objectiveWith(day, evaluation))) {
                s.replace(day, reduced, evaluation);
            }
        }
        if (p.dayCount > 1 && random.nextInt(RELOCATION_SHAKE_ONE_IN) == 0) {
            moveRandomVisit(s, random);
        }
    }

    private int[] withoutOptionalSegment(int[] route, int start, int length) {
        int end = Math.min(route.length, start + length);
        int[] kept = new int[route.length];
        int size = 0;
        for (int i = 0; i < route.length; i++) {
            boolean inSegment = i >= start && i < end;
            if (!inSegment || p.mandatory[route[i]]) {
                kept[size++] = route[i];
            }
        }
        return Arrays.copyOf(kept, size);
    }

    /** Moves one random scheduled visit to a random other feasible day, if that stays feasible. */
    private void moveRandomVisit(WorkingSolution s, SplittableRandom random) {
        int scheduled = s.scheduledCount();
        if (scheduled == 0) {
            return;
        }
        int pick = random.nextInt(scheduled);
        int from = 0;
        while (pick >= s.route(from).length) {
            pick -= s.route(from).length;
            from++;
        }
        int candidate = s.route(from)[pick];
        int to = random.nextInt(p.dayCount);
        if (to == from || !analysis[candidate].feasibleDays()[to]) {
            return;
        }
        int[] reduced = Routes.removeAt(s.route(from), pick);
        RouteEvaluation reducedEvaluation = evaluator.evaluate(from, reduced);
        if (!reducedEvaluation.feasible()) {
            return;
        }
        Insertion moved = insertion.bestOnDay(s, candidate, to, s.route(to), Limits.IGNORE_BUDGET);
        if (moved == null) {
            return;
        }
        Objective after = s.objectiveWith(from, reducedEvaluation, to, moved.evaluation());
        if (s.withinBudget(after)) {
            s.replace(from, reduced, reducedEvaluation);
            s.replace(to, moved.route(), moved.evaluation());
        }
    }
}
