package dev.tabiji.itinerary.domain;

import dev.tabiji.catalog.domain.PlaceCategory;
import dev.tabiji.optimizer.travel.TravelMode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.OptimisticLock;

/**
 * Aggregate root of a trip plan's inputs. {@code ownerId} is fixed at creation and is part of every
 * repository query, which is what makes object-level authorization structural rather than a check
 * someone could forget.
 *
 * <p>Optimistic locking: the element collections are excluded from version increments
 * ({@code @OptimisticLock(excluded = true)}) because Hibernate would otherwise bump the version a
 * second time right after inserting a new itinerary. Every change goes through {@link #apply}, which
 * also updates {@code updatedAt}, so each update still increments the version exactly once.
 */
@Entity
@Table(name = "itinerary")
public class Itinerary {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "day_start_minute", nullable = false)
    private int dayStartMinute;

    @Column(name = "day_end_minute", nullable = false)
    private int dayEndMinute;

    @Column(name = "budget_yen", nullable = false)
    private int budgetYen;

    @Column(name = "max_visits_per_day", nullable = false)
    private int maxVisitsPerDay;

    @Column(name = "base_location_id", nullable = false)
    private UUID baseLocationId;

    @Column(name = "include_suggestions", nullable = false)
    private boolean includeSuggestions;

    @ElementCollection
    @CollectionTable(name = "itinerary_interest", joinColumns = @JoinColumn(name = "itinerary_id"))
    @Column(name = "interest", nullable = false)
    @Enumerated(EnumType.STRING)
    @OptimisticLock(excluded = true)
    private Set<PlaceCategory> interests = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "itinerary_travel_mode", joinColumns = @JoinColumn(name = "itinerary_id"))
    @Column(name = "travel_mode", nullable = false)
    @Enumerated(EnumType.STRING)
    @OptimisticLock(excluded = true)
    private Set<TravelMode> travelModes = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "itinerary_stop", joinColumns = @JoinColumn(name = "itinerary_id"))
    @OrderColumn(name = "position")
    @OptimisticLock(excluded = true)
    private List<ItineraryStop> stops = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Itinerary() {
        // for JPA
    }

    private Itinerary(UUID ownerId, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.createdAt = now;
    }

    public static Itinerary create(UUID ownerId, ItineraryDraft draft, Instant now) {
        Itinerary itinerary = new Itinerary(ownerId, now);
        itinerary.apply(draft, now);
        return itinerary;
    }

    public void apply(ItineraryDraft draft, Instant now) {
        this.title = draft.title();
        this.startDate = draft.startDate();
        this.endDate = draft.endDate();
        this.dayStartMinute = draft.dayStart().getHour() * 60 + draft.dayStart().getMinute();
        this.dayEndMinute = draft.dayEnd().getHour() * 60 + draft.dayEnd().getMinute();
        this.budgetYen = draft.budgetYen();
        this.maxVisitsPerDay = draft.maxVisitsPerDay();
        this.baseLocationId = draft.baseLocationId();
        this.includeSuggestions = draft.includeSuggestions();
        this.interests.clear();
        this.interests.addAll(draft.interests());
        this.travelModes.clear();
        this.travelModes.addAll(draft.travelModes());
        this.stops.clear();
        this.stops.addAll(draft.stops());
        this.updatedAt = now;
    }

    /** A copy owned by {@code newOwnerId} (always the caller), without the generated plan. */
    public Itinerary duplicate(UUID newOwnerId, String newTitle, Instant now) {
        Itinerary copy = new Itinerary(newOwnerId, now);
        copy.apply(toDraft().withTitle(newTitle), now);
        return copy;
    }

    public ItineraryDraft toDraft() {
        return new ItineraryDraft(
                title,
                startDate,
                endDate,
                dayStart(),
                dayEnd(),
                budgetYen,
                interests,
                travelModes,
                maxVisitsPerDay,
                baseLocationId,
                includeSuggestions,
                stops);
    }

    public List<LocalDate> dates() {
        return startDate.datesUntil(endDate.plusDays(1)).toList();
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String title() {
        return title;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public LocalTime dayStart() {
        return LocalTime.of(dayStartMinute / 60, dayStartMinute % 60);
    }

    public LocalTime dayEnd() {
        return LocalTime.of(dayEndMinute / 60, dayEndMinute % 60);
    }

    public int dayStartMinute() {
        return dayStartMinute;
    }

    public int dayEndMinute() {
        return dayEndMinute;
    }

    public int budgetYen() {
        return budgetYen;
    }

    public int maxVisitsPerDay() {
        return maxVisitsPerDay;
    }

    public UUID baseLocationId() {
        return baseLocationId;
    }

    public boolean includeSuggestions() {
        return includeSuggestions;
    }

    public Set<PlaceCategory> interests() {
        return interests.isEmpty() ? EnumSet.noneOf(PlaceCategory.class) : EnumSet.copyOf(interests);
    }

    public Set<TravelMode> travelModes() {
        return travelModes.isEmpty() ? EnumSet.noneOf(TravelMode.class) : EnumSet.copyOf(travelModes);
    }

    public List<ItineraryStop> stops() {
        return List.copyOf(stops);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version == null ? 0 : version;
    }
}
