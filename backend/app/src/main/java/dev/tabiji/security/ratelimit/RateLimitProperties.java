package dev.tabiji.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Token-bucket capacities; each bucket refills greedily over its period. */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("300") int apiPerIpPerMinute,
        @DefaultValue("20") int loginPerIpPerMinute,
        @DefaultValue("5") int loginPerAccountPer15Minutes,
        @DefaultValue("10") int registerPerIpPerHour,
        @DefaultValue("12") int planPerUserPerMinute) {

    public RateLimitProperties {
        if (apiPerIpPerMinute < 1
                || loginPerIpPerMinute < 1
                || loginPerAccountPer15Minutes < 1
                || registerPerIpPerHour < 1
                || planPerUserPerMinute < 1) {
            throw new IllegalArgumentException("rate limits must be positive");
        }
    }
}
