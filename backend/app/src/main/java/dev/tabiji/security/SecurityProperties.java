package dev.tabiji.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param corsAllowedOrigins origins allowed to call the API cross-origin with credentials. Empty by
 *                           default: the browser talks to the API through the same-origin proxy.
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(@DefaultValue({}) List<String> corsAllowedOrigins) {

    public SecurityProperties {
        corsAllowedOrigins = List.copyOf(corsAllowedOrigins);
        if (corsAllowedOrigins.contains("*")) {
            // A wildcard origin together with credentials would let any site act on behalf of a user.
            throw new IllegalArgumentException("app.security.cors-allowed-origins must not contain '*'");
        }
    }
}
