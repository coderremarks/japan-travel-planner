package dev.tabiji.shared.error;

import dev.tabiji.shared.web.RequestIdFilter;
import java.net.URI;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem bodies with the project-wide extension members {@code code}, {@code requestId}. */
public final class Problems {

    private Problems() {}

    public static ProblemDetail of(ErrorCode code) {
        return of(code, code.defaultDetail(), List.of());
    }

    public static ProblemDetail of(ErrorCode code, String detail, List<FieldViolation> violations) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create(code.typeUri()));
        problem.setTitle(code.status().getReasonPhrase());
        problem.setProperty("code", code.name());
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        if (!violations.isEmpty()) {
            problem.setProperty("errors", violations);
        }
        return problem;
    }
}
