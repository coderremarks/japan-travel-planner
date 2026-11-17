package dev.tabiji.optimizer.problem;

import java.util.Objects;

/**
 * One day of the trip.
 *
 * @param availableTime  when the traveller leaves {@code startLocation} and the latest time to arrive at
 *                       {@code endLocation}
 * @param startLocation  travel-matrix index where the day starts (usually the hotel)
 * @param endLocation    travel-matrix index where the day ends
 */
public record TripDay(TimeWindow availableTime, int startLocation, int endLocation) {

    public TripDay {
        Objects.requireNonNull(availableTime, "availableTime");
        if (startLocation < 0 || endLocation < 0) {
            throw new IllegalArgumentException("locations must be >= 0");
        }
    }
}
