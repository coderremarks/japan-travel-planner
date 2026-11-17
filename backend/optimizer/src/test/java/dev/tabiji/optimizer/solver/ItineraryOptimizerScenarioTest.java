package dev.tabiji.optimizer.solver;

import static dev.tabiji.optimizer.testsupport.Fixtures.NINE_TO_SIX;
import static dev.tabiji.optimizer.testsupport.Fixtures.dayOf;
import static dev.tabiji.optimizer.testsupport.Fixtures.everyDay;
import static dev.tabiji.optimizer.testsupport.Fixtures.leg;
import static dev.tabiji.optimizer.testsupport.Fixtures.lineMatrix;
import static dev.tabiji.optimizer.testsupport.Fixtures.mustVisit;
import static dev.tabiji.optimizer.testsupport.Fixtures.optional;
import static dev.tabiji.optimizer.testsupport.Fixtures.problem;
import static dev.tabiji.optimizer.testsupport.Fixtures.reasonFor;
import static dev.tabiji.optimizer.testsupport.Fixtures.uniformMatrix;
import static dev.tabiji.optimizer.testsupport.Fixtures.visitOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.tabiji.optimizer.geo.GeoPoint;
import dev.tabiji.optimizer.problem.Candidate;
import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.problem.TripDay;
import dev.tabiji.optimizer.solution.Hint;
import dev.tabiji.optimizer.solution.PlanStatus;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solution.UnscheduledReason;
import dev.tabiji.optimizer.travel.DistanceModel;
import dev.tabiji.optimizer.travel.DistanceModelEstimator;
import dev.tabiji.optimizer.travel.ModeChoicePolicy;
import dev.tabiji.optimizer.travel.TravelMatrix;
import dev.tabiji.optimizer.travel.TravelMatrixBuilder;
import dev.tabiji.optimizer.travel.TravelMode;
import dev.tabiji.optimizer.travel.transit.LineType;
import dev.tabiji.optimizer.travel.transit.Station;
import dev.tabiji.optimizer.travel.transit.TransitAssumptions;
import dev.tabiji.optimizer.travel.transit.TransitLine;
import dev.tabiji.optimizer.travel.transit.TransitNetwork;
import dev.tabiji.optimizer.travel.transit.TransitNetworkEstimator;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Behaviour-level scenarios, each tied to a requirement of the planner. */
class ItineraryOptimizerScenarioTest {

    private final ItineraryOptimizer optimizer = new ItineraryOptimizer();

    private PlanningResult optimize(PlanningProblem problem) {
        PlanningResult result = optimizer.optimize(problem, Strategy.OPTIMIZE);
        assertThat(PlanValidator.violations(problem, result)).isEmpty();
        return result;
    }

    @Test
    void emptyTripIsComplete() {
        PlanningResult result = optimize(problem(List.of(), 2, NINE_TO_SIX, uniformMatrix(1, 0, 0), 4, 0));

        assertThat(result.status()).isEqualTo(PlanStatus.COMPLETE);
        assertThat(result.days())
                .hasSize(2)
                .allSatisfy(day -> assertThat(day.visits()).isEmpty());
        assertThat(result.totals().totalCostYen()).isZero();
    }

    @Nested
    class ImpossibleItineraries {

        @Test
        void mustVisitClosedOnEveryTripDayMakesThePlanInfeasible() {
            Candidate closed = mustVisit("museum", 1, 60, everyDay(2));
            Candidate park = optional("park", 2, 60, 5, everyDay(2, TimeWindow.allDay()));

            PlanningResult result =
                    optimize(problem(List.of(closed, park), 2, NINE_TO_SIX, uniformMatrix(3, 15, 0), 4, 99_999));

            assertThat(result.status()).isEqualTo(PlanStatus.INFEASIBLE);
            assertThat(reasonFor(result, "museum")).contains(UnscheduledReason.CLOSED_ON_TRIP_DATES);
            assertThat(UnscheduledReason.CLOSED_ON_TRIP_DATES.hint()).isEqualTo(Hint.CHANGE_DATES);
            assertThat(dayOf(result, "park")).isNotNegative();
        }

        @Test
        void placeOpenOnlyOutsideTheTravellersHoursIsReportedAsSuch() {
            Candidate nightView = optional("night-view", 1, 60, 5, everyDay(1, TimeWindow.between(19, 0, 23, 0)));

            PlanningResult result =
                    optimize(problem(List.of(nightView), 1, NINE_TO_SIX, uniformMatrix(2, 10, 0), 4, 9_999));

            assertThat(reasonFor(result, "night-view")).contains(UnscheduledReason.OUTSIDE_DAILY_HOURS);
            assertThat(result.status()).isEqualTo(PlanStatus.PARTIAL);
        }

