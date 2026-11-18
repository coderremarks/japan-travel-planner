package dev.tabiji.shared.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes. The frontend translates them; clients must not parse the
 * English {@code detail} text. Adding a code is backwards compatible, renaming one is not.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request contains invalid values."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read."),
    UNKNOWN_FIELD(HttpStatus.BAD_REQUEST, "The request contains a field that is not allowed."),
    PASSWORD_TOO_WEAK(HttpStatus.BAD_REQUEST, "The password does not meet the password policy."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "The email address or password is incorrect."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "The CSRF token is missing or invalid."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource does not exist."),
    ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "The itinerary does not exist."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "The place does not exist."),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "This itinerary has not been planned yet."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The HTTP method is not supported for this resource."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "The requested representation is not available."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email address already exists."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "The resource was changed by another request. Reload and try again."),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state of the resource."),
    VERSION_MISMATCH(
            HttpStatus.PRECONDITION_FAILED, "The itinerary was changed since you loaded it. Reload and try again."),
    VERSION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "Send an If-Match header with the version you edited."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The request body is too large."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The content type is not supported."),
    NOTHING_TO_PLAN(
            HttpStatus.UNPROCESSABLE_CONTENT, "Add at least one place, or enable suggestions, before planning."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait and try again."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus status;
    private final String defaultDetail;

    ErrorCode(HttpStatus status, String defaultDetail) {
        this.status = status;
        this.defaultDetail = defaultDetail;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultDetail() {
        return defaultDetail;
    }

    /** RFC 9457 problem type, e.g. {@code urn:tabiji:problem:itinerary-not-found}. */
    public String typeUri() {
        return "urn:tabiji:problem:" + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
