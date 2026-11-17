package dev.tabiji.optimizer.travel;

import java.util.Comparator;

/**
 * Chooses one leg per origin/destination pair among the modes the traveller allows.
 *
 * <p>Legs are compared by <em>generalised cost</em> — a standard transport-economics measure —
 * {@code minutes + costYen / yenPerMinute}, i.e. money is converted into time with a "value of time".
 * With the default ¥30/min (¥1,800/h) a ¥1,500 taxi must save 50 minutes to beat a free walk, which
 * matches how budget-conscious travellers behave. Ties are broken by duration, then by mode order.
 *
 * @param yenPerMinute             value of time used to convert fares into minutes
 * @param alwaysAllowedWalkMinutes walks up to this length are offered even if walking is not an allowed
 *                                 mode (nobody takes a train for 400 m)
 */
public record ModeChoicePolicy(double yenPerMinute, int alwaysAllowedWalkMinutes) {

    public static final ModeChoicePolicy DEFAULT = new ModeChoicePolicy(30.0, 12);

    public ModeChoicePolicy {
        if (!(yenPerMinute > 0.0) || Double.isInfinite(yenPerMinute)) {
            throw new IllegalArgumentException("yenPerMinute must be positive and finite");
        }
        if (alwaysAllowedWalkMinutes < 0) {
            throw new IllegalArgumentException("alwaysAllowedWalkMinutes must be >= 0");
        }
    }

    public double generalisedCost(Leg leg) {
        return leg.minutes() + leg.costYen() / yenPerMinute;
    }

    /** Orders legs from most to least attractive. */
    public Comparator<Leg> preference() {
        return Comparator.comparingDouble(this::generalisedCost)
                .thenComparingInt(Leg::minutes)
                .thenComparing(Leg::mode);
    }
}
