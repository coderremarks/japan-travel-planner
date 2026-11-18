package dev.tabiji.demo;

import dev.tabiji.account.application.AccountService;
import dev.tabiji.account.domain.AppLocale;
import dev.tabiji.account.domain.UserAccount;
import dev.tabiji.account.infrastructure.UserAccountRepository;
import dev.tabiji.catalog.domain.PlaceCategory;
import dev.tabiji.catalog.infrastructure.BaseLocationRepository;
import dev.tabiji.catalog.infrastructure.PlaceRepository;
import dev.tabiji.itinerary.application.ItineraryService;
import dev.tabiji.itinerary.domain.ItineraryDraft;
import dev.tabiji.itinerary.domain.ItineraryStop;
import dev.tabiji.optimizer.travel.TravelMode;
import dev.tabiji.shared.error.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local demo convenience: creates {@code demo@tabiji.example} with one sample trip. Enabled only by
 * {@code app.demo.enabled=true} (the {@code local} profile) and only if a password is supplied via
 * {@code TABIJI_DEMO_PASSWORD} — there is no default password in the code base.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final AccountService accounts;
    private final UserAccountRepository accountRepository;
    private final ItineraryService itineraries;
    private final PlaceRepository places;
    private final BaseLocationRepository baseLocations;
    private final Clock clock;
    private final String email;
    private final String password;

    DemoDataInitializer(
            AccountService accounts,
            UserAccountRepository accountRepository,
            ItineraryService itineraries,
            PlaceRepository places,
            BaseLocationRepository baseLocations,
            Clock clock,
            @Value("${app.demo.email}") String email,
            @Value("${app.demo.password:}") String password) {
        this.accounts = accounts;
        this.accountRepository = accountRepository;
        this.itineraries = itineraries;
        this.places = places;
        this.baseLocations = baseLocations;
        this.clock = clock;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.info("Demo data skipped: set TABIJI_DEMO_PASSWORD to create the demo account");
            return;
        }
        if (accountRepository.findByEmail(email).isPresent()) {
            return;
        }
        UserAccount demo;
        try {
            demo = accounts.register(email, password, "Demo Traveller", AppLocale.EN);
        } catch (ApiException rejected) {
            // A local convenience must not stop the application; say why, never echo the password.
            log.warn("Demo data skipped: TABIJI_DEMO_PASSWORD was rejected ({})", rejected.violations());
            return;
        }
        Optional<java.util.UUID> base = baseLocations.findAll().stream()
                .filter(location -> location.slug().equals("asakusa"))
                .map(location -> location.id())
                .findFirst();
        if (base.isEmpty()) {
            return;
        }
        List<ItineraryStop> stops = places.findAll().stream()
                .filter(place -> List.of(
                                "senso-ji",
                                "tokyo-skytree",
                                "tokyo-national-museum",
                                "ueno-park",
                                "akihabara",
                                "meiji-jingu",
                                "shibuya-crossing",
                                "tsukiji-outer-market")
                        .contains(place.slug()))
                .map(place -> new ItineraryStop(place.id(), place.slug().equals("senso-ji")))
                .toList();
        LocalDate start =
                LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Tokyo"))).plusDays(30);
        itineraries.create(
                demo.id(),
                new ItineraryDraft(
                        "Tokyo classics in 3 days",
                        start,
                        start.plusDays(2),
                        LocalTime.of(9, 0),
                        LocalTime.of(19, 0),
                        30_000,
                        EnumSet.of(PlaceCategory.CULTURE, PlaceCategory.FOOD),
                        EnumSet.of(TravelMode.WALK, TravelMode.TRANSIT),
                        4,
                        base.get(),
                        true,
                        stops));
        log.info("Demo account {} created", email);
    }
}
