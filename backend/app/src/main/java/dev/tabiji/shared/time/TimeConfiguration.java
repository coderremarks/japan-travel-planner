package dev.tabiji.shared.time;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A single injectable {@link Clock}: production uses UTC, tests can substitute a fixed clock. */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    /** All itinerary dates and opening hours are local Japan time (no daylight-saving time). */
    public static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
