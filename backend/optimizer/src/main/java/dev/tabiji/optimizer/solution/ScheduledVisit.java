package dev.tabiji.optimizer.solution;

import dev.tabiji.optimizer.travel.Leg;
import java.util.Objects;

/**
 * A visit in the final plan. Times are minutes since local midnight.
 *
 * @param travelFromPrevious leg from the previous visit (or the day's start location)
 */
public record ScheduledVisit(
        String candidateId, int arrivalMinute, int startMinute, int endMinute, Leg travelFromPrevious) {

    public ScheduledVisit {
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(travelFromPrevious, "travelFromPrevious");
        if (startMinute < arrivalMinute || endMinute <= startMinute) {
            throw new IllegalArgumentException("visit times must satisfy arrival <= start < end");
        }
    }

    /** Time spent waiting for the place to open after arriving. */
    public int waitMinutes() {
        return startMinute - arrivalMinute;
    }
}
