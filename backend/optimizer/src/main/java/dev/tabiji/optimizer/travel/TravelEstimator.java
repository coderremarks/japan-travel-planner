package dev.tabiji.optimizer.travel;

import dev.tabiji.optimizer.geo.GeoPoint;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port through which the planner obtains travel estimates. Implementations wrap a distance model, the
 * bundled sample rail network, or — in a production deployment — an external routing provider.
 * Implementations must be thread-safe and must never return a leg for a mode they do not support.
 */
public interface TravelEstimator {

    Set<TravelMode> supportedModes();

    /** Estimate for a single pair; empty when this mode cannot connect the two points. */
    Optional<Leg> estimate(GeoPoint from, GeoPoint to, TravelMode mode);

    /**
     * Estimates for all ordered pairs of {@code points}. The default loops over pairs; providers with a
     * cheaper one-to-many query (for example one Dijkstra run per origin) override this.
     */
    default TravelMatrix estimateAll(List<GeoPoint> points, TravelMode mode) {
        TravelMatrix.Builder builder = TravelMatrix.builder(points.size());
        for (int i = 0; i < points.size(); i++) {
            for (int j = 0; j < points.size(); j++) {
                if (i != j) {
                    int from = i;
                    int to = j;
                    estimate(points.get(i), points.get(j), mode).ifPresent(leg -> builder.set(from, to, leg));
                }
            }
        }
        return builder.build();
    }
}
