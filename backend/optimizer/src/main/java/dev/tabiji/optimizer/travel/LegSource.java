package dev.tabiji.optimizer.travel;

/** Where a travel estimate came from. Shown to users so that estimates are never mistaken for facts. */
public enum LegSource {
    /** Origin and destination are the same location; nothing to estimate. */
    SAME_LOCATION,
    /** Straight-line distance × detour factor ÷ assumed average speed. */
    DISTANCE_MODEL,
    /** Shortest path on the bundled, simplified rail graph (distance-derived edge times, not timetables). */
    SAMPLE_TRANSIT_NETWORK,
    /** An external routing provider (e.g. a timetable-based transit API). */
    EXTERNAL_PROVIDER
}
