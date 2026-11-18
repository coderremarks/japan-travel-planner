package dev.tabiji.security;

import dev.tabiji.shared.error.ErrorCode;
import dev.tabiji.shared.error.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** JSON problem responses for 401 and 403 decided inside the security filter chain (no HTML, no redirects). */
@Component
public class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger securityLog = LoggerFactory.getLogger("security");

    private final ProblemResponseWriter problems;

    public ProblemSecurityHandlers(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        problems.write(response, ErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        if (exception instanceof CsrfException) {
            securityLog.warn("csrf.rejected method={}", request.getMethod());
            problems.write(response, ErrorCode.CSRF_TOKEN_INVALID);
            return;
        }
        securityLog.warn("access.denied method={}", request.getMethod());
        problems.write(response, ErrorCode.FORBIDDEN);
    }
}