        @Test
        void visitLongerThanTheOpeningWindowCannotBeScheduled() {
            Candidate shortHours = optional("garden", 1, 180, 5, everyDay(1, TimeWindow.between(10, 0, 12, 0)));

            PlanningResult result =
                    optimize(problem(List.of(shortHours), 1, NINE_TO_SIX, uniformMatrix(2, 10, 0), 4, 9_999));

            assertThat(reasonFor(result, "garden")).contains(UnscheduledReason.OUTSIDE_DAILY_HOURS);
        }

        @Test
        void placeTooFarForADayTripIsExplained() {
            Candidate faraway = optional("faraway", 1, 30, 5, everyDay(1, TimeWindow.allDay()));
            TimeWindow morningOnly = TimeWindow.between(9, 0, 12, 0);

            PlanningResult result =
                    optimize(problem(List.of(faraway), 1, morningOnly, uniformMatrix(2, 100, 0), 4, 9_999));

            assertThat(reasonFor(result, "faraway")).contains(UnscheduledReason.TOO_FAR_FOR_DAY);
        }

        @Test
        void mustVisitMoreExpensiveThanTheBudgetIsOverBudget() {
            Candidate pricey = new Candidate("theme-park", 1, 120, 9_000, 5, true, everyDay(1, TimeWindow.allDay()));

            PlanningResult result =
                    optimize(problem(List.of(pricey), 1, NINE_TO_SIX, uniformMatrix(2, 10, 0), 4, 5_000));

            assertThat(result.status()).isEqualTo(PlanStatus.INFEASIBLE);
            assertThat(reasonFor(result, "theme-park")).contains(UnscheduledReason.OVER_BUDGET);
            assertThat(UnscheduledReason.OVER_BUDGET.hint()).isEqualTo(Hint.INCREASE_BUDGET);
        }

        @Test
        void conflictingMustVisitsAreDetected() {
            // Both are open only 10:00-11:30 on the single day and are an hour apart.
            TravelMatrix matrix = TravelMatrix.builder(3)
                    .set(0, 1, leg(30, 0))
                    .set(1, 0, leg(30, 0))
                    .set(0, 2, leg(30, 0))
                    .set(2, 0, leg(30, 0))
                    .set(1, 2, leg(60, 0))
                    .set(2, 1, leg(60, 0))
                    .build();
            TimeWindow lateMorning = TimeWindow.between(10, 0, 11, 30);
            Candidate a = mustVisit("tea-ceremony", 1, 60, everyDay(1, lateMorning));
            Candidate b = mustVisit("temple-tour", 2, 60, everyDay(1, lateMorning));

            PlanningResult result = optimize(problem(List.of(a, b), 1, NINE_TO_SIX, matrix, 4, 9_999));

            assertThat(result.status()).isEqualTo(PlanStatus.INFEASIBLE);
            assertThat(result.totals().mandatoryScheduled()).isEqualTo(1);
            assertThat(reasonFor(result, "temple-tour")).contains(UnscheduledReason.CONFLICTS_WITH_MUST_VISITS);
        }
    }

    @Nested
    class OpeningHours {

        @Test
        void museumClosedOnTheFirstDayIsVisitedOnTheSecond() {
            List<List<TimeWindow>> closedMonday = List.of(List.of(), List.of(TimeWindow.between(9, 30, 17, 0)));
            Candidate museum = mustVisit("museum", 1, 120, closedMonday);

            PlanningResult result =
                    optimize(problem(List.of(museum), 2, NINE_TO_SIX, uniformMatrix(2, 20, 0), 4, 9_999));

            assertThat(result.status()).isEqualTo(PlanStatus.COMPLETE);
            assertThat(dayOf(result, "museum")).isEqualTo(1);
            assertThat(result.days().get(1).visits().getFirst().startMinute()).isGreaterThanOrEqualTo(9 * 60 + 30);
        }

        @Test
        void lunchBreakSplitsTheDayIntoTwoWindows() {
            List<List<TimeWindow>> withLunch =
                    everyDay(1, TimeWindow.between(9, 0, 12, 0), TimeWindow.between(13, 0, 17, 0));
            Candidate shrine = mustVisit("shrine", 1, 150, withLunch);
            Candidate market = mustVisit("market", 2, 150, everyDay(1, TimeWindow.allDay()));

            PlanningResult result =
                    optimize(problem(List.of(shrine, market), 1, NINE_TO_SIX, uniformMatrix(3, 10, 0), 4, 9_999));

            assertThat(result.status()).isEqualTo(PlanStatus.COMPLETE);
            var shrineVisit = result.days().getFirst().visits().stream()
                    .filter(v -> v.candidateId().equals("shrine"))
                    .findFirst()
                    .orElseThrow();
            boolean inMorning = shrineVisit.endMinute() <= 12 * 60;
            boolean inAfternoon = shrineVisit.startMinute() >= 13 * 60;
            assertThat(inMorning || inAfternoon)
                    .as("visit must not span the lunch closure")
                    .isTrue();
        }
    }

