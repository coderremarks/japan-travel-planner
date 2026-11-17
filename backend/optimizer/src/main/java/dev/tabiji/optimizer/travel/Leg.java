package dev.tabiji.optimizer.travel;

import java.util.List;
import java.util.Objects;

/**
 * One door-to-door travel estimate.
 *
 * @param mode           how the traveller moves; for transit legs this includes walking to and from stations
 * @param minutes        door-to-door duration, rounded up to whole minutes
 * @param costYen        fare estimate in yen, {@code 0} for walking
 * @param distanceMeters travelled distance (not straight-line distance)
 * @param source         provenance of the estimate
 * @param estimated      {@code false} only when the source provides authoritative data
 * @param via            transit line identifiers in boarding order; empty for non-transit legs
 */
public record Leg(
        TravelMode mode,
        int minutes,
        int costYen,
        int distanceMeters,
        LegSource source,
        boolean estimated,
        List<String> via) {

    private static final Leg STAY = new Leg(TravelMode.WALK, 0, 0, 0, LegSource.SAME_LOCATION, false, List.of());

    public Leg {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(source, "source");
        via = List.copyOf(via);
        if (minutes < 0 || costYen < 0 || distanceMeters < 0) {
            throw new IllegalArgumentException("minutes, costYen and distanceMeters must be >= 0");
        }
    }

    /** The zero leg used when origin and destination coincide. */
    public static Leg stay() {
        return STAY;
    }

    public static Leg estimate(TravelMode mode, int minutes, int costYen, int distanceMeters, LegSource source) {
        return new Leg(mode, minutes, costYen, distanceMeters, source, true, List.of());
    }
}
