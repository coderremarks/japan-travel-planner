package dev.tabiji.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated user as stored in the HTTP session. Holds only what request handling needs —
 * notably the user's ID, which every ownership check uses. The password hash is erased right after
 * authentication so it never lives in the session.
 */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String displayName;
    private final String role;
    private final String preferredLocale;
    private String passwordHash;

    public AppUserPrincipal(
            UUID id, String email, String passwordHash, String displayName, String role, String preferredLocale) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = passwordHash;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.role = Objects.requireNonNull(role, "role");
        this.preferredLocale = Objects.requireNonNull(preferredLocale, "preferredLocale");
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String role() {
        return role;
    }

    public String preferredLocale() {
        return preferredLocale;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    /** Identity is the user ID; never include credentials in equality or {@code toString}. */
    @Override
    public boolean equals(Object other) {
        return other instanceof AppUserPrincipal that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + ", role=" + role + "]";
    }
}
