package dev.tabiji.itinerary.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.tabiji.catalog.api.CatalogDtos.BaseLocationResponse;
import dev.tabiji.catalog.api.CatalogDtos.PlaceSummaryResponse;
import dev.tabiji.catalog.domain.PlaceCategory;
import dev.tabiji.itinerary.application.ItineraryService.ItinerarySummary;
import dev.tabiji.itinerary.application.ItineraryService.ItineraryView;
import dev.tabiji.itinerary.application.ItineraryService.PlanState;
import dev.tabiji.itinerary.domain.Itinerary;
import dev.tabiji.itinerary.domain.ItineraryDraft;
import dev.tabiji.itinerary.domain.ItineraryStop;
import dev.tabiji.itinerary.domain.PlanDocument;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.solution.PlanStatus;
import dev.tabiji.optimizer.travel.TravelMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Request and response bodies of the itinerary endpoints. The request record lists exactly the
 * fields a traveller may edit; {@code id}, {@code ownerId}, timestamps and {@code version} are not
 * part of it, and unknown JSON fields are rejected globally (mass-assignment protection).
 */
public final class ItineraryDtos {

    private ItineraryDtos() {}

    public record StopRequest(@NotNull UUID placeId, boolean mandatory) {}

    public record ItineraryRequest(
            @NotBlank @Size(max = 120) @Pattern(regexp = "[^\\p{Cntrl}]*") String title,

            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime dayStart,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime dayEnd,

            @NotNull @Min(0) @Max(ItineraryDraft.MAX_BUDGET_YEN) Integer budgetYen,

            @NotNull @Size(max = 6) Set<PlaceCategory> interests,
            @NotNull @Size(min = 1, max = 3) Set<TravelMode> travelModes,

            @NotNull @Min(1) @Max(ItineraryDraft.MAX_VISITS_PER_DAY) Integer maxVisitsPerDay,

            @NotNull UUID baseLocationId,
            boolean includeSuggestions,
            @NotNull @Size(max = ItineraryDraft.MAX_STOPS) List<@Valid @NotNull StopRequest> stops) {

        /** Null-tolerant copies: nulls must reach Bean Validation to become field errors. */
        public ItineraryRequest {
            interests = interests == null ? null : Set.copyOf(interests);
            travelModes = travelModes == null ? null : Set.copyOf(travelModes);
            stops = stops == null ? null : Collections.unmodifiableList(new ArrayList<>(stops));
        }

        ItineraryDraft toDraft() {
            return new ItineraryDraft(
                    title,
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
                    stops.stream()
                            .map(stop -> new ItineraryStop(stop.placeId(), stop.mandatory()))
                            .toList());
        }
    }

    /** Strategies a client may request; the exponential exact solver is deliberately not exposed. */
    public enum PlanStrategy {
        OPTIMIZE(Strategy.OPTIMIZE),
        KEEP_ORDER(Strategy.KEEP_ORDER);

        private final Strategy strategy;

        PlanStrategy(Strategy strategy) {
            this.strategy = strategy;
        }

        Strategy strategy() {
            return strategy;
        }
    }

    public record PlanRequest(@NotNull PlanStrategy strategy) {}

    public record PlanStateResponse(PlanStatus status, boolean stale) {

        static PlanStateResponse from(PlanState state) {
            return new PlanStateResponse(state.status(), state.stale());
        }
    }

    public record StopResponse(PlaceSummaryResponse place, boolean mandatory) {}

    public record ItineraryResponse(
            UUID id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            @JsonFormat(pattern = "HH:mm") LocalTime dayStart,
            @JsonFormat(pattern = "HH:mm") LocalTime dayEnd,
            int budgetYen,
            List<PlaceCategory> interests,
            List<TravelMode> travelModes,
            int maxVisitsPerDay,
            BaseLocationResponse baseLocation,
            boolean includeSuggestions,
            List<StopResponse> stops,
            PlanStateResponse plan,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        public ItineraryResponse {
            interests = List.copyOf(interests);
            travelModes = List.copyOf(travelModes);
            stops = List.copyOf(stops);
        }

        static ItineraryResponse from(ItineraryView view) {
            Itinerary itinerary = view.itinerary();
            List<StopResponse> stops = new ArrayList<>();
            List<ItineraryStop> itineraryStops = itinerary.stops();
            for (int i = 0; i < itineraryStops.size(); i++) {
                stops.add(new StopResponse(
                        PlaceSummaryResponse.from(view.stopPlaces().get(i)),
                        itineraryStops.get(i).mandatory()));
            }
            return new ItineraryResponse(
                    itinerary.id(),
                    itinerary.title(),
                    itinerary.startDate(),
                    itinerary.endDate(),
                    itinerary.dayStart(),
                    itinerary.dayEnd(),
                    itinerary.budgetYen(),
                    itinerary.interests().stream().sorted().toList(),
                    itinerary.travelModes().stream().sorted().toList(),
                    itinerary.maxVisitsPerDay(),
                    BaseLocationResponse.from(view.baseLocation()),
                    itinerary.includeSuggestions(),
                    stops,
                    view.plan().map(PlanStateResponse::from).orElse(null),
                    itinerary.createdAt(),
                    itinerary.updatedAt(),
                    itinerary.version());
        }
    }

    public record ItinerarySummaryResponse(
            UUID id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            int stopCount,
            PlanStateResponse plan,
            Instant updatedAt) {

        static ItinerarySummaryResponse from(ItinerarySummary summary) {
            Itinerary itinerary = summary.itinerary();
            return new ItinerarySummaryResponse(
                    itinerary.id(),
                    itinerary.title(),
                    itinerary.startDate(),
                    itinerary.endDate(),
                    summary.stopCount(),
                    summary.plan().map(PlanStateResponse::from).orElse(null),
                    itinerary.updatedAt());
        }
    }

    public record PlanResponse(PlanDocument plan, boolean stale) {}

    /** Portable JSON export of an itinerary and (if generated) its plan. */
    public record ItineraryExport(
            int schemaVersion, Instant exportedAt, ItineraryResponse itinerary, PlanDocument plan) {}
}
