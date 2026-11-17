package dev.tabiji.optimizer.solution;

import dev.tabiji.optimizer.travel.Leg;
import java.util.List;
import java.util.Objects;

/**
 * The schedule of one trip day.
 *
 * @param departureMinute      when the traveller leaves the start location
 * @param returnLeg            leg from the last visit (or start) to the end location
 * @param returnArrivalMinute  arrival time at the end location
 * @param travelMinutes        all legs of the day including the return leg
 */
public record DaySchedule(
        int dayIndex,
        int departureMinute,
        List<ScheduledVisit> visits,
        Leg returnLeg,
        int returnArrivalMinute,
        int travelMinutes,
        long transportCostYen,
        long admissionYen) {

    public DaySchedule {
        visits = List.copyOf(visits);
        Objects.requireNonNull(returnLeg, "returnLeg");
    }
}
