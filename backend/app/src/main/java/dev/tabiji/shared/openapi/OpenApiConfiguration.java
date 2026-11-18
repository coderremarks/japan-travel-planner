package dev.tabiji.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.1 description. The generated document is committed as {@code docs/api/openapi.json} and
 * checked in CI, and the frontend's TypeScript types are generated from it — a contract both sides
 * are tested against.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI tabijiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tabiji API")
                        .version("v1")
                        .description("Japan Intelligent Travel Planner. Session cookie authentication; "
                                + "state-changing requests need the X-XSRF-TOKEN header (value of the "
                                + "XSRF-TOKEN cookie). Errors are RFC 9457 problem details with a stable `code`.")
                        .license(new License().name("MIT")))
                .components(new Components()
                        .addSecuritySchemes(
                                "session",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("TABIJI_SESSION"))
                        .addSecuritySchemes(
                                "csrf",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-XSRF-TOKEN")))
                .addSecurityItem(new SecurityRequirement().addList("session").addList("csrf"));
    }
}
