package dev.tabiji.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.catalog.domain.PlaceCategory;
import dev.tabiji.itinerary.domain.ItineraryDraft;
import dev.tabiji.optimizer.problem.Candidate;
import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.problem.TripDay;
import dev.tabiji.optimizer.solution.ScheduledVisit;
import dev.tabiji.optimizer.solver.ItineraryOptimizer;
import dev.tabiji.optimizer.travel.Leg;
import dev.tabiji.optimizer.travel.LegSource;
import dev.tabiji.optimizer.travel.TravelMatrix;
import dev.tabiji.optimizer.travel.TravelMode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CandidateScoringTest {

    private static final Set<PlaceCategory> LIKES_FOOD = Set.of(PlaceCategory.FOOD);

    @Test
    void oneChosenPlaceOutweighsEverythingAPlanCouldGainByDroppingIt() {
        int bonusesOfAllOtherStops = ItineraryDraft.MAX_STOPS * CandidateScoring.INTEREST_BONUS;
        int largestSetOfSuggestions =
                CandidateScoring.MAX_SUGGESTIONS * CandidateScoring.suggested(PlaceCategory.FOOD, LIKES_FOOD);

        assertThat(CandidateScoring.chosen(PlaceCategory.MUSEUM, LIKES_FOOD))
                .isGreaterThan(bonusesOfAllOtherStops + largestSetOfSuggestions);
    }

    @Test
    void interestsAddTheSameBonusToChosenAndSuggestedPlaces() {
        assertThat(CandidateScoring.chosen(PlaceCategory.FOOD, LIKES_FOOD)
                        - CandidateScoring.chosen(PlaceCategory.MUSEUM, LIKES_FOOD))
                .isEqualTo(CandidateScoring.INTEREST_BONUS);
        assertThat(CandidateScoring.suggested(PlaceCategory.FOOD, LIKES_FOOD)
                        - CandidateScoring.suggested(PlaceCategory.MUSEUM, LIKES_FOOD))
                .isEqualTo(CandidateScoring.INTEREST_BONUS);
    }

    /**
     * One free morning: either the traveller's long museum visit or three short suggestions fit, never
     * both. The suggestions match the traveller's interest and together fill more of the morning.
     */
    @Test
    void suggestionsFillFreeTimeButNeverDisplaceAChosenPlace() {
        int museum = CandidateScoring.chosen(PlaceCategory.MUSEUM, LIKES_FOOD);
        int suggestion = CandidateScoring.suggested(PlaceCategory.FOOD, LIKES_FOOD);

        assertThat(scheduledIds(museum, suggestion)).containsExactly("museum");

        // The scenario is sensitive to the weights: had a chosen place been worth less than three
        // suggestions (e.g. 10 against 3 × 5), the museum would have been dropped.
        assertThat(scheduledIds(10, suggestion)).doesNotContain("museum").hasSize(3);
    }

    private static List<String> scheduledIds(int museumScore, int suggestionScore) {
        List<List<TimeWindow>> openAllDay = List.of(List.of(TimeWindow.between(9, 0, 17, 0)));
        List<Candidate> candidates = List.of(
                new Candidate("museum", 1, 150, 0, museumScore, false, openAllDay),
                new Candidate("cafe", 2, 45, 0, suggestionScore, false, openAllDay),
                new Candidate("market", 3, 45, 0, suggestionScore, false, openAllDay),
                new Candidate("bakery", 4, 45, 0, suggestionScore, false, openAllDay));
        TravelMatrix.Builder travel = TravelMatrix.builder(5);
        for (int from = 0; from < 5; from++) {
            for (int to = 0; to < 5; to++) {
                travel.set(from, to, Leg.estimate(TravelMode.WALK, 10, 0, 750, LegSource.DISTANCE_MODEL));
            }
        }
        // 09:00-12:00 from and back to the hotel (location 0): museum alone takes 10 + 150 + 10 minutes,
        // the three suggestions 4 × 10 + 3 × 45; the museum plus any suggestion does not fit.
        List<TripDay> morning = List.of(new TripDay(TimeWindow.between(9, 0, 12, 0), 0, 0));
        PlanningProblem problem =
                new PlanningProblem(candidates, morning, travel.build(), 12, PlanningProblem.UNLIMITED_BUDGET);

        return new ItineraryOptimizer()
                .optimize(problem, Strategy.OPTIMIZE).days().stream()
                        .flatMap(day -> day.visits().stream())
                        .map(ScheduledVisit::candidateId)
                        .toList();
    }
}