    @Nested
    class Priorities {

        @Test
        void mustVisitWinsOverAHigherScoredOptionalPlace() {
            Candidate must = mustVisit("castle", 1, 60, everyDay(1, TimeWindow.allDay()));
            Candidate fun = optional("arcade", 2, 60, 10, everyDay(1, TimeWindow.allDay()));

            PlanningResult result =
                    optimize(problem(List.of(fun, must), 1, NINE_TO_SIX, uniformMatrix(3, 10, 0), 1, 9_999));

            assertThat(dayOf(result, "castle")).isZero();
            assertThat(reasonFor(result, "arcade")).contains(UnscheduledReason.DAILY_LIMIT_REACHED);
            assertThat(result.status()).isEqualTo(PlanStatus.PARTIAL);
        }

        @Test
        void budgetGoesToTheHigherScoredPlace() {
            Candidate better = new Candidate("aquarium", 1, 60, 1_000, 5, false, everyDay(1, TimeWindow.allDay()));
            Candidate worse = new Candidate("tower", 2, 60, 1_000, 3, false, everyDay(1, TimeWindow.allDay()));

            PlanningResult result =
                    optimize(problem(List.of(worse, better), 1, NINE_TO_SIX, uniformMatrix(3, 10, 0), 4, 1_500));

            assertThat(dayOf(result, "aquarium")).isZero();
            assertThat(reasonFor(result, "tower")).contains(UnscheduledReason.BUDGET_EXHAUSTED);
            assertThat(result.totals().totalCostYen()).isLessThanOrEqualTo(1_500);
        }

        @Test
        void whenNotEverythingFitsTheLeftoverIsNotEnoughTime() {
            List<Candidate> places = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                places.add(optional("p" + i, i, 150, i, everyDay(1, TimeWindow.allDay())));
            }

            PlanningResult result = optimize(problem(places, 1, NINE_TO_SIX, uniformMatrix(6, 15, 0), 10, 9_999));

