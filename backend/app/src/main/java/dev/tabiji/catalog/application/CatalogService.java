package dev.tabiji.catalog.application;

import dev.tabiji.catalog.domain.Area;
import dev.tabiji.catalog.domain.BaseLocation;
import dev.tabiji.catalog.domain.JapaneseHoliday;
import dev.tabiji.catalog.domain.Place;
import dev.tabiji.catalog.domain.PlaceCategory;
import dev.tabiji.catalog.infrastructure.AreaRepository;
import dev.tabiji.catalog.infrastructure.BaseLocationRepository;
import dev.tabiji.catalog.infrastructure.JapaneseHolidayRepository;
import dev.tabiji.catalog.infrastructure.PlaceRepository;
import dev.tabiji.catalog.infrastructure.PlaceSpecifications;
import dev.tabiji.shared.error.ApiException;
import dev.tabiji.shared.error.ErrorCode;
import dev.tabiji.shared.i18n.LocalizedText;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only access to the catalogue for the API and for itinerary planning. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final Sort BY_NAME = Sort.by("nameEn", "id");

    private final AreaRepository areas;
    private final BaseLocationRepository baseLocations;
    private final PlaceRepository places;
    private final JapaneseHolidayRepository holidays;

    public CatalogService(
            AreaRepository areas,
            BaseLocationRepository baseLocations,
            PlaceRepository places,
            JapaneseHolidayRepository holidays) {
        this.areas = areas;
        this.baseLocations = baseLocations;
        this.places = places;
        this.holidays = holidays;
    }

    public List<Area> areas() {
        return areas.findAllByOrderBySortOrderAsc();
    }

    public List<BaseLocation> baseLocations(String areaCode) {
        return areaCode == null
                ? baseLocations.findAllByOrderByAreaCodeAscNameEnAsc()
                : baseLocations.findByAreaCodeOrderByNameEnAsc(areaCode);
    }

    public Optional<BaseLocation> baseLocation(UUID id) {
        return baseLocations.findById(id);
    }

    public Page<Place> search(String areaCode, PlaceCategory category, String text, int page, int size) {
        Specification<Place> specification = (root, query, cb) -> cb.conjunction();
        if (areaCode != null) {
            specification = specification.and(PlaceSpecifications.inArea(areaCode));
        }
        if (category != null) {
            specification = specification.and(PlaceSpecifications.inCategory(category));
        }
        if (text != null && !text.isBlank()) {
            specification = specification.and(PlaceSpecifications.nameContains(text));
        }
        return places.findAll(specification, PageRequest.of(page, size, BY_NAME));
    }

    /** A place with its opening rules initialised (the API maps it after the transaction ends). */
    public Place place(UUID id) {
        Place place = places.findById(id).orElseThrow(() -> new ApiException(ErrorCode.PLACE_NOT_FOUND));
        place.openingHours();
        place.closures();
        return place;
    }

    /** Places with their opening hours and closures initialised (batch-fetched, no N+1). */
    public List<Place> placesWithOpeningRules(Collection<UUID> ids) {
        List<Place> found = places.findByIdIn(ids);
        found.forEach(place -> {
            place.openingHours();
            place.closures();
        });
        return found;
    }

    /**
     * Places matching the traveller's interests in the given areas, excluding ones already chosen. The
     * sample catalogue has no popularity data, so suggestions are ordered by name — deterministic, and
     * honest about not knowing what is "best".
     */
    public List<Place> suggestions(Set<String> areaCodes, Set<PlaceCategory> interests, Set<UUID> exclude, int limit) {
        if (areaCodes.isEmpty() || interests.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<Place> matches = places.findByAreaCodeInAndCategoryIn(areaCodes, interests, BY_NAME).stream()
                .filter(place -> !exclude.contains(place.id()))
                .limit(limit)
                .toList();
        matches.forEach(place -> {
            place.openingHours();
            place.closures();
        });
        return matches;
    }

    /** National holidays in {@code [from, to]} with their bilingual names. */
    public Map<LocalDate, LocalizedText> holidaysBetween(LocalDate from, LocalDate to) {
        return holidays.findByDateBetween(from, to).stream()
                .collect(Collectors.toUnmodifiableMap(
                        JapaneseHoliday::date, holiday -> LocalizedText.of(holiday.nameEn(), holiday.nameJa())));
    }

    /** True if the holiday table covers the whole calendar year (official data is published per year). */
    public boolean hasHolidayDataFor(int year) {
        return !holidays.findByDateBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .isEmpty();
    }
}
