package dev.tabiji.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One opening interval on an ISO weekday (1 = Monday). Several rows per day model lunch breaks.
 * Minutes since midnight; {@code closesMinute = 1440} means "until midnight".
 */
@Embeddable
public record WeeklyHours(
        @Column(name = "day_of_week", nullable = false) int dayOfWeek,
        @Column(name = "opens_minute", nullable = false) int opensMinute,
        @Column(name = "closes_minute", nullable = false) int closesMinute) {}
