package dev.tabiji.optimizer.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.tabiji.optimizer.geo.GeoPoint;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TravelEstimationTest {

    private static final GeoPoint SENSO_JI = GeoPoint.of(35.7148, 139.7967);
    private static final GeoPoint ASAKUSA_STATION = GeoPoint.of(35.7110, 139.7964); // ~420 m south
    private static final GeoPoint SHIBUYA = GeoPoint.of(35.6580, 139.7016); // ~10.6 km south-west

    private final DistanceModelEstimator distance = new DistanceModelEstimator(DistanceModel.DEFAULT);

    @Test
    void walkingUsesDetourAndSpeedAndIsFree() {
        Leg walk = distance.estimate(SENSO_JI, ASAKUSA_STATION, TravelMode.WALK).orElseThrow();

        double meters = SENSO_JI.distanceMetersTo(ASAKUSA_STATION) * 1.25;
        assertThat(walk.minutes()).isEqualTo((int) Math.ceil(meters / 75.0));
        assertThat(walk.costYen()).isZero();
        assertThat(walk.estimated()).isTrue();
        assertThat(walk.source()).isEqualTo(LegSource.DISTANCE_MODEL);
    }

    @Test
    void walkingIsNotOfferedBeyondTheLimit() {
        assertThat(distance.estimate(SENSO_JI, SHIBUYA, TravelMode.WALK)).isEmpty();
    }

    @Test
    void taxiFareGrowsWithDistanceAndIsRoundedToTenYen() {
        Leg shortRide =
                distance.estimate(SENSO_JI, ASAKUSA_STATION, TravelMode.TAXI).orElseThrow();
        Leg longRide = distance.estimate(SENSO_JI, SHIBUYA, TravelMode.TAXI).orElseThrow();

        assertThat(shortRide.costYen()).isEqualTo(500); // within the flag-fall distance
        assertThat(longRide.costYen()).isGreaterThan(4_000).isLessThan(8_000);
        assertThat(longRide.costYen() % 10).isZero();
        assertThat(longRide.minutes()).isGreaterThan(shortRide.minutes());
    }

    @Test
    void distanceModelDoesNotInventTransit() {
        assertThat(distance.estimate(SENSO_JI, SHIBUYA, TravelMode.TRANSIT)).isEmpty();
        assertThat(distance.supportedModes()).containsExactlyInAnyOrder(TravelMode.WALK, TravelMode.TAXI);
    }

    @Test
    void matrixBuilderPicksTheModeWithTheLowestGeneralisedCost() {
        TravelEstimator transit = fixedEstimator(TravelMode.TRANSIT, 20, 200);
        TravelMatrixBuilder builder = new TravelMatrixBuilder(List.of(distance, transit), ModeChoicePolicy.DEFAULT);

        TravelMatrix matrix = builder.build(
                List.of(SENSO_JI, SHIBUYA), EnumSet.of(TravelMode.WALK, TravelMode.TRANSIT, TravelMode.TAXI));

        // Transit (20 min + ¥200 ≈ 26.7 min) beats a ~¥6,000 taxi; walking is out of range.
        assertThat(matrix.requireLeg(0, 1).mode()).isEqualTo(TravelMode.TRANSIT);
        assertThat(matrix.requireLeg(0, 0)).isEqualTo(Leg.stay());
    }

    @Test
    void shortWalksAreAllowedEvenWhenWalkingIsNotSelected() {
        TravelMatrixBuilder builder = new TravelMatrixBuilder(
                List.of(distance, fixedEstimator(TravelMode.TRANSIT, 30, 200)), ModeChoicePolicy.DEFAULT);

        TravelMatrix near = builder.build(List.of(SENSO_JI, ASAKUSA_STATION), EnumSet.of(TravelMode.TRANSIT));

        assertThat(near.requireLeg(0, 1).mode()).isEqualTo(TravelMode.WALK);
        assertThat(near.requireLeg(0, 1).minutes())
                .isLessThanOrEqualTo(ModeChoicePolicy.DEFAULT.alwaysAllowedWalkMinutes());
    }

    @Test
    void pairsWithoutAnAllowedModeStayUnreachable() {
        TravelMatrixBuilder builder = new TravelMatrixBuilder(List.of(distance), ModeChoicePolicy.DEFAULT);

        TravelMatrix matrix = builder.build(List.of(SENSO_JI, SHIBUYA), EnumSet.of(TravelMode.TRANSIT));

        assertThat(matrix.isReachable(0, 1)).isFalse();
        assertThat(matrix.leg(0, 1)).isEmpty();
    }

    @Test
    void rejectsAmbiguousEstimatorsAndEmptyModeSets() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TravelMatrixBuilder(List.of(distance, distance), ModeChoicePolicy.DEFAULT));
        TravelMatrixBuilder builder = new TravelMatrixBuilder(List.of(distance), ModeChoicePolicy.DEFAULT);
        assertThatIllegalArgumentException().isThrownBy(() -> builder.build(List.of(SENSO_JI), Set.of()));
    }

    private static TravelEstimator fixedEstimator(TravelMode mode, int minutes, int costYen) {
        return new TravelEstimator() {
            @Override
            public Set<TravelMode> supportedModes() {
                return EnumSet.of(mode);
            }

            @Override
            public Optional<Leg> estimate(GeoPoint from, GeoPoint to, TravelMode requested) {
                return requested == mode
                        ? Optional.of(Leg.estimate(mode, minutes, costYen, 1000, LegSource.EXTERNAL_PROVIDER))
                        : Optional.empty();
            }
        };
    }
}
