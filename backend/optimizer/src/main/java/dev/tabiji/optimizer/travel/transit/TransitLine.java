package dev.tabiji.optimizer.travel.transit;

import java.util.List;
import java.util.Objects;

/**
 * A line as an ordered list of station ids. Trains are assumed to run in both directions; a
 * {@code loop} line additionally connects its last station back to the first (e.g. JR Yamanote).
 */
public record TransitLine(
        String id, String nameEn, String nameJa, LineType type, List<String> stationIds, boolean loop) {

    public TransitLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameEn, "nameEn");
        Objects.requireNonNull(nameJa, "nameJa");
        Objects.requireNonNull(type, "type");
        stationIds = List.copyOf(stationIds);
        if (id.isBlank()) {
            throw new IllegalArgumentException("line id must not be blank");
        }
        if (stationIds.size() < 2) {
            throw new IllegalArgumentException("line " + id + " needs at least two stations");
        }
        for (int i = 1; i < stationIds.size(); i++) {
            if (stationIds.get(i).equals(stationIds.get(i - 1))) {
                throw new IllegalArgumentException("line " + id + " lists " + stationIds.get(i) + " twice in a row");
            }
        }
        if (loop && stationIds.getFirst().equals(stationIds.getLast())) {
            throw new IllegalArgumentException("loop line " + id + " must not repeat its first station at the end");
        }
    }
}
