package dev.tabiji.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.tabiji.account.domain.UserAccount;
import dev.tabiji.account.infrastructure.UserAccountRepository;
import dev.tabiji.testsupport.IntegrationTest;
import dev.tabiji.testsupport.TestBrowser;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

/** HTTP-level hardening: headers, error hygiene, limits, CORS, actuator exposure, RBAC. */
class WebSecurityIT extends IntegrationTest {

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        MockHttpServletResponse response =
                mvc.perform(get("/api/v1/areas")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("default-src 'none'", "frame-ancestors 'none'");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getHeader("Permissions-Policy")).contains("geolocation=()");
        assertThat(response.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
        assertThat(response.getHeader("Cross-Origin-Resource-Policy")).isEqualTo("same-origin");
        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void wellFormedRequestIdsAreEchoedAndForgedOnesReplaced() throws Exception {
        String echoed = mvc.perform(get("/api/v1/areas").header("X-Request-Id", "trace-1234abcd"))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");
        String replaced = mvc.perform(get("/api/v1/areas").header("X-Request-Id", "x\" onload=alert(1)"))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(echoed).isEqualTo("trace-1234abcd");
        assertThat(replaced).matches("[0-9a-f-]{36}");
    }

    @Test
    void oversizedBodiesAreRejectedBeforeParsing() throws Exception {
        String huge = "{\"email\":\"" + "a".repeat(70_000) + "\"}";
        TestBrowser browser = browser();
        MvcResult result = browser.postJson("/api/v1/auth/login", huge);

        assertThat(result.getResponse().getStatus()).isEqualTo(413);
        assertThat(browser.body(result).get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    void errorBodiesNeverContainInternals() throws Exception {
        TestBrowser browser = browser();
        MvcResult malformed = browser.postJson("/api/v1/auth/login", "{\"email\": [unclosed");

        assertThat(malformed.getResponse().getStatus()).isEqualTo(400);
        assertThat(malformed.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String body = malformed.getResponse().getContentAsString();
        assertThat(body)
                .contains("\"code\":\"MALFORMED_REQUEST\"", "\"requestId\"")
                .doesNotContain("Exception", "jackson", "at dev.", "trace");
    }

    @Test
    void unknownRoutesAndWrongMethodsGiveProblemResponses() throws Exception {
        TestBrowser browser = browser();
        browser.register(uniqueEmail("routes"), STRONG_PASSWORD);

        MvcResult unknown = browser.get("/api/v1/does-not-exist");
        assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
        assertThat(browser.body(unknown).get("code").asString()).isEqualTo("NOT_FOUND");

        MvcResult wrongMethod =
                browser.send(HttpMethod.PATCH, "/api/v1/areas", r -> r.contentType(MediaType.APPLICATION_JSON));
        assertThat(wrongMethod.getResponse().getStatus()).isIn(403, 405);
    }

    @Test
    void crossOriginRequestsGetNoCorsGrant() throws Exception {
        MockHttpServletResponse preflight = mvc.perform(options("/api/v1/itineraries")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn()
                .getResponse();
        MockHttpServletResponse simple = mvc.perform(get("/api/v1/areas").header("Origin", "https://evil.example"))
                .andReturn()
                .getResponse();

        assertThat(preflight.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(simple.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void actuatorExposesOnlyMinimalHealth() throws Exception {
        MockHttpServletResponse health =
                mvc.perform(get("/actuator/health")).andReturn().getResponse();
        assertThat(health.getStatus()).isEqualTo(200);
        assertThat(health.getContentAsString()).contains("\"status\":\"UP\"").doesNotContain("components");

        assertThat(mvc.perform(get("/actuator/env")).andReturn().getResponse().getStatus())
                .isIn(401, 403, 404);
        assertThat(mvc.perform(get("/actuator/heapdump"))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isIn(401, 403, 404);
    }

    @Test
    void adminEndpointRequiresAdminRole() throws Exception {
        String email = uniqueEmail("admin");
        accounts.save(
                UserAccount.administrator(email, passwordEncoder.encode(STRONG_PASSWORD), "Operator", Instant.now()));
        TestBrowser admin = browser();
        admin.login(email, STRONG_PASSWORD);

        MvcResult stats = admin.get("/api/v1/admin/stats");

        assertThat(stats.getResponse().getStatus()).isEqualTo(200);
        assertThat(admin.body(stats).get("users").asLong()).isPositive();
        assertThat(mvc.perform(get("/api/v1/admin/stats"))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
    }

    @Test
    void logoutWithoutCsrfIsRefused() throws Exception {
        MockHttpServletResponse response =
                mvc.perform(post("/api/v1/auth/logout")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
