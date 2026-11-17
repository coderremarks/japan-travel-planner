package dev.tabiji.optimizer.solver;

import static dev.tabiji.optimizer.testsupport.Fixtures.NINE_TO_SIX;
import static dev.tabiji.optimizer.testsupport.Fixtures.everyDay;
import static dev.tabiji.optimizer.testsupport.Fixtures.optional;
import static dev.tabiji.optimizer.testsupport.Fixtures.problem;
import static dev.tabiji.optimizer.testsupport.Fixtures.uniformMatrix;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.solution.DaySchedule;
import dev.tabiji.optimizer.solution.PlanStatus;
import dev.tabiji.optimizer.solution.PlanTotals;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solution.ScheduledVisit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The validator guards the property tests, so it needs tests of its own: it must catch tampering. */
class PlanValidatorTest {

    private final PlanningProblem problem = problem(
            List.of(
                    optional("a", 1, 60, 3, everyDay(1, TimeWindow.between(10, 0, 12, 0))),
                    optional("b", 2, 60, 2, everyDay(1, TimeWindow.allDay()))),
            1,
            NINE_TO_SIX,
            uniformMatrix(3, 15, 100),
            4,
            10_000);

    private final PlanningResult valid = new ItineraryOptimizer().optimize(problem, Strategy.OPTIMIZE);

    @Test
    void acceptsTheOptimizersOwnPlan() {
        assertThat(valid.status()).isEqualTo(PlanStatus.COMPLETE);
        assertThat(PlanValidator.violations(problem, valid)).isEmpty();
    }

    @Test
    void detectsAVisitOutsideOpeningHours() {
        DaySchedule day = valid.days().getFirst();
        ScheduledVisit a = day.visits().stream()
                .filter(v -> v.candidateId().equals("a"))
                .findFirst()
                .orElseThrow();
        ScheduledVisit shifted =
                new ScheduledVisit("a", a.arrivalMinute(), 11 * 60 + 30, 12 * 60 + 30, a.travelFromPrevious());

        PlanningResult tampered =
                withVisits(day.visits().stream().map(v -> v == a ? shifted : v).toList());

        assertThat(PlanValidator.violations(problem, tampered)).anyMatch(v -> v.contains("outside its opening hours"));
    }

    @Test
    void detectsMissingCandidatesAndWrongTotals() {
        PlanningResult tampered = withVisits(valid.days().getFirst().visits().subList(0, 1));

        assertThat(PlanValidator.violations(problem, tampered))
                .anyMatch(v -> v.contains("neither scheduled nor reported"))
                .anyMatch(v -> v.contains("totals"));
    }

    @Test
    void detectsBudgetViolations() {
        PlanningProblem stingy = new PlanningProblem(
                problem.candidates(), problem.days(), problem.travel(), problem.maxVisitsPerDay(), 50);
        assertThat(PlanValidator.violations(stingy, valid)).anyMatch(v -> v.contains("exceeds budget"));
    }

    private PlanningResult withVisits(List<ScheduledVisit> visits) {
        DaySchedule day = valid.days().getFirst();
        DaySchedule changed = new DaySchedule(
                0,
                day.departureMinute(),
                visits,
                day.returnLeg(),
                day.returnArrivalMinute(),
                day.travelMinutes(),
                day.transportCostYen(),
                day.admissionYen());
        PlanTotals totals = valid.totals();
        return new PlanningResult(valid.status(), List.of(changed), valid.unscheduled(), totals, valid.stats());
    }
}
