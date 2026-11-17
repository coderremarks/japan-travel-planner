package dev.tabiji.optimizer.travel.transit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.tabiji.optimizer.geo.GeoPoint;
import dev.tabiji.optimizer.travel.Leg;
import dev.tabiji.optimizer.travel.LegSource;
import dev.tabiji.optimizer.travel.TravelMatrix;
import dev.tabiji.optimizer.travel.TravelMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A synthetic east–west network along the equator (0.01° ≈ 1.11 km):
 *
 * <pre>
 *   A ===red=== B ===red=== C            (subway)
 *               B' --------------- D      (express from B', 300 m from B; walking transfer)
 * </pre>
 */
class TransitNetworkEstimatorTest {

    private static final Station A = station("a", 0.000);
    private static final Station B = station("b", 0.020);
    private static final Station C = station("c", 0.040);
    private static final Station B_PRIME = new Station("b2", "B2", "B2", GeoPoint.of(0.0027, 0.020));
    private static final Station D = station("d", 0.200);

    private static final TransitNetwork NETWORK = new TransitNetwork(
            List.of(A, B, C, B_PRIME, D),
            List.of(
                    new TransitLine("red", "Red", "赤", LineType.SUBWAY, List.of("a", "b", "c"), false),
                    new TransitLine("express", "Express", "急行", LineType.REGIONAL_RAIL, List.of("b2", "d"), false)));

    private final TransitNetworkEstimator estimator = new TransitNetworkEstimator(NETWORK, TransitAssumptions.DEFAULT);

    private static Station station(String id, double longitude) {
        return new Station(id, id.toUpperCase(java.util.Locale.ROOT), id, GeoPoint.of(0.0, longitude));
    }

    @Test
    void ridesASingleLineWithWaitRideAndAlighting() {
        Leg leg = estimator
                .estimate(A.location(), C.location(), TravelMode.TRANSIT)
                .orElseThrow();

        double km = A.location().distanceMetersTo(C.location()) * 1.15 / 1000.0;
        double expectedMinutes = 3.0 /* wait */ + km / 30.0 * 60.0 + 1.0 /* alight */;
        assertThat(leg.minutes()).isEqualTo((int) Math.ceil(expectedMinutes));
        assertThat(leg.via()).containsExactly("red");
        assertThat(leg.source()).isEqualTo(LegSource.SAMPLE_TRANSIT_NETWORK);
        assertThat(leg.estimated()).isTrue();
        int expectedFare = (int) (Math.ceil((180 + 12 * km) / 10.0) * 10);
        assertThat(leg.costYen()).isEqualTo(expectedFare);
    }

    @Test
    void transfersThroughAWalkingConnectionAndPaysBothFares() {
        Leg leg = estimator
                .estimate(A.location(), D.location(), TravelMode.TRANSIT)
                .orElseThrow();

        assertThat(leg.via()).containsExactly("red", "express");
        assertThat(leg.costYen()).isGreaterThan(180 + 170);
    }

    @Test
    void returnsNoEstimateWhenThereIsNoStationWithinWalkingDistance() {
        GeoPoint remote = GeoPoint.of(0.1, 0.1); // ~11 km from every station
        assertThat(estimator.estimate(A.location(), remote, TravelMode.TRANSIT)).isEmpty();
    }

    @Test
    void requiresAtLeastOneRide() {
        GeoPoint nearA = GeoPoint.of(0.001, 0.0);
        GeoPoint alsoNearA = GeoPoint.of(-0.001, 0.0);
        assertThat(estimator.estimate(nearA, alsoNearA, TravelMode.TRANSIT)).isEmpty();
    }

    @Test
    void onlyAnswersForTransit() {
        assertThat(estimator.estimate(A.location(), C.location(), TravelMode.WALK))
                .isEmpty();
        assertThat(estimator.supportedModes()).containsExactly(TravelMode.TRANSIT);
    }

    @Test
    void matrixIsFilledForAllPairsWithOneDijkstraPerAccessStation() {
        TravelMatrix matrix =
                estimator.estimateAll(List.of(A.location(), C.location(), D.location()), TravelMode.TRANSIT);

        assertThat(matrix.isReachable(0, 1)).isTrue();
        assertThat(matrix.isReachable(1, 2)).isTrue();
        assertThat(matrix.isReachable(2, 0)).isTrue();
        assertThat(matrix.requireLeg(0, 1).minutes())
                .isEqualTo(matrix.requireLeg(1, 0).minutes());
        assertThat(estimator.graphNodeCount()).isEqualTo(5 + 3 + 2);
    }

    @Test
    void loopLinesConnectTheLastStationBackToTheFirst() {
        TransitNetwork loop = new TransitNetwork(
                List.of(A, B, C),
                List.of(new TransitLine("ring", "Ring", "環状", LineType.URBAN_RAIL, List.of("a", "b", "c"), true)));
        Leg leg = new TransitNetworkEstimator(loop, TransitAssumptions.DEFAULT)
                .estimate(C.location(), A.location(), TravelMode.TRANSIT)
                .orElseThrow();
        assertThat(leg.via()).containsExactly("ring");
    }

    @Test
    void validatesNetworkDefinitions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TransitNetwork(
                        List.of(A),
                        List.of(new TransitLine("x", "X", "X", LineType.SUBWAY, List.of("a", "zz"), false))));
        assertThatIllegalArgumentException().isThrownBy(() -> new TransitNetwork(List.of(A, A), List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TransitLine("x", "X", "X", LineType.SUBWAY, List.of("a", "a"), false));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TransitLine("x", "X", "X", LineType.SUBWAY, List.of("a"), false));
        assertThat(NETWORK.station("b")).contains(B);
        assertThat(NETWORK.station("nope")).isEmpty();
    }
}
