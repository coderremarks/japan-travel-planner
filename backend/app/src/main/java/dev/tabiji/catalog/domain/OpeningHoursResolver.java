package dev.tabiji.catalog.domain;

import dev.tabiji.optimizer.problem.TimeWindow;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns weekly hours, one-off closures and the national-holiday calendar into the concrete opening
 * windows of a place on a given date — the input format of the optimizer.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>A listed closure date closes the place.</li>
 *   <li>With {@link HolidayPolicy#OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY}: if the date is a holiday falling on
 *       the regular closing day, the place opens with the hours of its next regular opening day; if the
 *       date is the first non-holiday after such a holiday chain, the place is closed.</li>
 *   <li>Otherwise the weekly hours of that weekday apply.</li>
 * </ol>
 * Pure and stateless: the holiday set is passed in, which keeps it trivially unit-testable.
 */
public final class OpeningHoursResolver {

    private OpeningHoursResolver() {}

    public static List<TimeWindow> windowsOn(Place place, LocalDate date, Set<LocalDate> holidays) {
        return windowsOn(place.openingHours(), place.closures(), place.holidayPolicy(), date, holidays);
    }

    static List<TimeWindow> windowsOn(
            Set<WeeklyHours> weekly,
            Set<Closure> closures,
            HolidayPolicy policy,
            LocalDate date,
            Set<LocalDate> holidays) {
        if (closures.stream().anyMatch(closure -> closure.closedOn().equals(date))) {
            return List.of();
        }
        List<TimeWindow> regular = hoursOn(weekly, date.getDayOfWeek());
        if (policy == HolidayPolicy.OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY) {
            boolean holiday = holidays.contains(date);
            if (holiday && regular.isEmpty()) {
                return nextRegularHours(weekly, date.getDayOfWeek());
            }
            if (!holiday && !regular.isEmpty() && followsShiftedClosure(weekly, date, holidays)) {
                return List.of();
            }
        }
        return regular;
    }

    /** True if the holiday chain directly before {@code date} contains a regular closing day. */
    private static boolean followsShiftedClosure(Set<WeeklyHours> weekly, LocalDate date, Set<LocalDate> holidays) {
        for (LocalDate previous = date.minusDays(1); holidays.contains(previous); previous = previous.minusDays(1)) {
            if (hoursOn(weekly, previous.getDayOfWeek()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static List<TimeWindow> nextRegularHours(Set<WeeklyHours> weekly, DayOfWeek closedDay) {
        for (int offset = 1; offset < 7; offset++) {
            List<TimeWindow> hours = hoursOn(weekly, closedDay.plus(offset));
            if (!hours.isEmpty()) {
                return hours;
            }
        }
        return List.of();
    }

    private static List<TimeWindow> hoursOn(Set<WeeklyHours> weekly, DayOfWeek day) {
        return TimeWindow.normalize(weekly.stream()
                .filter(hours -> hours.dayOfWeek() == day.getValue())
                .sorted(Comparator.comparingInt(WeeklyHours::opensMinute))
                .map(hours -> TimeWindow.of(hours.opensMinute(), hours.closesMinute()))
                .collect(Collectors.toList()));
    }
}
