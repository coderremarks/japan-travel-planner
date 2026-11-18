package dev.tabiji.itinerary.application;

import dev.tabiji.catalog.domain.DataSource;
import dev.tabiji.catalog.domain.Place;
import dev.tabiji.itinerary.application.PlanningInput.CandidateInfo;
import dev.tabiji.itinerary.domain.PlanDocument;
import dev.tabiji.itinerary.domain.PlanDocument.Comparison;
import dev.tabiji.itinerary.domain.PlanDocument.Day;
import dev.tabiji.itinerary.domain.PlanDocument.Notice;
import dev.tabiji.itinerary.domain.PlanDocument.SolverInfo;
import dev.tabiji.itinerary.domain.PlanDocument.Summary;
import dev.tabiji.itinerary.domain.PlanDocument.Unscheduled;
import dev.tabiji.itinerary.domain.PlanDocument.Visit;
import dev.tabiji.optimizer.solution.DaySchedule;
import dev.tabiji.optimizer.solution.Hint;
import dev.tabiji.optimizer.solution.PlanTotals;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solution.ScheduledVisit;
import dev.tabiji.optimizer.solution.UnscheduledCandidate;
import dev.tabiji.optimizer.travel.Leg;
import dev.tabiji.shared.i18n.LocalizedText;
import dev.tabiji.transport.TransitLineNames;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Translates an optimizer {@link PlanningResult} into the self-contained {@link PlanDocument}. */
@Component
class PlanAssembler {

    private final TransitLineNames lineNames;

    PlanAssembler(TransitLineNames lineNames) {
        this.lineNames = lineNames;
    }

    PlanDocument assemble(
            UUID itineraryId,
            PlanningInput input,
            PlanningResult result,
            PlanningResult baseline,
            Instant generatedAt,
            String algorithmVersion) {
        Map<String, CandidateInfo> byId = new HashMap<>();
        input.candidates().forEach(candidate -> byId.put(candidate.place().id().toString(), candidate));

        List<Day> days = new ArrayList<>();
        for (DaySchedule schedule : result.days()) {
            List<Visit> visits = schedule.visits().stream()
                    .map(visit -> toVisit(visit, byId.get(visit.candidateId())))
                    .toList();
            var date = input.dates().get(schedule.dayIndex());
            days.add(new Day(
                    schedule.dayIndex(),
                    date,
                    input.holidays().get(date),
                    time(schedule.departureMinute()),
                    time(schedule.returnArrivalMinute()),
                    visits,
                    toLeg(schedule.returnLeg()),
                    schedule.travelMinutes(),
                    schedule.transportCostYen(),
                    schedule.admissionYen()));
        }

        List<Unscheduled> unscheduled = new ArrayList<>();
        Set<Hint> hints = EnumSet.noneOf(Hint.class);
        for (UnscheduledCandidate entry : result.unscheduled()) {
            CandidateInfo info = byId.get(entry.candidateId());
            Place place = info.place();
            unscheduled.add(new Unscheduled(
                    place.id(), place.slug(), name(place), entry.mandatory(), info.suggested(), entry.reason()));
            if (!info.suggested()) {
                hints.add(entry.reason().hint());
            }
        }

        PlanTotals totals = result.totals();
        Summary summary = new Summary(
                totals.scheduledCount(),
                totals.mandatoryScheduled(),
                totals.mandatoryTotal(),
                totals.travelMinutes(),
                totals.transportCostYen(),
                totals.admissionYen(),
                totals.totalCostYen(),
                (int) input.problem().budgetYen(),
                totals.totalCostYen() <= input.problem().budgetYen());
        Comparison comparison = baseline == null
                ? null
                : new Comparison(
                        baseline.totals().scheduledCount(),
                        baseline.totals().travelMinutes(),
                        totals.scheduledCount() - baseline.totals().scheduledCount(),
                        baseline.totals().travelMinutes() - totals.travelMinutes());

        var stats = result.stats();
        SolverInfo solver = new SolverInfo(
                stats.iterations(),
                stats.improvingMoves(),
                stats.elapsedMillis(),
                stats.seed(),
                stats.timeLimitReached(),
                stats.constructionScheduled(),
                stats.constructionTravelMinutes());

        return new PlanDocument(
                PlanDocument.SCHEMA_VERSION,
                itineraryId,
                result.status(),
                stats.strategy(),
                generatedAt,
                algorithmVersion,
                summary,
                comparison,
                days,
                unscheduled,
                List.copyOf(hints),
                notices(input, result),
                solver);
    }

    private Visit toVisit(ScheduledVisit visit, CandidateInfo info) {
        Place place = info.place();
        return new Visit(
                place.id(),
                place.slug(),
                name(place),
                place.category(),
                place.latitude(),
                place.longitude(),
                time(visit.arrivalMinute()),
                time(visit.startMinute()),
                time(visit.endMinute()),
                visit.waitMinutes(),
                info.mandatory(),
                info.suggested(),
                place.admissionYen(),
                toLeg(visit.travelFromPrevious()));
    }

    private PlanDocument.Leg toLeg(Leg leg) {
        return new PlanDocument.Leg(
                leg.mode(),
                leg.minutes(),
                leg.costYen(),
                leg.distanceMeters(),
                leg.source(),
                leg.estimated(),
                lineNames.resolve(leg.via()));
    }

    private static List<Notice> notices(PlanningInput input, PlanningResult result) {
        List<Notice> notices = new ArrayList<>();
        boolean anyEstimate = result.days().stream()
                .anyMatch(day -> day.returnLeg().estimated()
                        || day.visits().stream()
                                .anyMatch(v -> v.travelFromPrevious().estimated()));
        if (anyEstimate) {
            notices.add(Notice.ESTIMATED_TRAVEL);
        }
        if (input.candidates().stream().anyMatch(c -> c.place().dataSource() == DataSource.SAMPLE_UNVERIFIED)) {
            notices.add(Notice.SAMPLE_CATALOG_DATA);
        }
        if (!input.holidayDataComplete()) {
            notices.add(Notice.HOLIDAY_DATA_UNAVAILABLE);
        }
        if (result.stats().timeLimitReached()) {
            notices.add(Notice.SEARCH_TIME_LIMIT_REACHED);
        }
        return notices;
    }

    private static LocalizedText name(Place place) {
        return LocalizedText.of(place.nameEn(), place.nameJa());
    }

    /** Day windows end at 23:59 at the latest, so every plan time fits a {@link LocalTime}. */
    private static LocalTime time(int minuteOfDay) {
        return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
    }
}
