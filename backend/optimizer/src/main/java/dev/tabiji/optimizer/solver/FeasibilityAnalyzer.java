package dev.tabiji.optimizer.solver;

import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.solution.UnscheduledReason;

/**
 * Checks each candidate on its own, before any search: on which days could it be visited if it were the
 * only place in the trip? Candidates that fail everywhere get a precise, intrinsic
 * {@link UnscheduledReason} and are excluded from the search; the others carry the set of days worth
 * trying, which prunes the search. {@code O(candidates × days × windows)}.
 */
final class FeasibilityAnalyzer {

    private FeasibilityAnalyzer() {}

    /**
     * @param reason       intrinsic reason, or {@code null} if the candidate is feasible on some day
     * @param feasibleDays {@code feasibleDays[d]} is true if the candidate can be visited alone on day d
     */
    record CandidateAnalysis(UnscheduledReason reason, boolean[] feasibleDays) {

        boolean isFeasibleAlone() {
            return reason == null;
        }

        int feasibleDayCount() {
            int count = 0;
            for (boolean feasible : feasibleDays) {
                if (feasible) {
                    count++;
                }
            }
            return count;
        }
    }

    static CandidateAnalysis[] analyze(ProblemIndex p, RouteEvaluator evaluator) {
        long emptyTripCost = 0;
        RouteEvaluation[] emptyDay = new RouteEvaluation[p.dayCount];
        for (int d = 0; d < p.dayCount; d++) {
            emptyDay[d] = evaluator.evaluate(d, Routes.EMPTY);
            emptyTripCost += emptyDay[d].costYen();
        }

        CandidateAnalysis[] result = new CandidateAnalysis[p.candidateCount];
        for (int c = 0; c < p.candidateCount; c++) {
            boolean open = false;
            boolean longEnough = false;
            boolean reachable = false;
            boolean fitsAlone = false;
            boolean affordable = false;
            boolean[] feasibleDays = new boolean[p.dayCount];
            for (int d = 0; d < p.dayCount; d++) {
                if (p.windows[c][d].length == 0) {
                    continue;
                }
                open = true;
                TimeWindow dayWindow = TimeWindow.of(p.dayStart[d], p.dayEnd[d]);
                if (longestOverlap(p.windows[c][d], dayWindow) < p.visitMinutes[c]) {
                    continue;
                }
                longEnough = true;
                if (!p.travel.isReachable(p.dayStartLocation[d], p.location[c])
                        || !p.travel.isReachable(p.location[c], p.dayEndLocation[d])) {
                    continue;
                }
                reachable = true;
                RouteEvaluation alone = evaluator.evaluate(d, new int[] {c});
                if (!alone.feasible()) {
                    continue;
                }
                fitsAlone = true;
                long tripCostWithCandidate = emptyTripCost - emptyDay[d].costYen() + alone.costYen();
                if (tripCostWithCandidate > p.budgetYen) {
                    continue;
                }
                affordable = true;
                feasibleDays[d] = true;
            }
            UnscheduledReason reason;
            if (!open) {
                reason = UnscheduledReason.CLOSED_ON_TRIP_DATES;
            } else if (!longEnough) {
                reason = UnscheduledReason.OUTSIDE_DAILY_HOURS;
            } else if (!reachable) {
                reason = UnscheduledReason.NO_ROUTE;
            } else if (!fitsAlone) {
                reason = UnscheduledReason.TOO_FAR_FOR_DAY;
            } else if (!affordable) {
                reason = UnscheduledReason.OVER_BUDGET;
            } else {
                reason = null;
            }
            result[c] = new CandidateAnalysis(reason, feasibleDays);
        }
        return result;
    }

    private static int longestOverlap(TimeWindow[] windows, TimeWindow dayWindow) {
        int longest = 0;
        for (TimeWindow window : windows) {
            int overlap = window.intersection(dayWindow)
                    .map(TimeWindow::lengthMinutes)
                    .orElse(0);
            longest = Math.max(longest, overlap);
        }
        return longest;
    }
}
