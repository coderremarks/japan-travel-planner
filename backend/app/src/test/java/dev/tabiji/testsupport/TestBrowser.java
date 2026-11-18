package dev.tabiji.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Behaves like the real single-page app in a browser: keeps cookies and the session between requests,
 * fetches the CSRF cookie and echoes it in {@code X-XSRF-TOKEN} on state-changing requests. Tests that
 * check CSRF protection switch that off with {@link #withoutCsrf()}.
 */
public final class TestBrowser {

    private final MockMvc mvc;
    private final JsonMapper json;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();
    private MockHttpSession session;
    private boolean sendCsrf = true;
    private String remoteAddress = "127.0.0.1";

    TestBrowser(MockMvc mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public TestBrowser withoutCsrf() {
        this.sendCsrf = false;
        return this;
    }

    public TestBrowser withCsrf() {
        this.sendCsrf = true;
        return this;
    }

    public TestBrowser from(String ipAddress) {
        this.remoteAddress = ipAddress;
        return this;
    }

    public MockHttpSession session() {
        return session;
    }

    public String cookie(String name) {
        Cookie cookie = cookies.get(name);
        return cookie == null ? null : cookie.getValue();
    }

    public MvcResult get(String url) throws Exception {
        return send(HttpMethod.GET, url, UnaryOperator.identity());
    }

    public MvcResult postJson(String url, Object body) throws Exception {
        return send(
                HttpMethod.POST,
                url,
                r -> r.contentType(MediaType.APPLICATION_JSON).content(asJson(body)));
    }

    public MvcResult putJson(String url, Object body, String ifMatch) throws Exception {
        return send(HttpMethod.PUT, url, r -> {
            r.contentType(MediaType.APPLICATION_JSON).content(asJson(body));
            return ifMatch == null ? r : r.header("If-Match", ifMatch);
        });
    }

    public MvcResult delete(String url) throws Exception {
        return send(HttpMethod.DELETE, url, UnaryOperator.identity());
    }

    public MvcResult send(HttpMethod method, String url, UnaryOperator<MockHttpServletRequestBuilder> customizer)
            throws Exception {
        boolean unsafe = !(method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS);
        if (sendCsrf && unsafe && cookie("XSRF-TOKEN") == null) {
            get("/api/v1/auth/csrf");
        }
        MockHttpServletRequestBuilder builder = customizer.apply(request(method, url));
        if (!cookies.isEmpty()) {
            builder.cookie(cookies.values().toArray(Cookie[]::new));
        }
        if (session != null) {
            builder.session(session);
        }
        if (sendCsrf && unsafe && cookie("XSRF-TOKEN") != null) {
            builder.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
        }
        String address = remoteAddress;
        builder.with(r -> {
            r.setRemoteAddr(address);
            return r;
        });
        MvcResult result = mvc.perform(builder).andReturn();
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie);
            }
        }
        HttpSession current = result.getRequest().getSession(false);
        if (current instanceof MockHttpSession mock) {
            session = mock.isInvalid() ? null : mock;
        }
        return result;
    }

    public JsonNode register(String email, String password) throws Exception {
        MvcResult result = postJson(
                "/api/v1/auth/register",
                Map.of("email", email, "password", password, "displayName", "Test User", "preferredLocale", "en"));
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException(
                    "registration failed: " + result.getResponse().getContentAsString());
        }
        return body(result);
    }

    public JsonNode login(String email, String password) throws Exception {
        MvcResult result = postJson("/api/v1/auth/login", Map.of("email", email, "password", password));
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException(
                    "login failed: " + result.getResponse().getContentAsString());
        }
        return body(result);
    }

    public JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    public String asJson(Object body) {
        return body instanceof String text ? text : json.writeValueAsString(body);
    }
}
