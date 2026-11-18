package dev.tabiji.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF handling for a single-page application, following the Spring Security reference documentation:
 *
 * <ul>
 *   <li>The token travels in the readable {@code XSRF-TOKEN} cookie; the SPA echoes it in the
 *       {@code X-XSRF-TOKEN} header (double-submit). A cross-site attacker can neither read the cookie
 *       nor set the custom header.</li>
 *   <li>Tokens rendered into responses are XOR-masked (BREACH protection); header values are compared
 *       raw, because the SPA sends the unmasked cookie value.</li>
 *   <li>The deferred token is loaded on every request so the cookie is (re)issued without a separate
 *       round trip — including right after login, when the token is rotated.</li>
 * </ul>
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
