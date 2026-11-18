package dev.tabiji.itinerary.application;

import dev.tabiji.catalog.application.CatalogService;
import dev.tabiji.catalog.domain.BaseLocation;
import dev.tabiji.catalog.domain.OpeningHoursResolver;
import dev.tabiji.catalog.domain.Place;
import dev.tabiji.itinerary.application.PlanningInput.CandidateInfo;
import dev.tabiji.itinerary.domain.Itinerary;
import dev.tabiji.itinerary.domain.ItineraryStop;
import dev.tabiji.optimizer.geo.GeoPoint;
import dev.tabiji.optimizer.problem.Candidate;
import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.problem.TripDay;
import dev.tabiji.optimizer.travel.TravelMatrix;
import dev.tabiji.optimizer.travel.TravelMatrixBuilder;
import dev.tabiji.shared.i18n.LocalizedText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer between the persistence model and the optimizer: resolves places, opening
 * windows per trip date (including Japanese holiday rules), suggestions and the travel matrix, and
 * produces a self-contained {@link PlanningProblem}. Location 0 is the base; candidate {@code i} is
 * location {@code i + 1}.
 */
@Component
class PlanningInputFactory {

    /** Holiday chains can start before the trip (e.g. Golden Week), so look back this far. */
    private static final int HOLIDAY_LOOKBACK_DAYS = 14;

    private final CatalogService catalog;
    private final TravelMatrixBuilder travelMatrixBuilder;
    private final int maxSuggestions;

    PlanningInputFactory(
            CatalogService catalog,
            TravelMatrixBuilder travelMatrixBuilder,
            @Value("${app.planning.max-suggestions:12}") int maxSuggestions) {
        this.catalog = catalog;
        this.travelMatrixBuilder = travelMatrixBuilder;
        this.maxSuggestions = maxSuggestions;
    }

    PlanningInput build(Itinerary itinerary, Strategy strategy) {
        BaseLocation base = catalog.baseLocation(itinerary.baseLocationId())
                .orElseThrow(() -> new IllegalStateException("itinerary references a missing base location"));
        List<LocalDate> dates = itinerary.dates();
        Map<LocalDate, LocalizedText> holidays =
                catalog.holidaysBetween(itinerary.startDate().minusDays(HOLIDAY_LOOKBACK_DAYS), itinerary.endDate());
        boolean holidayDataComplete =
                dates.stream().map(LocalDate::getYear).distinct().allMatch(catalog::hasHolidayDataFor);

        List<CandidateInfo> candidates = new ArrayList<>();
        Map<UUID, Place> chosenById =
                catalog
                        .placesWithOpeningRules(itinerary.stops().stream()
                                .map(ItineraryStop::placeId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Place::id, Function.identity()));
        for (ItineraryStop stop : itinerary.stops()) {
            Place place = chosenById.get(stop.placeId());
            if (place != null) {
                candidates.add(new CandidateInfo(place, stop.mandatory(), false));
            }
        }
        if (itinerary.includeSuggestions() && strategy == Strategy.OPTIMIZE) {
            Set<String> areas = new LinkedHashSet<>();
            areas.add(base.areaCode());
            candidates.forEach(candidate -> areas.add(candidate.place().areaCode()));
            Set<UUID> exclude = new HashSet<>(chosenById.keySet());
            catalog.suggestions(areas, itinerary.interests(), exclude, maxSuggestions)
                    .forEach(place -> candidates.add(new CandidateInfo(place, false, true)));
        }

        List<GeoPoint> points = new ArrayList<>();
        points.add(GeoPoint.of(base.latitude(), base.longitude()));
        candidates.forEach(candidate -> points.add(
                GeoPoint.of(candidate.place().latitude(), candidate.place().longitude())));
        TravelMatrix matrix = travelMatrixBuilder.build(points, itinerary.travelModes());

        List<Candidate> problemCandidates = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            CandidateInfo info = candidates.get(i);
            Place place = info.place();
            int score = info.suggested()
                    ? CandidateScoring.suggested(place.category(), itinerary.interests())
                    : CandidateScoring.chosen(place.category(), itinerary.interests());
            List<List<TimeWindow>> hours = dates.stream()
                    .map(date -> OpeningHoursResolver.windowsOn(place, date, holidays.keySet()))
                    .toList();
            problemCandidates.add(new Candidate(
                    place.id().toString(),
                    i + 1,
                    place.visitMinutes(),
                    place.admissionYen(),
                    score,
                    info.mandatory(),
                    hours));
        }
        TimeWindow dayWindow = TimeWindow.of(itinerary.dayStartMinute(), itinerary.dayEndMinute());
        List<TripDay> days =
                dates.stream().map(date -> new TripDay(dayWindow, 0, 0)).toList();
        PlanningProblem problem = new PlanningProblem(
                problemCandidates, days, matrix, itinerary.maxVisitsPerDay(), itinerary.budgetYen());
        return new PlanningInput(problem, candidates, base, dates, holidays, holidayDataComplete);
    }
}
