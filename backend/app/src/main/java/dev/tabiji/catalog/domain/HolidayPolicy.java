package dev.tabiji.catalog.domain;

/** How a place's regular closing day interacts with Japanese national holidays. */
public enum HolidayPolicy {
    /** Holidays use the normal hours of that weekday. */
    SAME_AS_WEEKDAY,
    /**
     * The common museum rule 「休館日が祝日の場合は開館し、翌平日に休館」: if the regular closing day is a
     * national holiday, the place opens that day and closes on the next non-holiday day instead.
     */
    OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY
}
