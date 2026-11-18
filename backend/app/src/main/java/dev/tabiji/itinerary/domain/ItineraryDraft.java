package dev.tabiji.itinerary.domain;

import dev.tabiji.catalog.domain.PlaceCategory;
import dev.tabiji.optimizer.travel.TravelMode;
import dev.tabiji.shared.error.FieldViolation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a traveller may edit on an itinerary, as one value. The service builds it from the
 * request DTO, checks it with {@link #violations()} and only then applies it to the aggregate — so
 * ownership, IDs, timestamps and versions can never be set from outside.
 */
public record ItineraryDraft(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime dayStart,
        LocalTime dayEnd,
        int budgetYen,
        Set<PlaceCategory> interests,
        Set<TravelMode> travelModes,
        int maxVisitsPerDay,
        UUID baseLocationId,
        boolean includeSuggestions,
        List<ItineraryStop> stops) {

    public static final int MAX_DAYS = 14;
    public static final int MAX_STOPS = 50;
    public static final int MAX_VISITS_PER_DAY = 12;
    public static final int MAX_BUDGET_YEN = 10_000_000;
    public static final int MIN_DAY_MINUTES = 60;

    public ItineraryDraft {
        title = title == null ? "" : title.strip();
        interests = Set.copyOf(interests);
        travelModes = Set.copyOf(travelModes);
        stops = List.copyOf(stops);
    }

    public ItineraryDraft withTitle(String newTitle) {
        return new ItineraryDraft(
                newTitle,
                startDate,
                endDate,
                dayStart,
                dayEnd,
                budgetYen,
                interests,
                travelModes,
                maxVisitsPerDay,
                baseLocationId,
                includeSuggestions,
                stops);
    }

    /** Cross-field rules that Bean Validation on single fields cannot express. */
    public List<FieldViolation> violations() {
        List<FieldViolation> violations = new ArrayList<>();
        if (title.isEmpty()) {
            violations.add(new FieldViolation("title", "NotBlank"));
        }
        if (endDate.isBefore(startDate)) {
            violations.add(new FieldViolation("endDate", "END_BEFORE_START"));
        } else if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_DAYS) {
            violations.add(new FieldViolation("endDate", "TRIP_TOO_LONG"));
        }
        if (ChronoUnit.MINUTES.between(dayStart, dayEnd) < MIN_DAY_MINUTES) {
            violations.add(new FieldViolation("dayEnd", "DAY_TOO_SHORT"));
        }
        if (travelModes.isEmpty()) {
            violations.add(new FieldViolation("travelModes", "NotEmpty"));
        }
        if (stops.size() > MAX_STOPS) {
            violations.add(new FieldViolation("stops", "TOO_MANY_STOPS"));
        }
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < stops.size(); i++) {
            if (!seen.add(stops.get(i).placeId())) {
                violations.add(new FieldViolation("stops[" + i + "].placeId", "DUPLICATE_PLACE"));
            }
        }
        return violations;
    }
}
