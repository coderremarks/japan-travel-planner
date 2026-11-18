package dev.tabiji.shared.error;

import java.util.List;
import java.util.Objects;

/** An expected, client-facing failure that maps to an RFC 9457 problem response. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<FieldViolation> violations;
    private final long retryAfterSeconds;

    public ApiException(ErrorCode code) {
        this(code, code.defaultDetail(), List.of(), 0);
    }

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of(), 0);
    }

    private ApiException(ErrorCode code, String detail, List<FieldViolation> violations, long retryAfterSeconds) {
        super(detail, null, false, false);
        this.code = Objects.requireNonNull(code, "code");
        this.violations = List.copyOf(violations);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException validation(List<FieldViolation> violations) {
        return new ApiException(
                ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultDetail(), violations, 0);
    }

    public static ApiException validation(ErrorCode code, List<FieldViolation> violations) {
        return new ApiException(code, code.defaultDetail(), violations, 0);
    }

    public static ApiException tooManyRequests(long retryAfterSeconds) {
        return new ApiException(
                ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.defaultDetail(), List.of(), retryAfterSeconds);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
