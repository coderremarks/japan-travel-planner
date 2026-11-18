package dev.tabiji.account.infrastructure;

import dev.tabiji.account.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** {@code email} must already be normalised (see {@link dev.tabiji.account.domain.EmailAddress}). */
    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);
}
