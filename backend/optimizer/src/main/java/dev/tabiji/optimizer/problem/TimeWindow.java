package dev.tabiji.optimizer.problem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Half-open interval of minutes since local midnight, {@code [startMinute, endMinute)}. Japan has no
 * daylight-saving time, so minute arithmetic within a day is exact.
 */
public record TimeWindow(int startMinute, int endMinute) {

    public static final int MINUTES_PER_DAY = 24 * 60;

    public TimeWindow {
        if (startMinute < 0 || endMinute > MINUTES_PER_DAY || startMinute >= endMinute) {
            throw new IllegalArgumentException(
                    "time window must satisfy 0 <= start < end <= 1440: [" + startMinute + ", " + endMinute + ")");
        }
    }

    public static TimeWindow of(int startMinute, int endMinute) {
        return new TimeWindow(startMinute, endMinute);
    }

    /** Convenience factory, e.g. {@code between(9, 30, 17, 0)} for 09:30–17:00. */
    public static TimeWindow between(int startHour, int startMinute, int endHour, int endMinute) {
        return new TimeWindow(startHour * 60 + startMinute, endHour * 60 + endMinute);
    }

    public static TimeWindow allDay() {
        return new TimeWindow(0, MINUTES_PER_DAY);
    }

    public int lengthMinutes() {
        return endMinute - startMinute;
    }

    public Optional<TimeWindow> intersection(TimeWindow other) {
        int start = Math.max(startMinute, other.startMinute);
        int end = Math.min(endMinute, other.endMinute);
        return start < end ? Optional.of(new TimeWindow(start, end)) : Optional.empty();
    }

    /** Sorts windows and merges overlapping or touching ones, so lookups can scan left to right. */
    public static List<TimeWindow> normalize(List<TimeWindow> windows) {
        List<TimeWindow> sorted = new ArrayList<>(windows);
        sorted.sort(Comparator.comparingInt(TimeWindow::startMinute).thenComparingInt(TimeWindow::endMinute));
        List<TimeWindow> merged = new ArrayList<>(sorted.size());
        for (TimeWindow window : sorted) {
            if (!merged.isEmpty() && window.startMinute <= merged.getLast().endMinute) {
                TimeWindow last = merged.removeLast();
                merged.add(new TimeWindow(last.startMinute, Math.max(last.endMinute, window.endMinute)));
            } else {
                merged.add(window);
            }
        }
        return List.copyOf(merged);
    }
}
