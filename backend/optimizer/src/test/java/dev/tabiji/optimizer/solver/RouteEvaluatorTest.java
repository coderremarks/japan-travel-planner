package dev.tabiji.optimizer.solver;

import static dev.tabiji.optimizer.testsupport.Fixtures.everyDay;
import static dev.tabiji.optimizer.testsupport.Fixtures.leg;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.optimizer.problem.Candidate;
import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.problem.TripDay;
import dev.tabiji.optimizer.solution.DaySchedule;
import dev.tabiji.optimizer.travel.TravelMatrix;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hand-computed schedules. Hotel = 0, A = 1, B = 2.
 * Legs: hotel–A 10 min/¥0, A–B 20 min/¥200, hotel–B 25 min/¥200. Day 09:00–18:00.
 * A: 60 min, open 10:00–17:00. B: 30 min, open 09:00–12:00.
 */
class RouteEvaluatorTest {

    private static final int A = 0;
    private static final int B = 1;

    private static TravelMatrix matrix() {
        return TravelMatrix.builder(3)
                .set(0, 1, leg(10, 0))
                .set(1, 0, leg(10, 0))
                .set(1, 2, leg(20, 200))
                .set(2, 1, leg(20, 200))
                .set(0, 2, leg(25, 200))
                .set(2, 0, leg(25, 200))
                .build();
    }

    private static RouteEvaluator evaluator(int maxVisits, TimeWindow day, int visitMinutesOfB) {
        PlanningProblem problem = new PlanningProblem(
                List.of(
                        new Candidate("A", 1, 60, 500, 3, false, everyDay(1, TimeWindow.between(10, 0, 17, 0))),
                        new Candidate(
                                "B", 2, visitMinutesOfB, 0, 2, true, everyDay(1, TimeWindow.between(9, 0, 12, 0)))),
                List.of(new TripDay(day, 0, 0)),
                matrix(),
                maxVisits,
                PlanningProblem.UNLIMITED_BUDGET);
        return new RouteEvaluator(new ProblemIndex(problem));
    }

    @Test
    void waitsForOpeningAndAddsUpTravelCostAndScore() {
        RouteEvaluator evaluator = evaluator(5, TimeWindow.between(9, 0, 18, 0), 30);

        RouteEvaluation evaluation = evaluator.evaluate(0, new int[] {A, B});

        // 09:00 depart, 09:10 arrive A, wait to 10:00, 11:00 leave, 11:20 B, 11:50 leave, 12:15 hotel.
        assertThat(evaluation.feasible()).isTrue();
        assertThat(evaluation.returnArrivalMinute()).isEqualTo(12 * 60 + 15);
        assertThat(evaluation.travelMinutes()).isEqualTo(55);
        assertThat(evaluation.transportCostYen()).isEqualTo(400);
        assertThat(evaluation.admissionYen()).isEqualTo(500);
        assertThat(evaluation.score()).isEqualTo(5);
        assertThat(evaluation.mandatoryCount()).isEqualTo(1);

        DaySchedule schedule = evaluator.describe(0, new int[] {A, B});
        assertThat(schedule.visits().getFirst().arrivalMinute()).isEqualTo(9 * 60 + 10);
        assertThat(schedule.visits().getFirst().startMinute()).isEqualTo(10 * 60);
        assertThat(schedule.visits().getFirst().waitMinutes()).isEqualTo(50);
        assertThat(schedule.visits().get(1).startMinute()).isEqualTo(11 * 60 + 20);
        assertThat(schedule.returnLeg().minutes()).isEqualTo(25);
    }

    @Test
    void rejectsVisitsThatCannotFinishBeforeClosing() {
        RouteEvaluator evaluator = evaluator(5, TimeWindow.between(9, 0, 18, 0), 45);

        // B would start at 11:20 and need until 12:05, but closes at 12:00.
        assertThat(evaluator.evaluate(0, new int[] {A, B}).feasible()).isFalse();
        // The other order works: B 09:25-10:10, A 10:30-11:30.
        assertThat(evaluator.evaluate(0, new int[] {B, A}).feasible()).isTrue();
    }

    @Test
    void rejectsRoutesThatReturnAfterTheDayEnds() {
        RouteEvaluator evaluator = evaluator(5, TimeWindow.between(9, 0, 12, 0), 30);
        assertThat(evaluator.evaluate(0, new int[] {A, B}).feasible()).isFalse();
    }

    @Test
    void enforcesTheDailyVisitLimitUnlessExplicitlyIgnored() {
        RouteEvaluator evaluator = evaluator(1, TimeWindow.between(9, 0, 18, 0), 30);

        assertThat(evaluator.evaluate(0, new int[] {A, B}).feasible()).isFalse();
        assertThat(evaluator.evaluateIgnoringDailyLimit(0, new int[] {A, B}).feasible())
                .isTrue();
    }

    @Test
    void emptyRouteIsFeasibleAndFree() {
        RouteEvaluation empty =
                evaluator(5, TimeWindow.between(9, 0, 18, 0), 30).evaluate(0, Routes.EMPTY);
        assertThat(empty.feasible()).isTrue();
        assertThat(empty.travelMinutes()).isZero();
        assertThat(empty.returnArrivalMinute()).isEqualTo(9 * 60);
    }

    @Test
    void routeHelpersBuildModifiedCopies() {
        int[] route = {1, 2, 3, 4};
        assertThat(Routes.insertAt(route, 2, 9)).containsExactly(1, 2, 9, 3, 4);
        assertThat(Routes.removeAt(route, 0)).containsExactly(2, 3, 4);
        assertThat(Routes.reverse(route, 1, 3)).containsExactly(1, 4, 3, 2);
        assertThat(Routes.move(route, 0, 3)).containsExactly(2, 3, 4, 1);
        assertThat(Routes.replaceAt(route, 1, 7)).containsExactly(1, 7, 3, 4);
        assertThat(Routes.indexOf(route, 3)).isEqualTo(2);
        assertThat(Routes.indexOf(route, 8)).isEqualTo(-1);
        assertThat(route).containsExactly(1, 2, 3, 4);
    }
}
