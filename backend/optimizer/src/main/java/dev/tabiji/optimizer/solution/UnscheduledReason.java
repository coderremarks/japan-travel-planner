package dev.tabiji.optimizer.solution;

/**
 * Why a candidate is not in the plan. The first five reasons are properties of the candidate alone
 * (it could not be scheduled even in an otherwise empty trip); the rest depend on the other candidates.
 */
public enum UnscheduledReason {
    /** Closed on every day of the trip. */
    CLOSED_ON_TRIP_DATES(Hint.CHANGE_DATES),
    /** Open, but never long enough for the visit inside the traveller's daily hours. */
    OUTSIDE_DAILY_HOURS(Hint.EXTEND_DAILY_HOURS),
    /** No allowed travel mode connects it with the day's start or end location (e.g. no transit data). */
    NO_ROUTE(Hint.ALLOW_MORE_TRAVEL_MODES),
    /** Reachable, but going there and back does not fit into any day. */
    TOO_FAR_FOR_DAY(Hint.EXTEND_DAILY_HOURS),
    /** Its admission plus the round trip alone exceed the budget. */
    OVER_BUDGET(Hint.INCREASE_BUDGET),
    /** Must-visit that fits alone but not together with the other must-visits. */
    CONFLICTS_WITH_MUST_VISITS(Hint.ADD_DAYS),
    /** Every day it could go into already has the maximum number of visits. */
    DAILY_LIMIT_REACHED(Hint.RAISE_DAILY_LIMIT),
    /** Would fit in time, but the budget is used up by higher-priority places. */
    BUDGET_EXHAUSTED(Hint.INCREASE_BUDGET),
    /** No room left after higher-priority places were scheduled. */
    NOT_ENOUGH_TIME(Hint.ADD_DAYS),
    /** Only in "keep my order" plans: it would fit if the order were changed. */
    DOES_NOT_FIT_GIVEN_ORDER(Hint.USE_OPTIMIZER);

    private final Hint hint;

    UnscheduledReason(Hint hint) {
        this.hint = hint;
    }

    public Hint hint() {
        return hint;
    }

    /** {@code true} if the reason holds no matter which other places are in the trip. */
    public boolean isIntrinsic() {
        return ordinal() <= OVER_BUDGET.ordinal();
    }
}
