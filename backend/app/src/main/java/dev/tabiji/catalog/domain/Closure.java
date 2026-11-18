package dev.tabiji.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

/** A date on which a place is closed regardless of its weekly hours. */
@Embeddable
public record Closure(
        @Column(name = "closed_on", nullable = false) LocalDate closedOn,
        @Column(name = "note", length = 200) String note) {}
