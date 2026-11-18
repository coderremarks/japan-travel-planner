package dev.tabiji.itinerary.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.tabiji.catalog.domain.PlaceCategory;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.solution.Hint;
import dev.tabiji.optimizer.solution.PlanStatus;
import dev.tabiji.optimizer.solution.UnscheduledReason;
import dev.tabiji.optimizer.travel.LegSource;
import dev.tabiji.optimizer.travel.TravelMode;
import dev.tabiji.shared.i18n.LocalizedText;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * A generated plan as stored (JSONB) and returned by the API. Self-contained: names and coordinates
 * are copied in, so a stored plan renders without catalogue lookups and does not change if the
 * catalogue does. {@code schemaVersion} allows the format to evolve.
 */
public record PlanDocument(
        int schemaVersion,
        UUID itineraryId,
        PlanStatus status,
        Strategy strategy,
        Instant generatedAt,
        String algorithmVersion,
        Summary summary,
        Comparison comparison,
        List<Day> days,
        List<Unscheduled> unscheduled,
        List<Hint> hints,
        List<Notice> notices,
        SolverInfo solver) {

    public static final int SCHEMA_VERSION = 1;

    public PlanDocument {
        days = List.copyOf(days);
        unscheduled = List.copyOf(unscheduled);
        hints = List.copyOf(hints);
        notices = List.copyOf(notices);
    }

    /** Facts the UI must show next to the plan. */
    public enum Notice {
        /** Travel times and fares are estimates (always true for the bundled providers). */
        ESTIMATED_TRAVEL,
        /** Some places use approximate sample data; verify before travelling. */
        SAMPLE_CATALOG_DATA,
        /** No holiday data for (part of) the trip, so holiday opening rules could not be applied. */
        HOLIDAY_DATA_UNAVAILABLE,
        /** The search hit its safety time limit; the plan is valid but may be improvable. */
        SEARCH_TIME_LIMIT_REACHED
    }

    public record Summary(
            int scheduledCount,
            int mandatoryScheduled,
            int mandatoryTotal,
            int travelMinutes,
            long transportCostYen,
            long admissionYen,
            long totalCostYen,
            int budgetYen,
            boolean withinBudget) {}

    /** The same problem solved in the traveller's own order ("keep my order"), for comparison. */
    public record Comparison(
            int baselineScheduledCount, int baselineTravelMinutes, int additionalPlaces, int travelMinutesSaved) {}

    public record Day(
            int dayIndex,
            LocalDate date,
            LocalizedText holiday,
            @JsonFormat(pattern = "HH:mm") LocalTime departure,
            @JsonFormat(pattern = "HH:mm") LocalTime returnArrival,
            List<Visit> visits,
            Leg returnLeg,
            int travelMinutes,
            long transportCostYen,
            long admissionYen) {

        public Day {
            visits = List.copyOf(visits);
        }
    }

    public record Visit(
            UUID placeId,
            String slug,
            LocalizedText name,
            PlaceCategory category,
            double lat,
            double lng,
            @JsonFormat(pattern = "HH:mm") LocalTime arrival,
            @JsonFormat(pattern = "HH:mm") LocalTime start,
            @JsonFormat(pattern = "HH:mm") LocalTime end,
            int waitMinutes,
            boolean mandatory,
            boolean suggested,
            int admissionYen,
            Leg travel) {}

    public record Leg(
            TravelMode mode,
            int minutes,
            int costYen,
            int distanceMeters,
            LegSource source,
            boolean estimated,
            List<LocalizedText> via) {

        public Leg {
            via = List.copyOf(via);
        }
    }

    public record Unscheduled(
            UUID placeId,
            String slug,
            LocalizedText name,
            boolean mandatory,
            boolean suggested,
            UnscheduledReason reason) {}

    public record SolverInfo(
            int iterations,
            int improvingMoves,
            long elapsedMillis,
            long seed,
            boolean timeLimitReached,
            int constructionScheduled,
            int constructionTravelMinutes) {}
}
