package dev.tabiji.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.catalog.domain.PlaceCategory;
import dev.tabiji.itinerary.domain.PlanDocument;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.solution.PlanStatus;
import dev.tabiji.optimizer.travel.LegSource;
import dev.tabiji.optimizer.travel.TravelMode;
import dev.tabiji.shared.i18n.LocalizedText;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalendarExporterTest {

    @Test
    void escapesTextValuesPerRfc5545AndDropsControlCharacters() {
        assertThat(CalendarExporter.text("a,b;c\\d\ne")).isEqualTo("a\\,b\\;c\\\\d\\ne");
        assertThat(CalendarExporter.text("evil\r\nATTENDEE:mailto:x")).isEqualTo("evil\\nATTENDEE:mailto:x");
        assertThat(CalendarExporter.text("tab\there\u0000")).isEqualTo("tabhere");
    }

    @Test
    void producesFoldedCrlfLinesWithoutBreakingUtf8() {
        String longJapanese = "京都の古都を巡るとても長いタイトルの旅行計画".repeat(3);
        String ics = CalendarExporter.export(longJapanese, plan(), true);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\r").doesNotContain("\n");
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).as(line).isLessThanOrEqualTo(75);
        }
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).contains("X-WR-CALNAME:" + longJapanese);
        assertThat(unfolded).contains("DTSTART;TZID=Asia/Tokyo:20261208T093000");
        assertThat(unfolded).contains("GEO:35.714800;139.796700");
        assertThat(unfolded).contains("SUMMARY:浅草寺");
    }

    private static PlanDocument plan() {
        PlanDocument.Leg leg = new PlanDocument.Leg(
                TravelMode.TRANSIT, 20, 210, 5000, LegSource.SAMPLE_TRANSIT_NETWORK, true, List.of());
        PlanDocument.Visit visit = new PlanDocument.Visit(
                UUID.randomUUID(),
                "senso-ji",
                LocalizedText.of("Senso-ji Temple", "浅草寺"),
                PlaceCategory.CULTURE,
                35.7148,
                139.7967,
                LocalTime.of(9, 25),
                LocalTime.of(9, 30),
                LocalTime.of(10, 30),
                5,
                false,
                false,
                0,
                leg);
        PlanDocument.Day day = new PlanDocument.Day(
                0,
                LocalDate.of(2026, 12, 8),
                null,
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                List.of(visit),
                leg,
                40,
                420,
                0);
        return new PlanDocument(
                PlanDocument.SCHEMA_VERSION,
                UUID.randomUUID(),
                PlanStatus.COMPLETE,
                Strategy.OPTIMIZE,
                Instant.parse("2026-11-18T00:00:00Z"),
                "test",
                new PlanDocument.Summary(1, 0, 0, 40, 420, 0, 420, 10_000, true),
                null,
                List.of(day),
                List.of(),
                List.of(),
                List.of(),
                new PlanDocument.SolverInfo(0, 0, 0, 0, false, 1, 40));
    }
}
