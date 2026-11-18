package dev.tabiji.account.api;

import dev.tabiji.account.api.AuthDtos.CsrfInfoResponse;
import dev.tabiji.account.api.AuthDtos.LoginRequest;
import dev.tabiji.account.api.AuthDtos.RegisterRequest;
import dev.tabiji.account.api.AuthDtos.UserResponse;
import dev.tabiji.account.application.AccountService;
import dev.tabiji.account.domain.EmailAddress;
import dev.tabiji.security.AppUserPrincipal;
import dev.tabiji.security.SessionLogin;
import dev.tabiji.security.ratelimit.RateLimiter;
import dev.tabiji.security.ratelimit.RateLimiter.Policy;
import dev.tabiji.shared.error.ApiException;
import dev.tabiji.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private static final Logger securityLog = LoggerFactory.getLogger("security");

    private final AccountService accountService;
    private final SessionLogin sessionLogin;
    private final RateLimiter rateLimiter;

    public AuthController(AccountService accountService, SessionLogin sessionLogin, RateLimiter rateLimiter) {
        this.accountService = accountService;
        this.sessionLogin = sessionLogin;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Issue the CSRF cookie", description = "Sets the XSRF-TOKEN cookie; echo it in X-XSRF-TOKEN.")
    public CsrfInfoResponse csrf(@Parameter(hidden = true) CsrfToken token) {
        // Accessing the token makes the repository write the cookie.
        token.getToken();
        return new CsrfInfoResponse("XSRF-TOKEN", token.getHeaderName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account and sign in")
    public UserResponse register(
            @Valid @RequestBody RegisterRequest body, HttpServletRequest request, HttpServletResponse response) {
        accountService.register(body.email(), body.password(), body.displayName(), body.preferredLocale());
        AppUserPrincipal principal = sessionLogin.login(body.email(), body.password(), request, response);
        return UserResponse.from(principal);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in", description = "Rotates the session ID and CSRF token on success.")
    public UserResponse login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String account = fingerprint(body.email());
        rateLimiter.check(Policy.LOGIN_PER_ACCOUNT, account);
        try {
            AppUserPrincipal principal = sessionLogin.login(body.email(), body.password(), request, response);
            securityLog.info("login.succeeded userId={}", principal.id());
            return UserResponse.from(principal);
        } catch (AuthenticationException failure) {
            securityLog.warn("login.failed account={}", account);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Current user", description = "401 when not signed in.")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return UserResponse.from(principal);
    }

    /**
     * Short SHA-256 fingerprint of the normalised email: lets logs and the rate limiter correlate
     * attempts against one account without storing the address itself.
     */
    static String fingerprint(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(EmailAddress.normalize(email).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java SE specification", impossible);
        }
    }
}
