package dev.tabiji.optimizer.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeWindowTest {

    @Test
    void normalizeSortsAndMergesOverlappingAndTouchingWindows() {
        List<TimeWindow> normalized = TimeWindow.normalize(List.of(
                TimeWindow.of(780, 1020), TimeWindow.of(540, 720), TimeWindow.of(700, 760), TimeWindow.of(1020, 1080)));

        assertThat(normalized).containsExactly(TimeWindow.of(540, 760), TimeWindow.of(780, 1080));
    }

    @Test
    void intersection() {
        TimeWindow museum = TimeWindow.between(9, 30, 17, 0);
        assertThat(museum.intersection(TimeWindow.between(10, 0, 20, 0))).contains(TimeWindow.between(10, 0, 17, 0));
        assertThat(museum.intersection(TimeWindow.between(17, 0, 18, 0))).isEmpty();
        assertThat(TimeWindow.allDay().lengthMinutes()).isEqualTo(1440);
    }

    @ParameterizedTest
    @CsvSource({"-1, 10", "10, 10", "20, 10", "0, 1441"})
    void rejectsInvalidWindows(int start, int end) {
        assertThatIllegalArgumentException().isThrownBy(() -> TimeWindow.of(start, end));
    }
}
