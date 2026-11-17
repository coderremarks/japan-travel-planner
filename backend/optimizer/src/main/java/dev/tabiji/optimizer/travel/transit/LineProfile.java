package dev.tabiji.optimizer.travel.transit;

/**
 * Assumptions for one {@link LineType}.
 *
 * @param scheduledSpeedKmh   average speed <em>including</em> station stops (表定速度), used because the
 *                            sample network omits most intermediate stations
 * @param boardingWaitMinutes expected wait when boarding, roughly half the typical headway; also acts as
 *                            the transfer penalty, since a transfer is "alight + board"
 * @param baseFareYen         fare added per boarding
 * @param yenPerKm            distance-based fare component
 */
public record LineProfile(double scheduledSpeedKmh, double boardingWaitMinutes, int baseFareYen, double yenPerKm) {

    public LineProfile {
        if (!(scheduledSpeedKmh > 0.0) || Double.isInfinite(scheduledSpeedKmh)) {
            throw new IllegalArgumentException("scheduledSpeedKmh must be positive and finite");
        }
        if (!(boardingWaitMinutes >= 0.0) || !(yenPerKm >= 0.0) || baseFareYen < 0) {
            throw new IllegalArgumentException("waits and fares must be >= 0");
        }
    }
}
