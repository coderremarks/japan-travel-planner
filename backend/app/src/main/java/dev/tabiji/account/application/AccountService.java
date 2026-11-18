package dev.tabiji.account.application;

import dev.tabiji.account.domain.AppLocale;
import dev.tabiji.account.domain.EmailAddress;
import dev.tabiji.account.domain.PasswordPolicy;
import dev.tabiji.account.domain.UserAccount;
import dev.tabiji.account.infrastructure.UserAccountRepository;
import dev.tabiji.shared.error.ApiException;
import dev.tabiji.shared.error.ErrorCode;
import dev.tabiji.shared.error.FieldViolation;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger securityLog = LoggerFactory.getLogger("security");

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Creates an account. The email check runs before hashing so that a duplicate does not cost an
     * Argon2 computation; the unique index remains the source of truth under concurrency.
     */
    @Transactional
    public UserAccount register(String email, String password, String displayName, AppLocale locale) {
        String normalizedEmail = EmailAddress.normalize(email);
        List<String> passwordViolations = PasswordPolicy.check(password, normalizedEmail);
        if (!passwordViolations.isEmpty()) {
            throw ApiException.validation(
                    ErrorCode.PASSWORD_TOO_WEAK,
                    passwordViolations.stream()
                            .map(code -> new FieldViolation("password", code))
                            .toList());
        }
        if (accounts.existsByEmail(normalizedEmail)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        String passwordHash = passwordEncoder.encode(password);
        if (passwordHash == null) {
            throw new IllegalStateException("password encoder returned no hash");
        }
        UserAccount account = UserAccount.register(normalizedEmail, passwordHash, displayName, locale, clock.instant());
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException raceWithAnotherRegistration) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        securityLog.info("account.registered userId={}", account.id());
        return account;
    }
}
