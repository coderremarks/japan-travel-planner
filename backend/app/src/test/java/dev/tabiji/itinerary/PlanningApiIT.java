package dev.tabiji.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.testsupport.IntegrationTest;
import dev.tabiji.testsupport.TestBrowser;
import dev.tabiji.testsupport.Trips;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Planning end to end: real catalogue data, real opening rules, real travel estimators. */
class PlanningApiIT extends IntegrationTest {

    private TestBrowser traveller;
    private Trips trips;

    @BeforeEach
    void signIn() throws Exception {
        traveller = browser();
        traveller.register(uniqueEmail("planner"), STRONG_PASSWORD);
        trips = new Trips(traveller);
    }

    private String create(Map<String, Object> trip) throws Exception {
        MvcResult created = traveller.postJson("/api/v1/itineraries", trip);
        assertThat(created.getResponse().getStatus())
                .as(created.getResponse().getContentAsString())
                .isEqualTo(201);
        return traveller.body(created).get("id").asString();
    }

    private JsonNode plan(String id, String strategy) throws Exception {
        MvcResult result = traveller.postJson("/api/v1/itineraries/" + id + "/plan", Map.of("strategy", strategy));
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(200);
        return traveller.body(result).get("plan");
    }

    @Test
    void optimizedTokyoPlanRespectsHoursAndBudgetAndIsLabelledAsEstimate() throws Exception {
        String id = create(trips.tokyoTrip(
                LocalDate.of(2026, 12, 8),
                2,
                List.of("senso-ji", "tokyo-skytree", "ueno-park", "akihabara", "ameyoko", "tokyo-national-museum"),
                List.of("tokyo-national-museum")));

        JsonNode plan = plan(id, "OPTIMIZE");

        assertThat(plan.get("status").asString()).isIn("COMPLETE", "PARTIAL");
        assertThat(plan.get("summary").get("withinBudget").asBoolean()).isTrue();
        assertThat(plan.get("summary").get("mandatoryScheduled").asInt()).isEqualTo(1);
        assertThat(plan.get("notices").toString()).contains("ESTIMATED_TRAVEL", "SAMPLE_CATALOG_DATA");
        assertThat(plan.get("comparison").isNull()).isFalse();
        for (JsonNode day : plan.get("days")) {
            LocalTime previousEnd = LocalTime.parse(day.get("departure").asString());
            for (JsonNode visit : day.get("visits")) {
                LocalTime arrival = LocalTime.parse(visit.get("arrival").asString());
                LocalTime start = LocalTime.parse(visit.get("start").asString());
                assertThat(arrival).isAfterOrEqualTo(previousEnd);
                assertThat(start).isAfterOrEqualTo(arrival);
                assertThat(visit.get("travel").get("estimated").asBoolean()).isTrue();
                previousEnd = LocalTime.parse(visit.get("end").asString());
            }
            assertThat(LocalTime.parse(day.get("returnArrival").asString())).isBeforeOrEqualTo(LocalTime.of(19, 0));
        }
    }

    /**
     * 2026-11-23 is Labour Thanksgiving Day (a Monday). The Tokyo National Museum is normally closed on
     * Mondays, but under the 「祝日の場合は開館、翌平日休館」 rule it opens that day and closes on Tuesday.
     */
    @Test
    void japaneseHolidayRuleMovesTheMuseumClosingDay() throws Exception {
        String id = create(trips.tokyoTrip(
                LocalDate.of(2026, 11, 23), 2, List.of("tokyo-national-museum"), List.of("tokyo-national-museum")));

        JsonNode plan = plan(id, "OPTIMIZE");

        assertThat(plan.get("status").asString()).isEqualTo("COMPLETE");
        JsonNode monday = plan.get("days").get(0);
        assertThat(monday.get("holiday").get("ja").asString()).isEqualTo("勤労感謝の日");
        assertThat(monday.get("visits")).hasSize(1);
        assertThat(plan.get("days").get(1).get("visits")).isEmpty();
    }

    @Test
    void regularMondayClosureIsRespected() throws Exception {
        String id = create(trips.tokyoTrip(
                LocalDate.of(2026, 12, 7), 2, List.of("tokyo-national-museum"), List.of("tokyo-national-museum")));

        JsonNode plan = plan(id, "OPTIMIZE");

        assertThat(plan.get("days").get(0).get("visits")).isEmpty();
        assertThat(plan.get("days").get(1).get("visits")).hasSize(1);
    }

    @Test
    void missingTransitDataIsReportedInsteadOfInvented() throws Exception {
        Map<String, Object> trip = trips.tokyoTrip(
                LocalDate.of(2026, 12, 8), 1, List.of("kinkaku-ji", "nijo-castle"), List.of("kinkaku-ji"));
        trip.put("baseLocationId", trips.baseLocationId("kyoto-station"));
        trip.put("travelModes", List.of("TRANSIT"));
        String id = create(trip);

        JsonNode plan = plan(id, "OPTIMIZE");

        assertThat(plan.get("status").asString()).isEqualTo("INFEASIBLE");
        JsonNode unscheduled = plan.get("unscheduled").get(0);
        assertThat(unscheduled.get("slug").asString()).isEqualTo("kinkaku-ji");
        assertThat(unscheduled.get("reason").asString()).isEqualTo("NO_ROUTE");
        assertThat(plan.get("hints").toString()).contains("ALLOW_MORE_TRAVEL_MODES");

        trip.put("travelModes", List.of("TRANSIT", "TAXI"));
        traveller.putJson("/api/v1/itineraries/" + id, trip, "\"0\"");
        assertThat(plan(id, "OPTIMIZE").get("status").asString()).isEqualTo("COMPLETE");
    }