            assertThat(result.status()).isEqualTo(PlanStatus.PARTIAL);
            assertThat(result.unscheduled())
                    .isNotEmpty()
                    .allSatisfy(entry -> assertThat(entry.reason()).isEqualTo(UnscheduledReason.NOT_ENOUGH_TIME));
            assertThat(dayOf(result, "p5")).as("highest score is kept").isZero();
        }
    }

    @Nested
    class RouteQuality {

        /** Places on a line at 4, 1, 3 and 2 km from the hotel, listed in a zig-zag order. */
        private PlanningProblem zigZag() {
            TravelMatrix matrix = lineMatrix(new double[] {0, 4, 1, 3, 2}, 10);
            List<Candidate> places = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                places.add(optional("p" + i, i, 30, 5, everyDay(1, TimeWindow.allDay())));
            }
            return problem(places, 1, NINE_TO_SIX, matrix, 6, 9_999);
        }

        @Test
        void optimizerVisitsPlacesInGeographicOrder() {
            PlanningResult result = optimize(zigZag());

            assertThat(result.status()).isEqualTo(PlanStatus.COMPLETE);
            assertThat(result.totals().travelMinutes()).isEqualTo(80); // 0 -> 1 -> 2 -> 3 -> 4 -> 0 km
            List<String> outward = List.of("p2", "p4", "p3", "p1");
            List<String> backward = List.of("p1", "p3", "p4", "p2");
            assertThat(visitOrder(result, 0)).isIn(outward, backward);
        }

        @Test
        void keepOrderBaselineFollowsTheTravellersListAndTravelsMore() {
            PlanningProblem problem = zigZag();
            PlanningResult kept = optimizer.optimize(problem, Strategy.KEEP_ORDER);

            assertThat(PlanValidator.violations(problem, kept)).isEmpty();
            assertThat(visitOrder(kept, 0)).containsExactly("p1", "p2", "p3", "p4");
            assertThat(kept.totals().travelMinutes()).isEqualTo(120); // 4+3+2+1+2 km
        }

        @Test
        void keepOrderExplainsPlacesThatWouldFitInAnotherOrder() {
            Candidate anytime = optional("anytime", 1, 60, 5, everyDay(1, TimeWindow.allDay()));
            Candidate earlyOnly = optional("morning-market", 2, 60, 5, everyDay(1, TimeWindow.between(9, 0, 10, 30)));
            PlanningProblem problem = problem(
                    List.of(anytime, earlyOnly), 1, TimeWindow.between(9, 0, 13, 0), uniformMatrix(3, 10, 0), 4, 9_999);

            PlanningResult kept = optimizer.optimize(problem, Strategy.KEEP_ORDER);
            PlanningResult optimized = optimize(problem);

            assertThat(reasonFor(kept, "morning-market")).contains(UnscheduledReason.DOES_NOT_FIT_GIVEN_ORDER);
            assertThat(UnscheduledReason.DOES_NOT_FIT_GIVEN_ORDER.hint()).isEqualTo(Hint.USE_OPTIMIZER);
            assertThat(optimized.status()).isEqualTo(PlanStatus.COMPLETE);
            assertThat(visitOrder(optimized, 0)).containsExactly("morning-market", "anytime");
        }

        @Test
        void exactSolverAgreesOnTheZigZagInstance() {
            PlanningResult exact = optimizer.optimize(zigZag(), Strategy.EXACT);
            assertThat(exact.totals().travelMinutes()).isEqualTo(80);
        }

        @Test
        void exactSolverRefusesLargeProblems() {
            List<Candidate> places = new ArrayList<>();
            for (int i = 1; i <= 9; i++) {
                places.add(optional("p" + i, i, 30, 1, everyDay(1, TimeWindow.allDay())));
            }
            PlanningProblem tooLarge = problem(places, 1, NINE_TO_SIX, uniformMatrix(10, 5, 0), 9, 9_999);
            assertThatIllegalArgumentException().isThrownBy(() -> optimizer.optimize(tooLarge, Strategy.EXACT));
        }
    }

    /** End-to-end with real estimators: "missing transit data" must not be papered over. */
    @Nested
    class MissingTransitData {

        private final Station hotelStation = new Station("s1", "S1", "S1", GeoPoint.of(35.0, 135.0));
        private final Station other = new Station("s2", "S2", "S2", GeoPoint.of(35.0, 135.05));
        private final TransitNetworkEstimator transit = new TransitNetworkEstimator(
                new TransitNetwork(
                        List.of(hotelStation, other),
                        List.of(new TransitLine("l", "L", "L", LineType.SUBWAY, List.of("s1", "s2"), false))),
                TransitAssumptions.DEFAULT);
        private final TravelMatrixBuilder builder = new TravelMatrixBuilder(
                List.of(new DistanceModelEstimator(DistanceModel.DEFAULT), transit), ModeChoicePolicy.DEFAULT);

        private final GeoPoint hotel = GeoPoint.of(35.0005, 135.0);
        private final GeoPoint nearStation = GeoPoint.of(35.0005, 135.05); // ~4.5 km east, next to s2
        private final GeoPoint noStationNearby = GeoPoint.of(35.05, 135.0); // ~5.5 km north, no station

        private PlanningProblem withModes(Set<TravelMode> modes) {
            TravelMatrix matrix = builder.build(List.of(hotel, nearStation, noStationNearby), modes);
            return new PlanningProblem(
                    List.of(
                            mustVisit("reachable", 1, 60, everyDay(1, TimeWindow.allDay())),
                            mustVisit("remote", 2, 60, everyDay(1, TimeWindow.allDay()))),
                    List.of(new TripDay(NINE_TO_SIX, 0, 0)),
                    matrix,
                    4,
                    PlanningProblem.UNLIMITED_BUDGET);
        }

        @Test
        void transitOnlyTravellerCannotReachAPlaceWithoutStationData() {
            PlanningResult result = optimize(withModes(EnumSet.of(TravelMode.TRANSIT)));

            assertThat(dayOf(result, "reachable")).isZero();
            assertThat(reasonFor(result, "remote")).contains(UnscheduledReason.NO_ROUTE);
            assertThat(UnscheduledReason.NO_ROUTE.hint()).isEqualTo(Hint.ALLOW_MORE_TRAVEL_MODES);
            assertThat(result.status()).isEqualTo(PlanStatus.INFEASIBLE);
        }

        @Test
        void allowingTaxisResolvesIt() {
            PlanningResult result = optimize(withModes(EnumSet.of(TravelMode.TRANSIT, TravelMode.TAXI)));

            assertThat(result.status()).isEqualTo(PlanStatus.COMPLETE);
            assertThat(result.days().getFirst().visits())
                    .extracting(visit -> visit.travelFromPrevious().mode())
                    .contains(TravelMode.TAXI);
        }
    }
}
