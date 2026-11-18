package dev.tabiji.shared.error;

import dev.tabiji.shared.web.RequestBodyTooLargeException;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Maps every exception to an RFC 9457 problem with a stable {@code code}. Nothing from an exception
 * message, stack trace or SQL error ever reaches the client; unexpected errors are logged with the
 * request ID that the client receives, so support can correlate them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern SAFE_FIELD_NAME = Pattern.compile("[A-Za-z0-9_.\\[\\]]{1,64}");

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        ProblemDetail problem = Problems.of(exception.code(), exception.getMessage(), exception.violations());
        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(exception.code().status());
        if (exception.retryAfterSeconds() > 0) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()));
        }
        return response.body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException exception) {
        return problem(ErrorCode.CONCURRENT_MODIFICATION);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrityViolation(DataIntegrityViolationException exception) {
        // Constraint names and SQL are logged, never returned.
        log.warn(
                "Data integrity violation: {}",
                exception.getMostSpecificCause().getClass().getSimpleName());
        return problem(ErrorCode.CONFLICT);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException exception) {
        return problem(ErrorCode.INVALID_CREDENTIALS);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception) {
        return problem(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception) {
        return problem(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest()
                .body(Problems.of(
                        ErrorCode.VALIDATION_FAILED,
                        ErrorCode.VALIDATION_FAILED.defaultDetail(),
                        List.of(new FieldViolation(safeFieldName(exception.getName()), "TypeMismatch"))));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problem(ErrorCode.INTERNAL_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        return ResponseEntity.badRequest()
                .body(Problems.of(
                        ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultDetail(), violations));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldViolation> violations = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                safeFieldName(result.getMethodParameter().getParameterName()),
                                constraintName(error.getCodes()))))
                .toList();
        return ResponseEntity.badRequest()
                .body(Problems.of(
                        ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultDetail(), violations));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof RequestBodyTooLargeException) {
                return ResponseEntity.status(ErrorCode.PAYLOAD_TOO_LARGE.status())
                        .body(Problems.of(ErrorCode.PAYLOAD_TOO_LARGE));
            }
            if (cause instanceof UnrecognizedPropertyException unknown) {
                return ResponseEntity.badRequest()
                        .body(Problems.of(
                                ErrorCode.UNKNOWN_FIELD,
                                ErrorCode.UNKNOWN_FIELD.defaultDetail(),
                                List.of(new FieldViolation(safeFieldName(unknown.getPropertyName()), "Unknown"))));
            }
            cause = cause.getCause();
        }
        return ResponseEntity.badRequest().body(Problems.of(ErrorCode.MALFORMED_REQUEST));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.badRequest()
                .body(Problems.of(
                        ErrorCode.VALIDATION_FAILED,
                        ErrorCode.VALIDATION_FAILED.defaultDetail(),
                        List.of(new FieldViolation(safeFieldName(exception.getParameterName()), "Required"))));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status()).body(Problems.of(ErrorCode.NOT_FOUND));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.status())
                .headers(headers)
                .body(Problems.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.status())
                .body(Problems.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(ErrorCode.NOT_ACCEPTABLE.status()).body(Problems.of(ErrorCode.NOT_ACCEPTABLE));
    }

    /** Remaining framework exceptions keep their status but get the project's problem format. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        ErrorCode code = statusCode.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.MALFORMED_REQUEST;
        if (statusCode.is5xxServerError()) {
            log.error("Framework error", exception);
        }
        ProblemDetail problem = Problems.of(code);
        problem.setStatus(statusCode.value());
        return ResponseEntity.status(statusCode).headers(headers).body(problem);
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode code) {
        return ResponseEntity.status(code.status()).body(Problems.of(code));
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(
                safeFieldName(error.getField()), error.getCode() == null ? "Invalid" : error.getCode());
    }

    private static String safeFieldName(@Nullable String name) {
        return name != null && SAFE_FIELD_NAME.matcher(name).matches() ? name : "request";
    }

    /** Message codes end with the most generic one, e.g. {@code "Max"}; never echo values. */
    private static String constraintName(String @Nullable [] codes) {
        if (codes == null || codes.length == 0) {
            return "Invalid";
        }
        return lastSegment(codes[codes.length - 1]);
    }

    private static String lastSegment(String code) {
        int dot = code.lastIndexOf('.');
        return dot >= 0 ? code.substring(dot + 1) : code;
    }
}
