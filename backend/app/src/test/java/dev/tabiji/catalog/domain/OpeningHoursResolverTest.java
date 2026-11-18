package dev.tabiji.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.optimizer.problem.TimeWindow;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Japanese opening rules on real 2026 holidays (Cabinet Office data): a museum closed on Mondays
 * under 「休館日が祝日の場合は開館し、翌平日に休館」.
 */
class OpeningHoursResolverTest {

    private static final LocalDate LABOUR_THANKSGIVING_MONDAY = LocalDate.of(2026, 11, 23);
    private static final Set<LocalDate> HOLIDAYS_2026 = Set.of(
            LocalDate.of(2026, 5, 3),
            LocalDate.of(2026, 5, 4),
            LocalDate.of(2026, 5, 5),
            LocalDate.of(2026, 5, 6),
            LABOUR_THANKSGIVING_MONDAY);
    private static final TimeWindow MUSEUM_HOURS = TimeWindow.between(9, 30, 17, 0);

    /** Tuesday to Sunday 09:30–17:00, closed Mondays. */
    private static Set<WeeklyHours> closedMondays() {
        Set<WeeklyHours> hours = new HashSet<>();
        for (int day = 2; day <= 7; day++) {
            hours.add(new WeeklyHours(day, 570, 1020));
        }
        return hours;
    }

    private static List<TimeWindow> museum(LocalDate date, HolidayPolicy policy, Set<Closure> closures) {
        return OpeningHoursResolver.windowsOn(closedMondays(), closures, policy, date, HOLIDAYS_2026);
    }

    @Test
    void regularMondayIsClosedAndTuesdayOpen() {
        assertThat(museum(LocalDate.of(2026, 11, 30), HolidayPolicy.OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY, Set.of()))
                .isEmpty();
        assertThat(museum(LocalDate.of(2026, 12, 1), HolidayPolicy.OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY, Set.of()))
                .containsExactly(MUSEUM_HOURS);
    }

    @Test
    void holidayMondayOpensAndTheNextDayCloses() {
        assertThat(museum(LABOUR_THANKSGIVING_MONDAY, HolidayPolicy.OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY, Set.of()))
                .containsExactly(MUSEUM_HOURS);
        assertThat(museum(LocalDate.of(2026, 11, 24), HolidayPolicy.OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY, Set.of()))
                .isEmpty();
        assertThat(museum(LocalDate.of(2026, 11, 25), HolidayPolicy.OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY, Set.of()))
                .containsExactly(MUSEUM_HOURS);
    }

    @Test
    void goldenWeekChainShiftsTheClosureToTheFirstNonHoliday() {
        // Mon 4 May is a holiday, so are Tue 5 and Wed 6 May → the museum closes on Thu 7 May instead.
        assertThat(museum(LocalDate.of(2026, 5, 4), HolidayPolicy.OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY, Set.of()))
                .containsExactly(MUSEUM_HOURS);
        assertThat(museum(LocalDate.of(2026, 5, 6), HolidayPolicy.OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY, Set.of()))
                .containsExactly(MUSEUM_HOURS);
        assertThat(museum(LocalDate.of(2026, 5, 7), HolidayPolicy.OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY, Set.of()))
                .isEmpty();
    }

    @Test
    void withoutThePolicyHolidaysFollowTheWeekday() {
        assertThat(museum(LABOUR_THANKSGIVING_MONDAY, HolidayPolicy.SAME_AS_WEEKDAY, Set.of()))
                .isEmpty();
        assertThat(museum(LocalDate.of(2026, 11, 24), HolidayPolicy.SAME_AS_WEEKDAY, Set.of()))
                .containsExactly(MUSEUM_HOURS);
    }

    @Test
    void explicitClosuresWinOverEverything() {
        LocalDate newYearsEve = LocalDate.of(2026, 12, 31);
        Set<Closure> closures = Set.of(new Closure(newYearsEve, "Year-end closure"));
        assertThat(museum(newYearsEve, HolidayPolicy.OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY, closures))
                .isEmpty();
    }

    @Test
    void lunchBreaksBecomeSeparateWindows() {
        Set<WeeklyHours> hours = Set.of(new WeeklyHours(3, 540, 720), new WeeklyHours(3, 780, 1020));
        assertThat(OpeningHoursResolver.windowsOn(
                        hours, Set.of(), HolidayPolicy.SAME_AS_WEEKDAY, LocalDate.of(2026, 12, 2), Set.of()))
                .containsExactly(TimeWindow.of(540, 720), TimeWindow.of(780, 1020));
    }
}
