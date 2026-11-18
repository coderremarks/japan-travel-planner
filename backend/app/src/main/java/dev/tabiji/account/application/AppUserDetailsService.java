package dev.tabiji.account.application;

import dev.tabiji.account.domain.EmailAddress;
import dev.tabiji.account.domain.UserAccount;
import dev.tabiji.account.infrastructure.UserAccountRepository;
import dev.tabiji.security.AppUserPrincipal;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads users for Spring Security and upgrades outdated password hashes on successful login. For an
 * unknown email, Spring's {@code DaoAuthenticationProvider} still runs a dummy hash comparison, so
 * response timing does not reveal whether an account exists.
 */
@Service
public class AppUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UserAccountRepository accounts;
    private final Clock clock;

    public AppUserDetailsService(UserAccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return accounts.findByEmail(EmailAddress.normalize(email))
                .map(AppUserDetailsService::toPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("unknown account"));
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails user, @Nullable String newPasswordHash) {
        if (newPasswordHash == null) {
            return user;
        }
        UserAccount account = accounts.findByEmail(EmailAddress.normalize(user.getUsername()))
                .orElseThrow(() -> new UsernameNotFoundException("unknown account"));
        account.replacePasswordHash(newPasswordHash, clock.instant());
        return toPrincipal(account);
    }

    static AppUserPrincipal toPrincipal(UserAccount account) {
        return new AppUserPrincipal(
                account.id(),
                account.email(),
                account.passwordHash(),
                account.displayName(),
                account.role().name(),
                account.preferredLocale().tag());
    }
}
