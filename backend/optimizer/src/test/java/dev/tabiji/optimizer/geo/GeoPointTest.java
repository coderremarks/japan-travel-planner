package dev.tabiji.optimizer.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeoPointTest {

    private static final GeoPoint TOKYO_STATION = GeoPoint.of(35.6812, 139.7671);
    private static final GeoPoint KYOTO_STATION = GeoPoint.of(34.9858, 135.7588);

    @Test
    void distanceBetweenTokyoAndKyotoStationsMatchesTheKnownGreatCircleDistance() {
        // ~364 km east-west and ~77 km north-south: about 372 km in a straight line (the track is ~476 km).
        assertThat(TOKYO_STATION.distanceMetersTo(KYOTO_STATION)).isCloseTo(372_000, within(3_000.0));
    }

    @Test
    void distanceIsSymmetricAndZeroToItself() {
        assertThat(TOKYO_STATION.distanceMetersTo(TOKYO_STATION)).isZero();
        assertThat(TOKYO_STATION.distanceMetersTo(KYOTO_STATION))
                .isCloseTo(KYOTO_STATION.distanceMetersTo(TOKYO_STATION), within(1e-6));
    }

    @Test
    void antipodalPointsDoNotProduceNaN() {
        double halfCircumference = GeoPoint.of(0, 0).distanceMetersTo(GeoPoint.of(0, 180));
        assertThat(halfCircumference).isCloseTo(Math.PI * 6_371_008.8, within(1.0));
    }

    @ParameterizedTest
    @CsvSource({"91, 0", "-91, 0", "0, 181", "0, -181", "NaN, 0", "0, Infinity"})
    void rejectsInvalidCoordinates(double latitude, double longitude) {
        assertThatIllegalArgumentException().isThrownBy(() -> GeoPoint.of(latitude, longitude));
    }
}
