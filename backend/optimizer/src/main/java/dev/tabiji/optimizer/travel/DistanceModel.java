package dev.tabiji.optimizer.travel;

/**
 * Assumptions of the straight-line distance model used for walking and taxi estimates.
 *
 * <p>These are deliberately simple, documented parameters — not measured data. The resulting legs are
 * always flagged {@code estimated = true}. Defaults:
 * <ul>
 *   <li>Walking 75 m/min (4.5 km/h), street detour factor 1.25 over the straight line, at most 7 km.</li>
 *   <li>Taxi detour 1.35; 20 km/h for the first 10 km (city traffic), 45 km/h beyond (expressways);
 *       5 min pick-up; fare ≈ ¥500 for the first km then ¥400/km, rounded up to ¥10. This is an
 *       order-of-magnitude approximation of metered fares in large Japanese cities; real fares vary by
 *       region, time of day (late-night surcharge) and traffic.</li>
 * </ul>
 */
public record DistanceModel(
        double walkMetersPerMinute,
        double walkDetourFactor,
        int maxWalkMeters,
        double taxiDetourFactor,
        double taxiCityKmh,
        double taxiHighwayKmh,
        int taxiCityMeters,
        int taxiPickupMinutes,
        int taxiFlagFallYen,
        int taxiFlagFallMeters,
        int taxiYenPerKm,
        int maxTaxiMeters) {

    public static final DistanceModel DEFAULT =
            new DistanceModel(75.0, 1.25, 7_000, 1.35, 20.0, 45.0, 10_000, 5, 500, 1_000, 400, 80_000);

    public DistanceModel {
        requirePositive(walkMetersPerMinute, "walkMetersPerMinute");
        requireAtLeastOne(walkDetourFactor, "walkDetourFactor");
        requireAtLeastOne(taxiDetourFactor, "taxiDetourFactor");
        requirePositive(taxiCityKmh, "taxiCityKmh");
        requirePositive(taxiHighwayKmh, "taxiHighwayKmh");
        if (maxWalkMeters < 0
                || maxTaxiMeters < 0
                || taxiCityMeters < 0
                || taxiPickupMinutes < 0
                || taxiFlagFallYen < 0
                || taxiFlagFallMeters < 0
                || taxiYenPerKm < 0) {
            throw new IllegalArgumentException("distance model parameters must be >= 0");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void requireAtLeastOne(double value, String name) {
        if (!(value >= 1.0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be >= 1 and finite");
        }
    }
}
