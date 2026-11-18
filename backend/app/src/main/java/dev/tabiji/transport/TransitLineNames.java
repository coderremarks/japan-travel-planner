package dev.tabiji.transport;

import dev.tabiji.optimizer.travel.transit.TransitLine;
import dev.tabiji.optimizer.travel.transit.TransitNetwork;
import dev.tabiji.shared.i18n.LocalizedText;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Bilingual names of transit lines, for "via Tokaido Shinkansen / 東海道新幹線" in plans. */
public final class TransitLineNames {

    private final Map<String, LocalizedText> names;

    public TransitLineNames(TransitNetwork network) {
        this.names = network.lines().stream()
                .collect(Collectors.toUnmodifiableMap(
                        TransitLine::id, line -> LocalizedText.of(line.nameEn(), line.nameJa()), (a, b) -> a));
    }

    public List<LocalizedText> resolve(List<String> lineIds) {
        return lineIds.stream()
                .map(id -> names.getOrDefault(id, LocalizedText.of(id, id)))
                .toList();
    }
}