    @Test
    void keepOrderFollowsTheTravellersList() throws Exception {
        List<String> slugs = List.of("akihabara", "senso-ji", "ueno-park");
        String id = create(trips.tokyoTrip(LocalDate.of(2026, 12, 8), 1, slugs, List.of()));

        JsonNode plan = plan(id, "KEEP_ORDER");

        List<String> visited = new ArrayList<>();
        plan.get("days")
                .get(0)
                .get("visits")
                .forEach(v -> visited.add(v.get("slug").asString()));
        assertThat(visited).containsExactly("akihabara", "senso-ji", "ueno-park");
        assertThat(plan.get("strategy").asString()).isEqualTo("KEEP_ORDER");
        assertThat(plan.get("comparison").isNull()).isTrue();
    }

    @Test
    void suggestionsFillFreeTimeWithPlacesMatchingInterests() throws Exception {
        Map<String, Object> trip = trips.tokyoTrip(LocalDate.of(2026, 12, 8), 1, List.of("senso-ji"), List.of());
        trip.put("includeSuggestions", true);
        trip.put("interests", List.of("FOOD"));
        String id = create(trip);

        JsonNode plan = plan(id, "OPTIMIZE");

        List<String> suggestedCategories = new ArrayList<>();
        plan.get("days").get(0).get("visits").forEach(v -> {
            if (v.get("suggested").asBoolean()) {
                suggestedCategories.add(v.get("category").asString());
            }
        });
        assertThat(suggestedCategories).isNotEmpty().allMatch("FOOD"::equals);
    }

    @Test
    void storedPlanBecomesStaleWhenTheItineraryChanges() throws Exception {
        Map<String, Object> trip =
                trips.tokyoTrip(LocalDate.of(2026, 12, 8), 1, List.of("senso-ji", "ueno-park"), List.of());
        String id = create(trip);
        plan(id, "OPTIMIZE");
        assertThat(traveller
                        .body(traveller.get("/api/v1/itineraries/" + id + "/plan"))
                        .get("stale")
                        .asBoolean())
                .isFalse();

        trip.put("title", "Renaming does not invalidate a plan");
        traveller.putJson("/api/v1/itineraries/" + id, trip, "\"0\"");
        assertThat(traveller
                        .body(traveller.get("/api/v1/itineraries/" + id + "/plan"))
                        .get("stale")
                        .asBoolean())
                .isFalse();

        trip.put("budgetYen", 1000);
        traveller.putJson("/api/v1/itineraries/" + id, trip, "\"1\"");
        assertThat(traveller
                        .body(traveller.get("/api/v1/itineraries/" + id + "/plan"))
                        .get("stale")
                        .asBoolean())
                .isTrue();
        assertThat(traveller
                        .body(traveller.get("/api/v1/itineraries/" + id))
                        .get("plan")
                        .get("stale")
                        .asBoolean())
                .isTrue();
    }

    @Test
    void planningWithoutPlacesIsRejected() throws Exception {
        String id = create(trips.tokyoTrip(LocalDate.of(2026, 12, 8), 1, List.of(), List.of()));

        MvcResult result = traveller.postJson("/api/v1/itineraries/" + id + "/plan", Map.of("strategy", "OPTIMIZE"));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(traveller.body(result).get("code").asString()).isEqualTo("NOTHING_TO_PLAN");
        assertThat(traveller
                        .get("/api/v1/itineraries/" + id + "/plan")
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
    }

    @Test
    void exactStrategyIsNotExposedOverHttp() throws Exception {
        String id = create(trips.tokyoTrip(LocalDate.of(2026, 12, 8), 1, List.of("senso-ji"), List.of()));
        MvcResult result = traveller.postJson("/api/v1/itineraries/" + id + "/plan", Map.of("strategy", "EXACT"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void calendarAndJsonExports() throws Exception {
        String id = create(trips.tokyoTrip(LocalDate.of(2026, 12, 8), 1, List.of("senso-ji", "ueno-park"), List.of()));
        assertThat(traveller
                        .get("/api/v1/itineraries/" + id + "/calendar.ics")
                        .getResponse()
                        .getStatus())
                .as("no plan yet")
                .isEqualTo(404);
        plan(id, "OPTIMIZE");

        MvcResult ics = traveller.get("/api/v1/itineraries/" + id + "/calendar.ics?lang=ja");
        assertThat(ics.getResponse().getStatus()).isEqualTo(200);
        assertThat(ics.getResponse().getContentType()).startsWith("text/calendar");
        assertThat(ics.getResponse().getHeader("Content-Disposition")).contains("attachment");
        String calendar = ics.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(calendar).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(calendar.split("BEGIN:VEVENT", -1)).hasSize(3);
        assertThat(calendar).contains("SUMMARY:浅草寺").contains("DTSTART;TZID=Asia/Tokyo:20261208T");

        MvcResult export = traveller.get("/api/v1/itineraries/" + id + "/export.json");
        JsonNode body = traveller.body(export);
        assertThat(body.get("itinerary").get("id").asString()).isEqualTo(id);
        assertThat(body.get("plan").get("days")).hasSize(1);
    }
}
