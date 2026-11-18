package dev.tabiji.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.1 description. The generated document is committed as {@code docs/api/openapi.json} and
 * checked in CI, and the frontend's TypeScript types are generated from it — a contract both sides
 * are tested against. Every operation documents the shared RFC 9457 problem response.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String PROBLEM = "Problem";

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
                                        .name("TABIJI_SESSION")
                                        .description("__Host-TABIJI_SESSION in production"))
                        .addSecuritySchemes(
                                "csrf",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-XSRF-TOKEN")))
                .addSecurityItem(new SecurityRequirement().addList("session").addList("csrf"));
    }

    /**
     * Registers the problem schema and adds it as the {@code default} response of every operation.
     * The schema is added here rather than in the {@link OpenAPI} bean because springdoc rebuilds
     * the schema map from the controllers and would drop it.
     */
    @Bean
    OpenApiCustomizer problemResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM, problemSchema());
            ApiResponse problem = new ApiResponse()
                    .description("Error (RFC 9457 problem details)")
                    .content(new Content()
                            .addMediaType(
                                    "application/problem+json",
                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))));
            openApi.getPaths()
                    .values()
                    .forEach(path -> path.readOperations()
                            .forEach(operation -> operation.getResponses().addApiResponse("default", problem)));
        };
    }

    /**
     * Response records always serialise every component, so their properties are documented as
     * required — except the few that are legitimately absent. Request schemas keep the requirements
     * derived from Bean Validation. This makes the generated TypeScript types exact.
     */
    @Bean
    OpenApiCustomizer requiredResponseProperties() {
        return openApi -> openApi.getComponents().getSchemas().forEach((name, rawSchema) -> {
            Schema<?> schema = rawSchema; // swagger-models exposes a raw Map<String, Schema>
            if (name.endsWith("Request") || name.equals(PROBLEM) || schema.getProperties() == null) {
                return;
            }
            List<String> required = schema.getProperties().keySet().stream()
                    .filter(property -> !NULLABLE_PROPERTIES.contains(property))
                    .sorted()
                    .toList();
            schema.setRequired(required);
        });
    }

    /** Properties that may be {@code null} in responses: no plan yet, no baseline, not a holiday, unverified. */
    private static final java.util.Set<String> NULLABLE_PROPERTIES =
            java.util.Set.of("plan", "comparison", "holiday", "verifiedOn");

    private static ObjectSchema problemSchema() {
        ObjectSchema violation = new ObjectSchema();
        violation.addProperty("field", new StringSchema());
        violation.addProperty("code", new StringSchema());
        violation.setRequired(List.of("field", "code"));

        ObjectSchema problem = new ObjectSchema();
        problem.addProperty("type", new StringSchema());
        problem.addProperty("title", new StringSchema());
        problem.addProperty("status", new IntegerSchema());
        problem.addProperty("detail", new StringSchema());
        problem.addProperty("instance", new StringSchema());
        problem.addProperty("code", new StringSchema().description("Stable machine-readable error code"));
        problem.addProperty("requestId", new StringSchema());
        problem.addProperty("errors", new ArraySchema().items(violation));
        problem.setRequired(List.of("status", "code"));
        return problem;
    }
}
