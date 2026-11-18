package dev.tabiji.admin.application;

import dev.tabiji.account.infrastructure.UserAccountRepository;
import dev.tabiji.itinerary.infrastructure.ItineraryPlanRepository;
import dev.tabiji.itinerary.infrastructure.ItineraryRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregate counts for operators. Never exposes individual users' data. */
@Service
public class AdminStatsService {

    private final UserAccountRepository accounts;
    private final ItineraryRepository itineraries;
    private final ItineraryPlanRepository plans;

    public AdminStatsService(
            UserAccountRepository accounts, ItineraryRepository itineraries, ItineraryPlanRepository plans) {
        this.accounts = accounts;
        this.itineraries = itineraries;
        this.plans = plans;
    }

    public record Stats(long users, long itineraries, long plans) {}

    /** Method security as a second layer behind the URL rule in the filter chain. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Stats stats() {
        return new Stats(accounts.count(), itineraries.count(), plans.count());
    }
}
