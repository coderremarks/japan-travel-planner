package dev.tabiji.optimizer.solver;

/**
 * Aggregates of one simulated day route. Only feasible evaluations carry meaningful numbers.
 *
 * @param returnArrivalMinute arrival at the day's end location; doubles as the route's "completion time"
 */
record RouteEvaluation(
        boolean feasible,
        int travelMinutes,
        long transportCostYen,
        long admissionYen,
        long score,
        int mandatoryCount,
        int returnArrivalMinute) {

    static final RouteEvaluation INFEASIBLE = new RouteEvaluation(false, 0, 0, 0, 0, 0, 0);

    long costYen() {
        return transportCostYen + admissionYen;
    }
}
