package dev.tabiji.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.tabiji.shared.error.ApiException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * In-memory token-bucket rate limiting (Bucket4j). Buckets live in a size-bounded, expiring Caffeine
 * cache, so an attacker rotating keys (IPs, emails) cannot exhaust memory.
 *
 * <p>Limitation (documented in the threat model): buckets are per instance; with N replicas the
 * effective limit is N times higher. Bucket4j's JDBC/Redis back-ends are the scale-out path.
 */
@Component
public class RateLimiter {

    /** What is being limited; each policy has its own bucket per key. */
    public enum Policy {
        API_PER_IP,
        LOGIN_PER_IP,
        LOGIN_PER_ACCOUNT,
        REGISTER_PER_IP,
        PLAN_PER_USER
    }

    private record Limit(long capacity, Duration period) {}

    private final Map<Policy, Limit> limits = new EnumMap<>(Policy.class);
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    public RateLimiter(RateLimitProperties properties) {
        limits.put(Policy.API_PER_IP, new Limit(properties.apiPerIpPerMinute(), Duration.ofMinutes(1)));
        limits.put(Policy.LOGIN_PER_IP, new Limit(properties.loginPerIpPerMinute(), Duration.ofMinutes(1)));
        limits.put(
                Policy.LOGIN_PER_ACCOUNT, new Limit(properties.loginPerAccountPer15Minutes(), Duration.ofMinutes(15)));
        limits.put(Policy.REGISTER_PER_IP, new Limit(properties.registerPerIpPerHour(), Duration.ofHours(1)));
        limits.put(Policy.PLAN_PER_USER, new Limit(properties.planPerUserPerMinute(), Duration.ofMinutes(1)));
    }

    /**
     * Consumes one token.
     *
     * @return {@code 0} if allowed, otherwise the number of seconds until a token is available
     */
    public long tryConsume(Policy policy, String key) {
        Limit limit = limits.get(policy);
        Bucket bucket = buckets.get(
                policy.name() + '|' + key,
                ignored -> Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(limit.capacity())
                                .refillGreedy(limit.capacity(), limit.period())
                                .build())
                        .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return 0;
        }
        return Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1);
    }

    /** Throws a 429 {@link ApiException} carrying {@code Retry-After} if the limit is exhausted. */
    public void check(Policy policy, String key) {
        long retryAfter = tryConsume(policy, key);
        if (retryAfter > 0) {
            throw ApiException.tooManyRequests(retryAfter);
        }
    }
}
