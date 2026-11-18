package dev.tabiji.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.tabiji.itinerary.domain.Itinerary;
import dev.tabiji.itinerary.infrastructure.ItineraryPlanRepository;
import dev.tabiji.optimizer.problem.Candidate;
import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.problem.TripDay;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solver.ItineraryOptimizer;
import dev.tabiji.optimizer.solver.PlanValidator;
import dev.tabiji.optimizer.travel.Leg;
import dev.tabiji.optimizer.travel.LegSource;
import dev.tabiji.optimizer.travel.TravelMatrix;
import dev.tabiji.optimizer.travel.TravelMode;
import dev.tabiji.security.ratelimit.RateLimiter;
import dev.tabiji.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The application's last line of defence: whatever the optimizer returns is re-checked by the
 * independent {@link PlanValidator}, and an invalid plan is neither stored nor shown. The real
 * optimizer never produces one (see the optimizer's property tests), so a Mockito stand-in has to
 * simulate the bug.
 */
class PlanningServiceTest {

    private final ItineraryService itineraries = mock(ItineraryService.class);
    private final ItineraryPlanRepository plans = mock(ItineraryPlanRepository.class);
    private final PlanningInputFactory inputs = mock(PlanningInputFactory.class);
    private final PlanAssembler assembler = mock(PlanAssembler.class);
    private final ItineraryOptimizer optimizer = mock(ItineraryOptimizer.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final PlanningService service = new PlanningService(
            itineraries,
            plans,
            inputs,
            assembler,
            optimizer,
            rateLimiter,
            JsonMapper.builder().build(),
            Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneOffset.UTC));

    private final UUID owner = UUID.randomUUID();
    private final UUID itineraryId = UUID.randomUUID();

    @Test
    void anInvalidPlanFromTheOptimizerIsRefusedAndNotStored() {
        Itinerary itinerary = mock(Itinerary.class);
        when(itineraries.load(owner, itineraryId)).thenReturn(itinerary);
        // The museum the traveller chose is open only from 13:00 to 14:00 ...
        PlanningProblem actual = museumOpen(TimeWindow.between(13, 0, 14, 0));
        when(inputs.build(itinerary, Strategy.OPTIMIZE)).thenReturn(inputFor(actual));
        // ... but the "optimizer" returns a plan made for a museum open all day: a simulated solver bug.
        PlanningResult wrong =
                new ItineraryOptimizer().optimize(museumOpen(TimeWindow.between(9, 0, 17, 0)), Strategy.OPTIMIZE);
        when(optimizer.optimize(actual, Strategy.OPTIMIZE)).thenReturn(wrong);
        assertThat(PlanValidator.violations(actual, wrong)).isNotEmpty();

        assertThatThrownBy(() -> service.plan(owner, itineraryId, Strategy.OPTIMIZE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid plan");
        verify(plans, never()).save(any());
        verifyNoInteractions(assembler);
    }

    @Test
    void theExponentialExactSolverIsRefusedBeforeAnyWorkIsDone() {
        assertThatThrownBy(() -> service.plan(owner, itineraryId, Strategy.EXACT))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(itineraries, inputs, optimizer, rateLimiter, plans);
    }

    private static PlanningInput inputFor(PlanningProblem problem) {
        return new PlanningInput(
                problem, List.of(new PlanningInput.CandidateInfo(null, false, false)), null, List.of(), Map.of(), true);
    }

    /** A hotel (location 0) and one museum (location 1, 45-minute visit) ten minutes' walk apart. */
    private static PlanningProblem museumOpen(TimeWindow openingHours) {
        TravelMatrix travel = TravelMatrix.builder(2)
                .set(0, 1, Leg.estimate(TravelMode.WALK, 10, 0, 750, LegSource.DISTANCE_MODEL))
                .set(1, 0, Leg.estimate(TravelMode.WALK, 10, 0, 750, LegSource.DISTANCE_MODEL))
                .build();
        Candidate museum = new Candidate("museum", 1, 45, 0, 1_000, false, List.of(List.of(openingHours)));
        TripDay day = new TripDay(TimeWindow.between(9, 0, 17, 0), 0, 0);
        return new PlanningProblem(List.of(museum), List.of(day), travel, 4, PlanningProblem.UNLIMITED_BUDGET);
    }
}
