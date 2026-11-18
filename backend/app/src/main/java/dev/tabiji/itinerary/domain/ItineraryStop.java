package dev.tabiji.itinerary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** A place the traveller added, in list order; {@code mandatory} marks a must-visit. */
@Embeddable
public record ItineraryStop(
        @Column(name = "place_id", nullable = false) UUID placeId,
        @Column(name = "mandatory", nullable = false) boolean mandatory) {}
