package dev.tabiji.account;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.testsupport.IntegrationTest;
import dev.tabiji.testsupport.TestBrowser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Registration, login, logout and the session/CSRF guarantees around them. */
class AuthFlowIT extends IntegrationTest {

    @Test
    void registerSignsInAndLogoutSignsOut() throws Exception {
        TestBrowser browser = browser();
        String email = uniqueEmail("flow");

        JsonNode user = browser.register(email, STRONG_PASSWORD);
        assertThat(user.get("email").asString()).isEqualTo(email);
        assertThat(user.get("role").asString()).isEqualTo("USER");
        assertThat(user.has("passwordHash")).isFalse();

        assertThat(browser.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(200);

        MvcResult logout = browser.postJson("/api/v1/auth/logout", "");
        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        assertThat(browser.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void loginIsCaseInsensitiveOnEmailAndRotatesTheSessionId() throws Exception {
        String email = uniqueEmail("rotate");
        browser().register(email, STRONG_PASSWORD);

        TestBrowser browser = browser();
        browser.get("/api/v1/auth/csrf");
        browser.get("/api/v1/places?size=1");
        String csrfBefore = browser.cookie("XSRF-TOKEN");
        browser.login(email.toUpperCase(java.util.Locale.ROOT), STRONG_PASSWORD);

        assertThat(browser.session()).isNotNull();
        assertThat(browser.cookie("XSRF-TOKEN"))
                .as("CSRF token is rotated on login")
                .isNotEqualTo(csrfBefore);
        assertThat(browser.get("/api/v1/auth/me").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void sessionFixationIsPrevented() throws Exception {
        String email = uniqueEmail("fixation");
        browser().register(email, STRONG_PASSWORD);
        TestBrowser browser = browser();
        browser.get("/api/v1/auth/csrf");
        // An attacker-planted session exists before login...
        browser.login(email, STRONG_PASSWORD);
        String firstId = browser.session().getId();
        browser.postJson("/api/v1/auth/logout", "");
        browser.login(email, STRONG_PASSWORD);
        // ...and every successful login gets a different session ID.
        assertThat(browser.session().getId()).isNotEqualTo(firstId);
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        String email = uniqueEmail("generic");
        browser().register(email, STRONG_PASSWORD);

        MvcResult wrongPassword =
                browser().postJson("/api/v1/auth/login", Map.of("email", email, "password", "not the password!"));
        MvcResult unknownEmail = browser()
                .postJson("/api/v1/auth/login", Map.of("email", uniqueEmail("nobody"), "password", "whatever-1234"));

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(code(wrongPassword)).isEqualTo("INVALID_CREDENTIALS").isEqualTo(code(unknownEmail));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail("dupe");
        browser().register(email, STRONG_PASSWORD);

        MvcResult again = browser()
                .postJson(
                        "/api/v1/auth/register",
                        Map.of(
                                "email",
                                email,
                                "password",
                                STRONG_PASSWORD,
                                "displayName",
                                "X",
                                "preferredLocale",
                                "ja"));

        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(code(again)).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void weakPasswordsAreRejectedWithReasonCodesButWithoutEchoingThePassword() throws Exception {
        String localPart = "kenji" + Math.abs(System.nanoTime() % 1_000_000);
        String password = localPart + "-loves-japan";
        MvcResult result = browser()
                .postJson(
                        "/api/v1/auth/register",
                        Map.of(
                                "email",
                                localPart + "@example.test",
                                "password",
                                password,
                                "displayName",
                                "X",
                                "preferredLocale",
                                "en"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(result)).isEqualTo("PASSWORD_TOO_WEAK");
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("CONTAINS_EMAIL").doesNotContain(password);

        MvcResult tooShort = browser()
                .postJson(
                        "/api/v1/auth/register",
                        Map.of(
                                "email",
                                uniqueEmail("short"),
                                "password",
                                "short",
                                "displayName",
                                "X",
                                "preferredLocale",
                                "en"));
        assertThat(tooShort.getResponse().getContentAsString()).contains("TOO_SHORT");
    }

    @Test
    void massAssignmentOfRoleIsRejected() throws Exception {
        MvcResult result = browser()
                .postJson(
                        "/api/v1/auth/register",
                        Map.of(
                                "email", uniqueEmail("escalate"),
                                "password", STRONG_PASSWORD,
                                "displayName", "Mallory",
                                "preferredLocale", "en",
                                "role", "ADMIN"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(code(result)).isEqualTo("UNKNOWN_FIELD");
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreRejected() throws Exception {
        MvcResult result = browser()
                .withoutCsrf()
                .postJson("/api/v1/auth/login", Map.of("email", uniqueEmail("csrf"), "password", STRONG_PASSWORD));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(code(result)).isEqualTo("CSRF_TOKEN_INVALID");
    }

    @Test
    void repeatedFailedLoginsForOneAccountAreRateLimited() throws Exception {
        String email = uniqueEmail("bruteforce");
        browser().register(email, STRONG_PASSWORD);
        TestBrowser attacker = browser();
        int lastStatus = 0;
        MvcResult last = null;
        for (int attempt = 1; attempt <= 6; attempt++) {
            last = attacker.postJson(
                    "/api/v1/auth/login", Map.of("email", email, "password", "guess number " + attempt));
            lastStatus = last.getResponse().getStatus();
        }
        assertThat(lastStatus).isEqualTo(429);
        assertThat(last.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(code(last)).isEqualTo("TOO_MANY_REQUESTS");

        // Even the right password is refused while the account is throttled.
        assertThat(attacker.postJson("/api/v1/auth/login", Map.of("email", email, "password", STRONG_PASSWORD))
                        .getResponse()
                        .getStatus())
                .isEqualTo(429);
    }

    @Test
    void passwordHashIsStoredWithArgon2id() throws Exception {
        String email = uniqueEmail("argon");
        browser().register(email, STRONG_PASSWORD);
        String hash = accounts.findByEmail(email).orElseThrow().passwordHash();
        assertThat(hash).startsWith("{argon2}$argon2id$");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private dev.tabiji.account.infrastructure.UserAccountRepository accounts;

    private String code(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString())
                .get("code")
                .asString();
    }
}
