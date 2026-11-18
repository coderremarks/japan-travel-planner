package dev.tabiji.transport;

import dev.tabiji.optimizer.geo.GeoPoint;
import dev.tabiji.optimizer.travel.transit.LineType;
import dev.tabiji.optimizer.travel.transit.Station;
import dev.tabiji.optimizer.travel.transit.TransitLine;
import dev.tabiji.optimizer.travel.transit.TransitNetwork;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the bundled sample network ({@code transit/sample-network.json}). The JSON format is an
 * adapter concern; the optimizer only sees the validated {@link TransitNetwork}. A real deployment
 * would build the same structure from GTFS / GTFS-JP or ODPT feeds.
 */
public final class SampleTransitNetworkLoader {

    private SampleTransitNetworkLoader() {}

    record NetworkFile(int schemaVersion, String notice, List<StationJson> stations, List<LineJson> lines) {}

    record StationJson(String id, String nameEn, String nameJa, double lat, double lng) {}

    record LineJson(String id, String nameEn, String nameJa, LineType type, boolean loop, List<String> stations) {}

    public static TransitNetwork load(Resource resource, JsonMapper jsonMapper) {
        try (InputStream input = resource.getInputStream()) {
            NetworkFile file = jsonMapper.readValue(input, NetworkFile.class);
            if (file.schemaVersion() != 1) {
                throw new IllegalStateException("unsupported transit network schema " + file.schemaVersion());
            }
            return new TransitNetwork(
                    file.stations().stream()
                            .map(s -> new Station(s.id(), s.nameEn(), s.nameJa(), GeoPoint.of(s.lat(), s.lng())))
                            .toList(),
                    file.lines().stream()
                            .map(l -> new TransitLine(l.id(), l.nameEn(), l.nameJa(), l.type(), l.stations(), l.loop()))
                            .toList());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read transit network " + resource.getDescription(), e);
        }
    }
}
