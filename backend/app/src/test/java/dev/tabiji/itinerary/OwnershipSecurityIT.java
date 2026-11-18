package dev.tabiji.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.testsupport.IntegrationTest;
import dev.tabiji.testsupport.TestBrowser;
import dev.tabiji.testsupport.Trips;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Broken Object Level Authorization (OWASP API1) regression suite: another user's itinerary must be
 * invisible and immutable through <em>every</em> endpoint, and the answer must be indistinguishable
 * from a non-existent ID (404), so IDs cannot be probed.
 */
class OwnershipSecurityIT extends IntegrationTest {

    private TestBrowser alice;
    private TestBrowser mallory;
    private String aliceItinerary;
    private Map<String, Object> aliceTrip;
    private String aliceEtag;

    @BeforeEach
    void aliceHasAPlannedItinerary() throws Exception {
        alice = browser();
        alice.register(uniqueEmail("alice"), STRONG_PASSWORD);
        aliceTrip = new Trips(alice)
                .tokyoTrip(LocalDate.of(2026, 12, 8), 2, List.of("senso-ji", "ueno-park", "akihabara"), List.of());
        MvcResult created = alice.postJson("/api/v1/itineraries", aliceTrip);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        aliceItinerary = alice.body(created).get("id").asString();
        aliceEtag = created.getResponse().getHeader("ETag");
        assertThat(alice.postJson("/api/v1/itineraries/" + aliceItinerary + "/plan", Map.of("strategy", "OPTIMIZE"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);

        mallory = browser();
        mallory.register(uniqueEmail("mallory"), STRONG_PASSWORD);
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {"", "/plan", "/calendar.ics", "/export.json"})
    void otherUsersCannotReadAnything(String suffix) throws Exception {
        MvcResult result = mallory.get("/api/v1/itineraries/" + aliceItinerary + suffix);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString())
                .contains("ITINERARY_NOT_FOUND")
                .doesNotContain("Tokyo test trip");
    }

    @Test
    void otherUsersCannotModifyDeleteDuplicateOrPlan() throws Exception {
        String url = "/api/v1/itineraries/" + aliceItinerary;
        Map<String, Object> hijacked = new java.util.LinkedHashMap<>(aliceTrip);
        hijacked.put("title", "pwned");

        assertThat(mallory.putJson(url, hijacked, aliceEtag).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(mallory.delete(url).getResponse().getStatus()).isEqualTo(404);
        assertThat(mallory.postJson(url + "/duplicate", "").getResponse().getStatus())
                .isEqualTo(404);
        assertThat(mallory.postJson(url + "/plan", Map.of("strategy", "KEEP_ORDER"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);

        JsonNode stillAlices = alice.body(alice.get(url));
        assertThat(stillAlices.get("title").asString()).isEqualTo("Tokyo test trip");
        assertThat(stillAlices.get("version").asLong()).isZero();
        assertThat(alice.get(url + "/plan").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void foreignAndNonExistentIdsLookTheSame() throws Exception {
        MvcResult foreign = mallory.get("/api/v1/itineraries/" + aliceItinerary);
        MvcResult missing = mallory.get("/api/v1/itineraries/" + UUID.randomUUID());

        assertThat(foreign.getResponse().getStatus())
                .isEqualTo(missing.getResponse().getStatus());
        JsonNode foreignBody = mallory.body(foreign);
        JsonNode missingBody = mallory.body(missing);
        assertThat(foreignBody.get("code")).isEqualTo(missingBody.get("code"));
        assertThat(foreignBody.get("detail")).isEqualTo(missingBody.get("detail"));
    }

    @Test
    void listingOnlyShowsOwnItineraries() throws Exception {
        JsonNode malloryList = mallory.body(mallory.get("/api/v1/itineraries"));
        assertThat(malloryList.get("totalElements").asLong()).isZero();

        JsonNode aliceList = alice.body(alice.get("/api/v1/itineraries"));
        assertThat(aliceList.get("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void ownerCannotBeReassignedThroughTheBody() throws Exception {
        Map<String, Object> withOwner = new java.util.LinkedHashMap<>(aliceTrip);
        withOwner.put("ownerId", UUID.randomUUID().toString());

        MvcResult result = alice.postJson("/api/v1/itineraries", withOwner);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(alice.body(result).get("code").asString()).isEqualTo("UNKNOWN_FIELD");
    }

    @Test
    void anonymousRequestsAreUnauthenticated() throws Exception {
        TestBrowser anonymous = browser();
        assertThat(anonymous.get("/api/v1/itineraries").getResponse().getStatus())
                .isEqualTo(401);
        assertThat(anonymous
                        .get("/api/v1/itineraries/" + aliceItinerary)
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
        assertThat(anonymous
                        .postJson("/api/v1/itineraries", aliceTrip)
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
    }

    @Test
    void regularUsersCannotUseAdminEndpoints() throws Exception {
        MvcResult result = alice.get("/api/v1/admin/stats");
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(alice.body(result).get("code").asString()).isEqualTo("FORBIDDEN");
    }

    @Test
    void stateChangesWithoutCsrfTokenAreRejectedEvenWhenSignedIn() throws Exception {
        MvcResult result = alice.withoutCsrf()
                .send(
                        HttpMethod.DELETE,
                        "/api/v1/itineraries/" + aliceItinerary,
                        r -> r.contentType(MediaType.APPLICATION_JSON));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        alice.withCsrf();
        assertThat(alice.get("/api/v1/itineraries/" + aliceItinerary)
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
    }
}
