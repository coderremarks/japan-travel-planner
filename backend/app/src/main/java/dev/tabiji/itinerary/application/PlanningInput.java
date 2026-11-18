package dev.tabiji.itinerary.application;

import dev.tabiji.catalog.domain.BaseLocation;
import dev.tabiji.catalog.domain.Place;
import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.shared.i18n.LocalizedText;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * An optimizer problem together with the catalogue data needed to translate its result back.
 * {@code candidates.get(i)} describes {@code problem.candidates().get(i)}.
 */
record PlanningInput(
        PlanningProblem problem,
        List<CandidateInfo> candidates,
        BaseLocation baseLocation,
        List<LocalDate> dates,
        Map<LocalDate, LocalizedText> holidays,
        boolean holidayDataComplete) {

    record CandidateInfo(Place place, boolean mandatory, boolean suggested) {}
}
