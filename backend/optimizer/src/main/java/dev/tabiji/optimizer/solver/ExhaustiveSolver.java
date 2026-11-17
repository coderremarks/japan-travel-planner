package dev.tabiji.optimizer.solver;

import dev.tabiji.optimizer.solver.FeasibilityAnalyzer.CandidateAnalysis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact reference solver for <em>small</em> problems, used to measure the heuristic's optimality gap.
 *
 * <ol>
 *   <li>For every day and every subset of candidates feasible on that day, enumerate all orderings
 *       (at most {@code maxVisitsPerDay} long) and keep the Pareto front of (travel minutes, cost) over
 *       the feasible ones. Cost must be kept as a second dimension because the budget couples days.</li>
 *   <li>Enumerate every assignment of candidates to a day or to "unscheduled"; for each, combine one
 *       front entry per day, minimising travel subject to the budget.</li>
 * </ol>
 *
 * No pruning relies on the triangle inequality (estimated matrices need not satisfy it), so the result
 * is exact for any matrix. Complexity is exponential — {@code O(d · n! + (d+1)^n)} — hence the hard
 * limits {@link #MAX_CANDIDATES} and {@link #MAX_DAYS}.
 */
final class ExhaustiveSolver {

    static final int MAX_CANDIDATES = 8;
    static final int MAX_DAYS = 3;

    private record Option(int[] route, RouteEvaluation evaluation) {}

    private final ProblemIndex p;
    private final RouteEvaluator evaluator;
    private final CandidateAnalysis[] analysis;
    private final List<Map<Integer, List<Option>>> frontsByDay = new ArrayList<>();

    private Objective bestObjective;
    private Option[] bestChoice;

    private ExhaustiveSolver(ProblemIndex p, RouteEvaluator evaluator, CandidateAnalysis[] analysis) {
        this.p = p;
        this.evaluator = evaluator;
        this.analysis = analysis;
    }

    static WorkingSolution solve(ProblemIndex p, RouteEvaluator evaluator, CandidateAnalysis[] analysis) {
        if (p.candidateCount > MAX_CANDIDATES || p.dayCount > MAX_DAYS) {
            throw new IllegalArgumentException(
                    "exhaustive search supports at most " + MAX_CANDIDATES + " candidates and " + MAX_DAYS + " days");
        }
        ExhaustiveSolver solver = new ExhaustiveSolver(p, evaluator, analysis);
        for (int day = 0; day < p.dayCount; day++) {
            Map<Integer, List<Option>> fronts = new HashMap<>();
            solver.enumerateRoutes(day, Routes.EMPTY, 0, fronts);
            solver.frontsByDay.add(fronts);
        }
        solver.assign(0, new int[p.dayCount]);

        WorkingSolution solution = WorkingSolution.empty(p, evaluator);
        if (solver.bestChoice != null) {
            for (int day = 0; day < p.dayCount; day++) {
                solution.replace(day, solver.bestChoice[day].route(), solver.bestChoice[day].evaluation());
            }
        }
        return solution;
    }

    private void enumerateRoutes(int day, int[] prefix, int mask, Map<Integer, List<Option>> fronts) {
        RouteEvaluation evaluation = evaluator.evaluate(day, prefix);
        if (evaluation.feasible()) {
            addToFront(fronts.computeIfAbsent(mask, key -> new ArrayList<>()), new Option(prefix, evaluation));
        }
        if (prefix.length == p.maxVisitsPerDay) {
            return;
        }
        for (int c = 0; c < p.candidateCount; c++) {
            if ((mask & (1 << c)) == 0 && analysis[c].feasibleDays()[day]) {
                enumerateRoutes(day, Routes.insertAt(prefix, prefix.length, c), mask | (1 << c), fronts);
            }
        }
    }

    private static void addToFront(List<Option> front, Option option) {
        int travel = option.evaluation().travelMinutes();
        long cost = option.evaluation().costYen();
        for (Option existing : front) {
            if (existing.evaluation().travelMinutes() <= travel
                    && existing.evaluation().costYen() <= cost) {
                return;
            }
        }
        front.removeIf(existing -> travel <= existing.evaluation().travelMinutes()
                && cost <= existing.evaluation().costYen());
        front.add(option);
    }

    private void assign(int candidate, int[] masks) {
        if (candidate == p.candidateCount) {
            evaluateAssignment(masks);
            return;
        }
        assign(candidate + 1, masks);
        if (!analysis[candidate].isFeasibleAlone()) {
            return;
        }
        for (int day = 0; day < p.dayCount; day++) {
            if (!analysis[candidate].feasibleDays()[day] || Integer.bitCount(masks[day]) >= p.maxVisitsPerDay) {
                continue;
            }
            masks[day] |= 1 << candidate;
            assign(candidate + 1, masks);
            masks[day] &= ~(1 << candidate);
        }
    }

    private void evaluateAssignment(int[] masks) {
        List<List<Option>> fronts = new ArrayList<>(p.dayCount);
        for (int day = 0; day < p.dayCount; day++) {
            List<Option> front = frontsByDay.get(day).get(masks[day]);
            if (front == null) {
                return;
            }
            fronts.add(front);
        }
        combine(fronts, 0, new Option[p.dayCount]);
    }

    private void combine(List<List<Option>> fronts, int day, Option[] chosen) {
        if (day == p.dayCount) {
            int mandatory = 0;
            long score = 0;
            long travel = 0;
            long cost = 0;
            for (Option option : chosen) {
                mandatory += option.evaluation().mandatoryCount();
                score += option.evaluation().score();
                travel += option.evaluation().travelMinutes();
                cost += option.evaluation().costYen();
            }
            Objective objective = new Objective(mandatory, score, travel, cost);
            if (cost <= p.budgetYen && (bestObjective == null || objective.isBetterThan(bestObjective))) {
                bestObjective = objective;
                bestChoice = chosen.clone();
            }
            return;
        }
        for (Option option : fronts.get(day)) {
            chosen[day] = option;
            combine(fronts, day + 1, chosen);
        }
    }
}
