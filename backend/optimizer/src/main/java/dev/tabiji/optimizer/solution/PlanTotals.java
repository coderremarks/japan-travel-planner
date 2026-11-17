package dev.tabiji.optimizer.solution;

/** Aggregates over the whole plan. */
public record PlanTotals(
        int scheduledCount,
        int mandatoryScheduled,
        int mandatoryTotal,
        long score,
        int travelMinutes,
        long transportCostYen,
        long admissionYen) {

    public long totalCostYen() {
        return transportCostYen + admissionYen;
    }
}
