package dev.tabiji.optimizer.travel.transit;

import dev.tabiji.optimizer.geo.GeoPoint;
import java.util.Objects;

/** A station (or a merged station complex) in the transit network. */
public record Station(String id, String nameEn, String nameJa, GeoPoint location) {

    public Station {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameEn, "nameEn");
        Objects.requireNonNull(nameJa, "nameJa");
        Objects.requireNonNull(location, "location");
        if (id.isBlank()) {
            throw new IllegalArgumentException("station id must not be blank");
        }
    }
}
