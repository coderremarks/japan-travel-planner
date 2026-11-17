package dev.tabiji.optimizer.travel.transit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Validated, immutable set of stations and lines. */
public final class TransitNetwork {

    private final List<Station> stations;
    private final List<TransitLine> lines;
    private final Map<String, Integer> stationIndex;

    public TransitNetwork(List<Station> stations, List<TransitLine> lines) {
        this.stations = List.copyOf(stations);
        this.lines = List.copyOf(lines);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < this.stations.size(); i++) {
            if (index.put(this.stations.get(i).id(), i) != null) {
                throw new IllegalArgumentException(
                        "duplicate station id " + this.stations.get(i).id());
            }
        }
        Map<String, Boolean> lineIds = new HashMap<>();
        for (TransitLine line : this.lines) {
            if (lineIds.put(line.id(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("duplicate line id " + line.id());
            }
            for (String stationId : line.stationIds()) {
                if (!index.containsKey(stationId)) {
                    throw new IllegalArgumentException(
                            "line " + line.id() + " references unknown station " + stationId);
                }
            }
        }
        this.stationIndex = Map.copyOf(index);
    }

    public List<Station> stations() {
        return stations;
    }

    public List<TransitLine> lines() {
        return lines;
    }

    public Optional<Station> station(String id) {
        Integer index = stationIndex.get(id);
        return index == null ? Optional.empty() : Optional.of(stations.get(index));
    }

    int indexOf(String stationId) {
        Integer index = stationIndex.get(stationId);
        if (index == null) {
            throw new IllegalArgumentException("unknown station " + stationId);
        }
        return index;
    }
}
