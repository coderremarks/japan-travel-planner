package dev.tabiji.optimizer.travel;

import dev.tabiji.optimizer.geo.GeoPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the single best-leg {@link TravelMatrix} the solver works on.
 *
 * <p>For each allowed mode the responsible {@link TravelEstimator} fills a per-mode matrix (one
 * provider call per mode, which lets network-based providers batch their work). Short walks are always
 * considered (see {@link ModeChoicePolicy#alwaysAllowedWalkMinutes()}). Per pair, the leg with the best
 * generalised cost wins. Choosing the mode per pair up front keeps the solver's inner loop to an array
 * lookup; the trade-off is that mode choice does not react to the remaining budget (documented in
 * {@code docs/algorithm.md}).
 */
public final class TravelMatrixBuilder {

    private final Map<TravelMode, TravelEstimator> estimators;
    private final ModeChoicePolicy policy;

    public TravelMatrixBuilder(List<TravelEstimator> availableEstimators, ModeChoicePolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        Map<TravelMode, TravelEstimator> byMode = new EnumMap<>(TravelMode.class);
        for (TravelEstimator estimator : availableEstimators) {
            for (TravelMode mode : estimator.supportedModes()) {
                if (byMode.putIfAbsent(mode, estimator) != null) {
                    throw new IllegalArgumentException("more than one estimator supports " + mode);
                }
            }
        }
        this.estimators = byMode;
    }

    public Set<TravelMode> supportedModes() {
        return Set.copyOf(estimators.keySet());
    }

    public TravelMatrix build(List<GeoPoint> points, Set<TravelMode> allowedModes) {
        if (allowedModes.isEmpty()) {
            throw new IllegalArgumentException("at least one travel mode must be allowed");
        }
        List<ModeMatrix> perMode = new ArrayList<>();
        for (TravelMode mode : TravelMode.values()) {
            boolean allowed = allowedModes.contains(mode);
            boolean shortWalksOnly = mode == TravelMode.WALK && !allowed;
            if ((allowed || shortWalksOnly) && estimators.containsKey(mode)) {
                perMode.add(new ModeMatrix(estimators.get(mode).estimateAll(points, mode), shortWalksOnly));
            }
        }

        Comparator<Leg> preference = policy.preference();
        TravelMatrix.Builder result = TravelMatrix.builder(points.size());
        for (int i = 0; i < points.size(); i++) {
            for (int j = 0; j < points.size(); j++) {
                if (i == j) {
                    continue;
                }
                Leg best = null;
                for (ModeMatrix candidate : perMode) {
                    Leg leg = candidate.matrix().leg(i, j).orElse(null);
                    if (leg == null
                            || (candidate.shortWalksOnly() && leg.minutes() > policy.alwaysAllowedWalkMinutes())) {
                        continue;
                    }
                    if (best == null || preference.compare(leg, best) < 0) {
                        best = leg;
                    }
                }
                if (best != null) {
                    result.set(i, j, best);
                }
            }
        }
        return result.build();
    }

    private record ModeMatrix(TravelMatrix matrix, boolean shortWalksOnly) {}
}
