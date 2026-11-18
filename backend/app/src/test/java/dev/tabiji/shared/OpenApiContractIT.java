package dev.tabiji.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.tabiji.testsupport.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * API contract check. The OpenAPI document generated from the code must equal the committed
 * {@code docs/api/openapi.json}; the frontend's TypeScript types are generated from that file. A
 * change to the API therefore shows up as a reviewed diff instead of silently breaking the client.
 *
 * <p>Regenerate after an intended change: {@code ./mvnw -pl app -am verify -Dit.test=OpenApiContractIT
 * -Dtabiji.openapi.write=true}.
 */
class OpenApiContractIT extends IntegrationTest {

    private static final Path COMMITTED = Path.of("..", "..", "docs", "api", "openapi.json");

    @Test
    void generatedContractMatchesTheCommittedOne() throws Exception {
        String generated =
                mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode tree = json.readTree(generated);
        String pretty = json.writerWithDefaultPrettyPrinter().writeValueAsString(tree) + "\n";

        if (Boolean.getBoolean("tabiji.openapi.write")) {
            Files.createDirectories(COMMITTED.getParent());
            Files.writeString(COMMITTED, pretty, StandardCharsets.UTF_8);
        }
        assertThat(COMMITTED)
                .as("run with -Dtabiji.openapi.write=true to create it")
                .exists();
        JsonNode committed = json.readTree(Files.readString(COMMITTED, StandardCharsets.UTF_8));
        assertThat(tree)
                .as("OpenAPI contract drift: regenerate docs/api/openapi.json")
                .isEqualTo(committed);
        assertThat(tree.get("paths").has("/api/v1/itineraries/{id}/plan")).isTrue();
    }
}
