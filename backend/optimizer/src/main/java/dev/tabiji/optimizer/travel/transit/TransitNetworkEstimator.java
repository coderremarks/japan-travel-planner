package dev.tabiji.optimizer.travel.transit;

import dev.tabiji.optimizer.geo.GeoPoint;
import dev.tabiji.optimizer.graph.Dijkstra;
import dev.tabiji.optimizer.graph.ShortestPathTree;
import dev.tabiji.optimizer.graph.WeightedDigraph;
import dev.tabiji.optimizer.travel.Leg;
import dev.tabiji.optimizer.travel.LegSource;
import dev.tabiji.optimizer.travel.TravelEstimator;
import dev.tabiji.optimizer.travel.TravelMatrix;
import dev.tabiji.optimizer.travel.TravelMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Door-to-door transit estimates on a <em>line-expanded</em> rail graph, using Dijkstra.
 *
 * <h2>Graph model</h2>
 * <ul>
 *   <li>One <b>concourse</b> node per station ({@code 0..S-1}) and one <b>platform</b> node per
 *       (line, stop) pair.</li>
 *   <li>concourse → platform: boarding, weighted with the expected wait of that line type;
 *       platform → concourse: alighting. A transfer is therefore "alight + board" and automatically
 *       pays a waiting penalty — no special transfer logic is needed.</li>
 *   <li>platform ↔ next platform of the same line: riding, weighted with
 *       {@code distance × railDetour ÷ scheduled speed}.</li>
 *   <li>concourse ↔ concourse for stations closer than {@code transferWalkMeters}: walking transfer
 *       (station complexes such as Gion-Shijo / Kyoto-Kawaramachi).</li>
 * </ul>
 *
 * <h2>Door-to-door query</h2>
 * For points {@code p → q}: up to {@code k} stations within walking distance of each point are
 * candidates; the answer is {@code min over (a, e) of walk(p, a) + dist(a, e) + walk(e, q)} with at least
 * one ride. {@link #estimateAll} runs one Dijkstra per distinct access station, so filling an
 * {@code n × n} matrix costs {@code O(n·k · (V + E) log V)} instead of {@code n²} separate searches.
 * If either point has no station within walking distance, there is <b>no transit estimate</b> — the
 * planner then falls back to other allowed modes instead of inventing one.
 *
 * <p>Fares: per boarding, {@code base + perKm × km}, rounded up to ¥10. These are estimates, not
 * official fares; legs are always labelled {@link LegSource#SAMPLE_TRANSIT_NETWORK} and estimated.
 */
public final class TransitNetworkEstimator implements TravelEstimator {

    private static final double EPSILON = 1e-9;

    private final TransitNetwork network;
    private final TransitAssumptions assumptions;
    private final WeightedDigraph graph;
    private final int stationCount;
    private final int[] platformLine;
    private final int[] platformStation;

    public TransitNetworkEstimator(TransitNetwork network, TransitAssumptions assumptions) {
        this.network = Objects.requireNonNull(network, "network");
        this.assumptions = Objects.requireNonNull(assumptions, "assumptions");
        List<Station> stations = network.stations();
        List<TransitLine> lines = network.lines();
        this.stationCount = stations.size();
        int platformCount =
                lines.stream().mapToInt(line -> line.stationIds().size()).sum();
        this.platformLine = new int[platformCount];
        this.platformStation = new int[platformCount];

        WeightedDigraph.Builder builder = WeightedDigraph.builder(stationCount + platformCount);
        int nextPlatform = 0;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            TransitLine line = lines.get(lineIndex);
            LineProfile profile = assumptions.profile(line.type());
            int firstPlatform = nextPlatform;
            for (int stop = 0; stop < line.stationIds().size(); stop++) {
                int station = network.indexOf(line.stationIds().get(stop));
                int platform = nextPlatform++;
                platformLine[platform] = lineIndex;
                platformStation[platform] = station;
                int platformNode = stationCount + platform;
                builder.addEdge(station, platformNode, profile.boardingWaitMinutes());
                builder.addEdge(platformNode, station, assumptions.alightingMinutes());
                if (stop > 0) {
                    builder.addUndirectedEdge(
                            platformNode - 1, platformNode, rideMinutes(platform - 1, platform, profile));
                }
            }
            if (line.loop()) {
                int lastPlatform = nextPlatform - 1;
                builder.addUndirectedEdge(
                        stationCount + lastPlatform,
                        stationCount + firstPlatform,
                        rideMinutes(lastPlatform, firstPlatform, profile));
            }
        }
        for (int a = 0; a < stationCount; a++) {
            for (int b = a + 1; b < stationCount; b++) {
                double meters = distanceMeters(a, b);
                if (meters <= assumptions.transferWalkMeters()) {
                    builder.addUndirectedEdge(a, b, assumptions.walkMinutes(meters));
                }
            }
        }
        this.graph = builder.build();
    }

    @Override
    public Set<TravelMode> supportedModes() {
        return EnumSet.of(TravelMode.TRANSIT);
    }

    @Override
    public Optional<Leg> estimate(GeoPoint from, GeoPoint to, TravelMode mode) {
        return estimateAll(List.of(from, to), mode).leg(0, 1);
    }

    @Override
    public TravelMatrix estimateAll(List<GeoPoint> points, TravelMode mode) {
        TravelMatrix.Builder result = TravelMatrix.builder(points.size());
        if (mode != TravelMode.TRANSIT) {
            return result.build();
        }
        List<List<Access>> access = new ArrayList<>(points.size());
        for (GeoPoint point : points) {
            access.add(nearbyStations(point));
        }
        Map<Integer, ShortestPathTree> treesByOrigin = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            for (int j = 0; j < points.size(); j++) {
                if (i == j) {
                    continue;
                }
                Leg leg = bestRoute(access.get(i), access.get(j), treesByOrigin);
                if (leg != null) {
                    result.set(i, j, leg);
                }
            }
        }
        return result.build();
    }

    /** Number of nodes in the line-expanded graph; exposed for diagnostics and benchmarks. */
    public int graphNodeCount() {
        return graph.nodeCount();
    }

    private Leg bestRoute(List<Access> origins, List<Access> destinations, Map<Integer, ShortestPathTree> trees) {
        Route best = null;
        for (Access origin : origins) {
            ShortestPathTree tree =
                    trees.computeIfAbsent(origin.station(), station -> Dijkstra.shortestPaths(graph, station));
            for (Access destination : destinations) {
                if (origin.station() == destination.station() || !tree.hasPathTo(destination.station())) {
                    continue;
                }
                double total =
                        origin.walkMinutes() + tree.distanceTo(destination.station()) + destination.walkMinutes();
                if (best != null && total >= best.totalMinutes()) {
                    continue;
                }
                PathSummary summary = summarise(tree.pathTo(destination.station()));
                if (summary.rides() > 0) {
                    best = new Route(total, origin, destination, summary);
                }
            }
        }
        if (best == null) {
            return null;
        }
        int minutes = (int) Math.ceil(best.totalMinutes() - EPSILON);
        int meters = (int) Math.round(best.origin().walkMeters()
                + best.summary().meters()
                + best.destination().walkMeters());
        return new Leg(
                TravelMode.TRANSIT,
                minutes,
                best.summary().fareYen(),
                meters,
                LegSource.SAMPLE_TRANSIT_NETWORK,
                true,
                best.summary().lineIds());
    }

    private PathSummary summarise(int[] path) {
        int rides = 0;
        double meters = 0.0;
        int fare = 0;
        List<String> lineIds = new ArrayList<>();
        int currentLine = -1;
        double segmentKm = 0.0;
        for (int k = 1; k < path.length; k++) {
            int u = path[k - 1];
            int v = path[k];
            boolean fromConcourse = u < stationCount;
            boolean toConcourse = v < stationCount;
            if (fromConcourse && toConcourse) {
                meters += distanceMeters(u, v) * assumptions.walkDetourFactor();
            } else if (fromConcourse) {
                currentLine = platformLine[v - stationCount];
                segmentKm = 0.0;
                lineIds.add(network.lines().get(currentLine).id());
            } else if (toConcourse) {
                fare += segmentFare(currentLine, segmentKm);
                currentLine = -1;
            } else {
                double km = rideKm(u - stationCount, v - stationCount);
                segmentKm += km;
                meters += km * 1000.0;
                rides++;
            }
        }
        return new PathSummary(rides, meters, fare, lineIds);
    }

    private int segmentFare(int lineIndex, double km) {
        LineProfile profile = assumptions.profile(network.lines().get(lineIndex).type());
        double yen = profile.baseFareYen() + profile.yenPerKm() * km;
        return (int) (Math.ceil((yen - EPSILON) / 10.0) * 10);
    }

    private List<Access> nearbyStations(GeoPoint point) {
        List<Access> nearby = new ArrayList<>();
        List<Station> stations = network.stations();
        for (int s = 0; s < stations.size(); s++) {
            double meters = point.distanceMetersTo(stations.get(s).location());
            if (meters <= assumptions.maxAccessWalkMeters()) {
                nearby.add(new Access(
                        s, assumptions.walkMinutes(meters), meters * assumptions.walkDetourFactor(), meters));
            }
        }
        nearby.sort(Comparator.comparingDouble(Access::straightMeters).thenComparingInt(Access::station));
        return nearby.size() > assumptions.maxAccessStations()
                ? List.copyOf(nearby.subList(0, assumptions.maxAccessStations()))
                : nearby;
    }

    private double rideMinutes(int platformA, int platformB, LineProfile profile) {
        return rideKm(platformA, platformB) / profile.scheduledSpeedKmh() * 60.0;
    }

    private double rideKm(int platformA, int platformB) {
        return distanceMeters(platformStation[platformA], platformStation[platformB])
                * assumptions.railDetourFactor()
                / 1000.0;
    }

    private double distanceMeters(int stationA, int stationB) {
        List<Station> stations = network.stations();
        return stations.get(stationA)
                .location()
                .distanceMetersTo(stations.get(stationB).location());
    }

    private record Access(int station, double walkMinutes, double walkMeters, double straightMeters) {}

    private record PathSummary(int rides, double meters, int fareYen, List<String> lineIds) {}

    private record Route(double totalMinutes, Access origin, Access destination, PathSummary summary) {}
}
