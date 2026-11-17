package dev.tabiji.optimizer.travel.transit;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Every number the sample transit model relies on, in one documented place. None of these values is a
 * timetable or an official fare; they are round figures chosen so that estimates land in a plausible
 * range, and every resulting leg is labelled as an estimate.
 *
 * @param profiles              per-line-type speed, wait and fare assumptions
 * @param railDetourFactor      track length ÷ straight-line distance between consecutive listed stations
 * @param walkMetersPerMinute   walking speed for station access, egress and transfers
 * @param walkDetourFactor      street detour factor for walking
 * @param maxAccessWalkMeters   stations farther than this (straight line) are not considered walkable
 * @param maxAccessStations     how many nearby stations to consider at each end of a trip
 * @param transferWalkMeters    stations closer than this are linked by a walking transfer
 * @param alightingMinutes      time from platform to street / connecting concourse
 */
public record TransitAssumptions(
        Map<LineType, LineProfile> profiles,
        double railDetourFactor,
        double walkMetersPerMinute,
        double walkDetourFactor,
        int maxAccessWalkMeters,
        int maxAccessStations,
        int transferWalkMeters,
        double alightingMinutes) {

    public static final TransitAssumptions DEFAULT = new TransitAssumptions(
            Map.of(
                    LineType.SUBWAY, new LineProfile(30.0, 3.0, 180, 12.0),
                    LineType.URBAN_RAIL, new LineProfile(33.0, 3.0, 150, 12.0),
                    LineType.REGIONAL_RAIL, new LineProfile(55.0, 5.0, 170, 15.0),
                    LineType.SHINKANSEN, new LineProfile(210.0, 8.0, 3_000, 26.0)),
            1.15,
            75.0,
            1.25,
            1_200,
            3,
            500,
            1.0);

    public TransitAssumptions {
        Objects.requireNonNull(profiles, "profiles");
        for (LineType type : LineType.values()) {
            if (!profiles.containsKey(type)) {
                throw new IllegalArgumentException("missing profile for " + type);
            }
        }
        profiles = Map.copyOf(new EnumMap<>(profiles));
        if (!(railDetourFactor >= 1.0) || !(walkDetourFactor >= 1.0)) {
            throw new IllegalArgumentException("detour factors must be >= 1");
        }
        if (!(walkMetersPerMinute > 0.0) || !(alightingMinutes >= 0.0)) {
            throw new IllegalArgumentException("walking speed must be > 0 and alighting time >= 0");
        }
        if (maxAccessWalkMeters < 0 || maxAccessStations < 1 || transferWalkMeters < 0) {
            throw new IllegalArgumentException("access and transfer limits are invalid");
        }
    }

    public LineProfile profile(LineType type) {
        return profiles.get(type);
    }

    double walkMinutes(double straightMeters) {
        return straightMeters * walkDetourFactor / walkMetersPerMinute;
    }
}
