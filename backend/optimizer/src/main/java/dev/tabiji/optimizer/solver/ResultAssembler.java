package dev.tabiji.optimizer.solver;

import dev.tabiji.optimizer.solution.DaySchedule;
import dev.tabiji.optimizer.solution.PlanStatus;
import dev.tabiji.optimizer.solution.PlanTotals;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solution.SolverStats;
import dev.tabiji.optimizer.solution.UnscheduledCandidate;
import dev.tabiji.optimizer.solution.UnscheduledReason;
import dev.tabiji.optimizer.solver.FeasibilityAnalyzer.CandidateAnalysis;
import dev.tabiji.optimizer.solver.InsertionSearch.Limits;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link WorkingSolution} into the public {@link PlanningResult} and explains every candidate
 * that is not in the plan. For candidates that are feasible alone, the reason is found by asking
 * counterfactual questions: would it fit in this order? without the daily limit? without the budget?
 */
final class ResultAssembler {

    private ResultAssembler() {}

    static PlanningResult assemble(
            ProblemIndex p,
            RouteEvaluator evaluator,
            InsertionSearch insertion,
            CandidateAnalysis[] analysis,
            WorkingSolution solution,
            SolverStats stats) {
        List<DaySchedule> days = new ArrayList<>(p.dayCount);
        long transport = 0;
        long admission = 0;
        int travel = 0;
        for (int day = 0; day < p.dayCount; day++) {
            DaySchedule schedule = evaluator.describe(day, solution.route(day));
            days.add(schedule);
            transport += schedule.transportCostYen();
            admission += schedule.admissionYen();
            travel += schedule.travelMinutes();
        }

        List<UnscheduledCandidate> unscheduled = new ArrayList<>();
        int mandatoryScheduled = 0;
        long score = 0;
        for (int c = 0; c < p.candidateCount; c++) {
            if (solution.isScheduled(c)) {
                score += p.score[c];
                if (p.mandatory[c]) {
                    mandatoryScheduled++;
                }
            } else {
                unscheduled.add(new UnscheduledCandidate(
                        p.id(c), p.mandatory[c], reasonFor(p, insertion, analysis, solution, c)));
            }
        }

        PlanStatus status;
        if (mandatoryScheduled < p.mandatoryTotal) {
            status = PlanStatus.INFEASIBLE;
        } else if (unscheduled.isEmpty()) {
            status = PlanStatus.COMPLETE;
        } else {
            status = PlanStatus.PARTIAL;
        }
        PlanTotals totals = new PlanTotals(
                solution.scheduledCount(), mandatoryScheduled, p.mandatoryTotal, score, travel, transport, admission);
        return new PlanningResult(status, days, unscheduled, totals, stats);
    }

    private static UnscheduledReason reasonFor(
            ProblemIndex p, InsertionSearch insertion, CandidateAnalysis[] analysis, WorkingSolution solution, int c) {
        CandidateAnalysis candidate = analysis[c];
        if (!candidate.isFeasibleAlone()) {
            return candidate.reason();
        }
        boolean[] days = candidate.feasibleDays();
        if (insertion.best(solution, c, days, Limits.STRICT) != null) {
            return UnscheduledReason.DOES_NOT_FIT_GIVEN_ORDER;
        }
        if (insertion.best(solution, c, days, Limits.IGNORE_DAILY_LIMIT) != null) {
            return UnscheduledReason.DAILY_LIMIT_REACHED;
        }
        if (insertion.best(solution, c, days, Limits.IGNORE_BUDGET) != null) {
            return UnscheduledReason.BUDGET_EXHAUSTED;
        }
        return p.mandatory[c] ? UnscheduledReason.CONFLICTS_WITH_MUST_VISITS : UnscheduledReason.NOT_ENOUGH_TIME;
    }
}
