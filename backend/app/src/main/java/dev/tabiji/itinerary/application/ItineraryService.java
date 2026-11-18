package dev.tabiji.itinerary.application;

import dev.tabiji.catalog.application.CatalogService;
import dev.tabiji.catalog.domain.BaseLocation;
import dev.tabiji.catalog.domain.Place;
import dev.tabiji.itinerary.domain.InputFingerprint;
import dev.tabiji.itinerary.domain.Itinerary;
import dev.tabiji.itinerary.domain.ItineraryDraft;
import dev.tabiji.itinerary.domain.ItineraryPlan;
import dev.tabiji.itinerary.domain.ItineraryStop;
import dev.tabiji.itinerary.infrastructure.ItineraryPlanRepository;
import dev.tabiji.itinerary.infrastructure.ItineraryRepository;
import dev.tabiji.shared.error.ApiException;
import dev.tabiji.shared.error.ErrorCode;
import dev.tabiji.shared.error.FieldViolation;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases on itineraries. Every method takes the caller's user ID and resolves itineraries only
 * through {@link #load}, which filters by owner — a foreign or unknown ID yields the same 404, so the
 * API is not an oracle for which IDs exist (OWASP API1: Broken Object Level Authorization).
 */
@Service
@Transactional
public class ItineraryService {

    static final int MAX_ITINERARIES_PER_USER = 100;

    private static final Logger securityLog = LoggerFactory.getLogger("security");

    private final ItineraryRepository itineraries;
    private final ItineraryPlanRepository plans;
    private final CatalogService catalog;
    private final Clock clock;

    public ItineraryService(
            ItineraryRepository itineraries, ItineraryPlanRepository plans, CatalogService catalog, Clock clock) {
        this.itineraries = itineraries;
        this.plans = plans;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Read model for one itinerary: the aggregate plus what is needed to render it. */
    public record ItineraryView(
            Itinerary itinerary, BaseLocation baseLocation, List<Place> stopPlaces, Optional<PlanState> plan) {

        public ItineraryView {
            stopPlaces = List.copyOf(stopPlaces);
        }
    }

    /** Summary of the stored plan; {@code stale} when the inputs changed since it was generated. */
    public record PlanState(dev.tabiji.optimizer.solution.PlanStatus status, boolean stale) {}

    public record ItinerarySummary(Itinerary itinerary, int stopCount, Optional<PlanState> plan) {}

    @Transactional(readOnly = true)
    public Page<ItinerarySummary> list(UUID ownerId, int page, int size) {
        Page<Itinerary> result = itineraries.findByOwnerId(
                ownerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
        Map<UUID, ItineraryPlan> plansById =
                plans
                        .findByItineraryIdIn(
                                result.getContent().stream().map(Itinerary::id).toList())
                        .stream()
                        .collect(Collectors.toMap(ItineraryPlan::itineraryId, Function.identity()));
        return result.map(itinerary -> new ItinerarySummary(
                itinerary,
                itinerary.stops().size(),
                planState(itinerary, Optional.ofNullable(plansById.get(itinerary.id())))));
    }

    @Transactional(readOnly = true)
    public ItineraryView get(UUID ownerId, UUID itineraryId) {
        return view(load(ownerId, itineraryId));
    }

    public ItineraryView create(UUID ownerId, ItineraryDraft draft) {
        if (itineraries.countByOwnerId(ownerId) >= MAX_ITINERARIES_PER_USER) {
            throw ApiException.validation(List.of(new FieldViolation("itinerary", "LIMIT_REACHED")));
        }
        validate(draft);
        Itinerary itinerary = itineraries.save(Itinerary.create(ownerId, draft, clock.instant()));
        return view(itinerary);
    }

    /**
     * Optimistic locking: the client sends the version it edited. A mismatch means someone else
     * (another tab) saved in between, and the client must reload instead of silently overwriting.
     */
    public ItineraryView update(UUID ownerId, UUID itineraryId, ItineraryDraft draft, long expectedVersion) {
        Itinerary itinerary = load(ownerId, itineraryId);
        if (itinerary.version() != expectedVersion) {
            throw new OptimisticLockingFailureException("itinerary version mismatch");
        }
        validate(draft);
        itinerary.apply(draft, clock.instant());
        return view(itineraries.saveAndFlush(itinerary));
    }

    public void delete(UUID ownerId, UUID itineraryId) {
        Itinerary itinerary = load(ownerId, itineraryId);
        plans.findById(itinerary.id()).ifPresent(plans::delete);
        itineraries.delete(itinerary);
    }

    public ItineraryView duplicate(UUID ownerId, UUID itineraryId) {
        Itinerary source = load(ownerId, itineraryId);
        if (itineraries.countByOwnerId(ownerId) >= MAX_ITINERARIES_PER_USER) {
            throw ApiException.validation(List.of(new FieldViolation("itinerary", "LIMIT_REACHED")));
        }
        String title = truncate(source.title() + " (copy)", 120);
        return view(itineraries.save(source.duplicate(ownerId, title, clock.instant())));
    }

    /**
     * The single entry point for resolving an itinerary on behalf of a user. Attempts to reach an
     * existing itinerary of another user are logged as security events and answered exactly like a
     * non-existent ID.
     */
    @Transactional(readOnly = true)
    public Itinerary load(UUID ownerId, UUID itineraryId) {
        return itineraries.findByIdAndOwnerId(itineraryId, ownerId).orElseThrow(() -> {
            if (itineraries.existsById(itineraryId)) {
                securityLog.warn("itinerary.access-denied userId={} itineraryId={}", ownerId, itineraryId);
            }
            return new ApiException(ErrorCode.ITINERARY_NOT_FOUND);
        });
    }

    Optional<PlanState> planState(Itinerary itinerary, Optional<ItineraryPlan> plan) {
        return plan.map(p -> new PlanState(p.status(), !p.inputFingerprint().equals(InputFingerprint.of(itinerary))));
    }

    /** Builds the read model inside the transaction, so no lazy collection is touched after it closes. */
    private ItineraryView view(Itinerary itinerary) {
        itinerary.interests();
        itinerary.travelModes();
        BaseLocation base = catalog.baseLocation(itinerary.baseLocationId())
                .orElseThrow(() -> new IllegalStateException("itinerary references a missing base location"));
        Map<UUID, Place> placesById =
                catalog
                        .placesWithOpeningRules(itinerary.stops().stream()
                                .map(ItineraryStop::placeId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Place::id, Function.identity()));
        List<Place> stopPlaces = new ArrayList<>();
        for (ItineraryStop stop : itinerary.stops()) {
            stopPlaces.add(placesById.get(stop.placeId()));
        }
        return new ItineraryView(itinerary, base, stopPlaces, planState(itinerary, plans.findById(itinerary.id())));
    }

    private void validate(ItineraryDraft draft) {
        List<FieldViolation> violations = new ArrayList<>(draft.violations());
        if (catalog.baseLocation(draft.baseLocationId()).isEmpty()) {
            violations.add(new FieldViolation("baseLocationId", "UNKNOWN_BASE_LOCATION"));
        }
        List<UUID> placeIds = draft.stops().stream().map(ItineraryStop::placeId).toList();
        if (!placeIds.isEmpty()) {
            var known = catalog.placesWithOpeningRules(placeIds).stream()
                    .map(Place::id)
                    .collect(Collectors.toSet());
            for (int i = 0; i < placeIds.size(); i++) {
                if (!known.contains(placeIds.get(i))) {
                    violations.add(new FieldViolation("stops[" + i + "].placeId", "UNKNOWN_PLACE"));
                }
            }
        }
        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
