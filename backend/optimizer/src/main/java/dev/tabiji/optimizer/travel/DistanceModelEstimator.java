package dev.tabiji.optimizer.travel;

import dev.tabiji.optimizer.geo.GeoPoint;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Walking and taxi estimates from straight-line distance; see {@link DistanceModel} for the assumptions. */
public final class DistanceModelEstimator implements TravelEstimator {

    private static final Set<TravelMode> MODES = EnumSet.of(TravelMode.WALK, TravelMode.TAXI);

    private final DistanceModel model;

    public DistanceModelEstimator(DistanceModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public Set<TravelMode> supportedModes() {
        return EnumSet.copyOf(MODES);
    }

    @Override
    public Optional<Leg> estimate(GeoPoint from, GeoPoint to, TravelMode mode) {
        double straightMeters = from.distanceMetersTo(to);
        return switch (mode) {
            case WALK -> walk(straightMeters);
            case TAXI -> taxi(straightMeters);
            case TRANSIT -> Optional.empty();
        };
    }

    private Optional<Leg> walk(double straightMeters) {
        if (straightMeters > model.maxWalkMeters()) {
            return Optional.empty();
        }
        double meters = straightMeters * model.walkDetourFactor();
        int minutes = ceilMinutes(meters / model.walkMetersPerMinute());
        return Optional.of(
                Leg.estimate(TravelMode.WALK, minutes, 0, (int) Math.round(meters), LegSource.DISTANCE_MODEL));
    }

    private Optional<Leg> taxi(double straightMeters) {
        if (straightMeters > model.maxTaxiMeters()) {
            return Optional.empty();
        }
        double meters = straightMeters * model.taxiDetourFactor();
        double cityMeters = Math.min(meters, model.taxiCityMeters());
        double highwayMeters = meters - cityMeters;
        double drivingMinutes = cityMeters / metersPerMinute(model.taxiCityKmh())
                + highwayMeters / metersPerMinute(model.taxiHighwayKmh());
        int minutes = model.taxiPickupMinutes() + ceilMinutes(drivingMinutes);

        double meteredKm = Math.max(0.0, meters - model.taxiFlagFallMeters()) / 1000.0;
        int fare = roundUpToTenYen(model.taxiFlagFallYen() + meteredKm * model.taxiYenPerKm());
        return Optional.of(
                Leg.estimate(TravelMode.TAXI, minutes, fare, (int) Math.round(meters), LegSource.DISTANCE_MODEL));
    }

    private static double metersPerMinute(double kmh) {
        return kmh * 1000.0 / 60.0;
    }

    /** Rounds up, ignoring floating-point noise such as {@code 12.000000001}. */
    static int ceilMinutes(double minutes) {
        return (int) Math.ceil(minutes - 1e-9);
    }

    static int roundUpToTenYen(double yen) {
        return (int) (Math.ceil((yen - 1e-9) / 10.0) * 10);
    }
}
