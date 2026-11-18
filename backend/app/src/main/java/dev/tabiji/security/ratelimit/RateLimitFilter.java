package dev.tabiji.security.ratelimit;

import dev.tabiji.security.ratelimit.RateLimiter.Policy;
import dev.tabiji.shared.error.ErrorCode;
import dev.tabiji.shared.error.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP limits applied before authentication, so that brute-force and enumeration attempts are
 * throttled before any password hashing happens. The client IP is the TCP peer as rewritten by
 * Tomcat's {@code RemoteIpValve}, which trusts {@code X-Forwarded-For} only from internal proxies.
 * Per-account and per-user limits are applied where the account/user is known (login, planning).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ProblemResponseWriter problems;

    public RateLimitFilter(RateLimiter rateLimiter, ProblemResponseWriter problems) {
        this.rateLimiter = rateLimiter;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        long retryAfter = rateLimiter.tryConsume(Policy.API_PER_IP, ip);
        if (retryAfter == 0 && "POST".equals(request.getMethod())) {
            String path = request.getRequestURI();
            if (path.equals("/api/v1/auth/login")) {
                retryAfter = rateLimiter.tryConsume(Policy.LOGIN_PER_IP, ip);
            } else if (path.equals("/api/v1/auth/register")) {
                retryAfter = rateLimiter.tryConsume(Policy.REGISTER_PER_IP, ip);
            }
        }
        if (retryAfter > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            problems.write(response, ErrorCode.TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request, response);
    }
}
