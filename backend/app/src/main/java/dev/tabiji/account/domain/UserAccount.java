package dev.tabiji.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A registered traveller. There is no setter for {@code role}: nothing a client sends can change it. */
@Entity
@Table(name = "app_user")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "preferred_locale", nullable = false, length = 5)
    private AppLocale preferredLocale;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected UserAccount() {
        // for JPA
    }

    private UserAccount(
            String email, String passwordHash, String displayName, Role role, AppLocale locale, Instant now) {
        this.id = UUID.randomUUID();
        this.email = EmailAddress.normalize(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.displayName = displayName.strip();
        this.role = role;
        this.preferredLocale = locale;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserAccount register(
            String email, String passwordHash, String displayName, AppLocale locale, Instant now) {
        return new UserAccount(email, passwordHash, displayName, Role.USER, locale, now);
    }

    /** Only for seeding operator accounts from trusted configuration, never from an HTTP request. */
    public static UserAccount administrator(String email, String passwordHash, String displayName, Instant now) {
        return new UserAccount(email, passwordHash, displayName, Role.ADMIN, AppLocale.EN, now);
    }

    public void replacePasswordHash(String newHash, Instant now) {
        this.passwordHash = Objects.requireNonNull(newHash, "newHash");
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public Role role() {
        return role;
    }

    public AppLocale preferredLocale() {
        return preferredLocale;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
