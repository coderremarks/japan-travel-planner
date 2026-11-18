package dev.tabiji.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.testsupport.IntegrationTest;
import dev.tabiji.testsupport.TestBrowser;
import dev.tabiji.testsupport.Trips;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** CRUD, optimistic locking and validation of itineraries. */
class ItineraryApiIT extends IntegrationTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 8);

    private TestBrowser traveller;
    private Trips trips;

    @BeforeEach
    void signIn() throws Exception {
        traveller = browser();
        traveller.register(uniqueEmail("crud"), STRONG_PASSWORD);
        trips = new Trips(traveller);
    }

    @Test
    void createReturnsLocationAndEtagAndTheStoredItinerary() throws Exception {
        MvcResult created = traveller.postJson(
                "/api/v1/itineraries",
                trips.tokyoTrip(START, 3, List.of("senso-ji", "ueno-park"), List.of("senso-ji")));

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = traveller.body(created);
        String id = body.get("id").asString();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/itineraries/" + id);
        assertThat(created.getResponse().getHeader("ETag")).isEqualTo("\"0\"");
        assertThat(body.get("dayStart").asString()).isEqualTo("09:00");
        assertThat(body.get("stops")).hasSize(2);
        assertThat(body.get("stops").get(0).get("mandatory").asBoolean()).isTrue();
        assertThat(body.get("stops").get(0).get("place").get("name").get("ja").asString())
                .isEqualTo("浅草寺");
        assertThat(body.get("plan").isNull()).isTrue();
    }

    @Test
    void updateRequiresTheCurrentVersion() throws Exception {
        Map<String, Object> trip = trips.tokyoTrip(START, 2, List.of("senso-ji"), List.of());
        String id = traveller
                .body(traveller.postJson("/api/v1/itineraries", trip))
                .get("id")
                .asString();
        String url = "/api/v1/itineraries/" + id;
        trip.put("title", "Renamed trip");

        MvcResult missing = traveller.putJson(url, trip, null);
        assertThat(missing.getResponse().getStatus()).isEqualTo(428);
        assertThat(code(missing)).isEqualTo("VERSION_REQUIRED");

        MvcResult updated = traveller.putJson(url, trip, "\"0\"");
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(updated.getResponse().getHeader("ETag")).isEqualTo("\"1\"");
        assertThat(traveller.body(updated).get("title").asString()).isEqualTo("Renamed trip");

        trip.put("title", "Lost update");
        MvcResult stale = traveller.putJson(url, trip, "\"0\"");
        assertThat(stale.getResponse().getStatus()).isEqualTo(412);
        assertThat(code(stale)).isEqualTo("VERSION_MISMATCH");
        assertThat(traveller.body(traveller.get(url)).get("title").asString()).isEqualTo("Renamed trip");
    }

    @Test
    void reorderingStopsIsPersisted() throws Exception {
        List<String> slugs = List.of("senso-ji", "ueno-park", "akihabara", "ginza");
        Map<String, Object> trip = trips.tokyoTrip(START, 2, slugs, List.of());
        String id = traveller
                .body(traveller.postJson("/api/v1/itineraries", trip))
                .get("id")
                .asString();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stops = new ArrayList<>((List<Map<String, Object>>) trip.get("stops"));
        java.util.Collections.reverse(stops);
        trip.put("stops", stops);
        MvcResult updated = traveller.putJson("/api/v1/itineraries/" + id, trip, "\"0\"");

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        JsonNode reloaded = traveller.body(traveller.get("/api/v1/itineraries/" + id));
        List<String> order = new ArrayList<>();
        reloaded.get("stops")
                .forEach(stop -> order.add(stop.get("place").get("slug").asString()));
        assertThat(order).containsExactly("ginza", "akihabara", "ueno-park", "senso-ji");
    }

    @Test
    void crossFieldRulesAreValidated() throws Exception {
        Map<String, Object> trip = trips.tokyoTrip(START, 2, List.of("senso-ji", "senso-ji"), List.of());
        trip.put("endDate", START.minusDays(1).toString());
        trip.put("dayEnd", "09:30");

        MvcResult result = traveller.postJson("/api/v1/itineraries", trip);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(result)).isEqualTo("VALIDATION_FAILED");
        assertThat(violations(result))
                .contains("endDate:END_BEFORE_START", "dayEnd:DAY_TOO_SHORT", "stops[1].placeId:DUPLICATE_PLACE");
    }

    @Test
    void tripsLongerThanTwoWeeksAndUnknownPlacesAreRejected() throws Exception {
        Map<String, Object> trip = trips.tokyoTrip(START, 15, List.of("senso-ji"), List.of());
        trip.put("stops", List.of(Map.of("placeId", java.util.UUID.randomUUID().toString(), "mandatory", false)));

        MvcResult result = traveller.postJson("/api/v1/itineraries", trip);

        assertThat(violations(result)).contains("endDate:TRIP_TOO_LONG", "stops[0].placeId:UNKNOWN_PLACE");
    }

    @Test
    void beanValidationCoversSingleFields() throws Exception {
        Map<String, Object> trip = trips.tokyoTrip(START, 2, List.of("senso-ji"), List.of());
        trip.put("title", "");
        trip.put("budgetYen", -1);
        trip.put("travelModes", List.of());
        trip.put("maxVisitsPerDay", 99);

        MvcResult result = traveller.postJson("/api/v1/itineraries", trip);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(violations(result))
                .contains("title:NotBlank", "budgetYen:Min", "travelModes:Size", "maxVisitsPerDay:Max");
    }

    @Test
    void controlCharactersInTitlesAreRejected() throws Exception {
        Map<String, Object> trip = trips.tokyoTrip(START, 2, List.of("senso-ji"), List.of());
        trip.put("title", "Trip\r\nATTENDEE:mailto:victim@example.test");

        assertThat(violations(traveller.postJson("/api/v1/itineraries", trip))).contains("title:Pattern");
    }

    @Test
    void malformedValuesAreRejectedWithoutLeakingParserDetails() throws Exception {
        Map<String, Object> trip = trips.tokyoTrip(START, 2, List.of("senso-ji"), List.of());
        trip.put("dayStart", "25:99");

        MvcResult result = traveller.postJson("/api/v1/itineraries", trip);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(result)).isEqualTo("MALFORMED_REQUEST");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Exception")
                .doesNotContain("jackson")
                .doesNotContain("at dev.");
    }

    @Test
    void duplicateCopiesInputsButNotThePlanAndDeleteRemovesEverything() throws Exception {
        String id = traveller
                .body(traveller.postJson(
                        "/api/v1/itineraries", trips.tokyoTrip(START, 2, List.of("senso-ji", "ueno-park"), List.of())))
                .get("id")
                .asString();
        traveller.postJson("/api/v1/itineraries/" + id + "/plan", Map.of("strategy", "OPTIMIZE"));

        MvcResult copy = traveller.postJson("/api/v1/itineraries/" + id + "/duplicate", "");
        assertThat(copy.getResponse().getStatus()).isEqualTo(201);
        JsonNode copyBody = traveller.body(copy);
        assertThat(copyBody.get("id").asString()).isNotEqualTo(id);
        assertThat(copyBody.get("title").asString()).isEqualTo("Tokyo test trip (copy)");
        assertThat(copyBody.get("stops")).hasSize(2);
        assertThat(copyBody.get("plan").isNull()).isTrue();

        assertThat(traveller.delete("/api/v1/itineraries/" + id).getResponse().getStatus())
                .isEqualTo(204);
        assertThat(traveller.get("/api/v1/itineraries/" + id).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(traveller
                        .get("/api/v1/itineraries/" + id + "/plan")
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);

        JsonNode list = traveller.body(traveller.get("/api/v1/itineraries"));
        assertThat(list.get("totalElements").asLong()).isEqualTo(1);
        assertThat(list.get("content").get(0).get("id").asString())
                .isEqualTo(copyBody.get("id").asString());
    }

    @Test
    void copiesAreNamedInTheTravellersLanguage() throws Exception {
        String id = traveller
                .body(traveller.postJson(
                        "/api/v1/itineraries", trips.tokyoTrip(START, 1, List.of("senso-ji"), List.of())))
                .get("id")
                .asString();

        MvcResult japanese = traveller.postJson("/api/v1/itineraries/" + id + "/duplicate?lang=ja", "");
        assertThat(japanese.getResponse().getStatus()).isEqualTo(201);
        assertThat(traveller.body(japanese).get("title").asString()).isEqualTo("Tokyo test trip（コピー）");
        assertThat(traveller
                        .postJson("/api/v1/itineraries/" + id + "/duplicate?lang=fr", "")
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
    }

    private String code(MvcResult result) throws Exception {
        return traveller.body(result).get("code").asString();
    }

    private List<String> violations(MvcResult result) throws Exception {
        List<String> out = new ArrayList<>();
        JsonNode errors = traveller.body(result).get("errors");
        if (errors != null) {
            errors.forEach(
                    e -> out.add(e.get("field").asString() + ":" + e.get("code").asString()));
        }
        return out;
    }
}
