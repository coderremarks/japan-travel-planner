package dev.tabiji.optimizer.solver;

import dev.tabiji.optimizer.solver.FeasibilityAnalyzer.CandidateAnalysis;

/**
 * The "plan it by hand" baseline: walk through the candidates in the order the traveller listed them
 * and append each one to the current day, moving on to the next day when it no longer fits. Never goes
 * back to an earlier day, never reorders. Used to show how much the optimizer improves on the
 * traveller's own order, and for travellers who want exactly their order. {@code O(n × days × n)}.
 */
final class KeepOrderScheduler {

    private KeepOrderScheduler() {}

    static WorkingSolution schedule(ProblemIndex p, RouteEvaluator evaluator, CandidateAnalysis[] analysis) {
        WorkingSolution solution = WorkingSolution.empty(p, evaluator);
        int currentDay = 0;
        for (int c = 0; c < p.candidateCount; c++) {
            if (!analysis[c].isFeasibleAlone()) {
                continue;
            }
            for (int day = currentDay; day < p.dayCount; day++) {
                if (!analysis[c].feasibleDays()[day]) {
                    continue;
                }
                int[] route = solution.route(day);
                int[] extended = Routes.insertAt(route, route.length, c);
                RouteEvaluation evaluation = evaluator.evaluate(day, extended);
                if (evaluation.feasible() && solution.withinBudget(solution.objectiveWith(day, evaluation))) {
                    solution.replace(day, extended, evaluation);
                    currentDay = day;
                    break;
                }
            }
        }
        return solution;
    }
}
