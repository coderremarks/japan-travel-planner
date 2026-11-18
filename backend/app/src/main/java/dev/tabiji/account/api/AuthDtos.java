package dev.tabiji.account.api;

import dev.tabiji.account.domain.AppLocale;
import dev.tabiji.security.AppUserPrincipal;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request and response bodies of the auth endpoints. Request records contain exactly the fields a
 * client may set — there is no way to submit a role, an ID or a password hash.
 */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(max = 128) String password,
            @NotBlank @Size(max = 60) String displayName,
            @NotNull AppLocale preferredLocale) {

        /** Never print the password, even by accident in a log statement. */
        @Override
        public String toString() {
            return "RegisterRequest[displayName=" + displayName + ", preferredLocale=" + preferredLocale + "]";
        }
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest[]";
        }
    }

    public record UserResponse(UUID id, String email, String displayName, String role, String preferredLocale) {

        static UserResponse from(AppUserPrincipal principal) {
            return new UserResponse(
                    principal.id(),
                    principal.email(),
                    principal.displayName(),
                    principal.role(),
                    principal.preferredLocale());
        }
    }

    /** Tells the SPA which cookie holds the CSRF token and which header must echo it. */
    public record CsrfInfoResponse(String cookieName, String headerName) {}
}
