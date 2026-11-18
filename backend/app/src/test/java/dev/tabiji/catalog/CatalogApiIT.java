package dev.tabiji.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tabiji.testsupport.IntegrationTest;
import dev.tabiji.testsupport.TestBrowser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** The public catalogue, including search-injection and validation edge cases. */
class CatalogApiIT extends IntegrationTest {

    // MockMvc percent-encodes the URI itself, so query values are written unencoded here.

    private TestBrowser anonymous;

    @BeforeEach
    void anonymousVisitor() {
        anonymous = browser();
    }

    @Test
    void areasAreListedInDisplayOrder() throws Exception {
        JsonNode areas = anonymous.body(anonymous.get("/api/v1/areas"));
        List<String> codes = new ArrayList<>();
        areas.forEach(area -> codes.add(area.get("code").asString()));
        assertThat(codes).containsExactly("TOKYO", "KAMAKURA", "KYOTO", "OSAKA", "NARA");
        assertThat(areas.get(2).get("name").get("ja").asString()).isEqualTo("京都");
    }

    @Test
    void filtersCombine() throws Exception {
        JsonNode page = anonymous.body(anonymous.get("/api/v1/places?area=KYOTO&category=CULTURE&size=50"));
        assertThat(page.get("totalElements").asLong()).isGreaterThan(3);
        page.get("content").forEach(place -> {
            assertThat(place.get("areaCode").asString()).isEqualTo("KYOTO");
            assertThat(place.get("category").asString()).isEqualTo("CULTURE");
            assertThat(place.get("dataSource").asString()).isEqualTo("SAMPLE_UNVERIFIED");
        });
    }

    @Test
    void searchMatchesJapaneseAndEnglishNames() throws Exception {
        assertThat(slugs("q=清水")).contains("kiyomizu-dera");
        assertThat(slugs("q=golden")).containsExactly("kinkaku-ji");
    }

    @Test
    void likeWildcardsInSearchAreTreatedLiterally() throws Exception {
        assertThat(total("q=%")).isZero();
        assertThat(total("q=_")).isZero();
    }

    @Test
    void sqlInjectionAttemptsAreJustSearchText() throws Exception {
        MvcResult result = anonymous.get("/api/v1/places?q=' OR '1'='1' --");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(anonymous.body(result).get("totalElements").asLong()).isZero();
        assertThat(total("")).isEqualTo(44);
    }

    @Test
    void placeDetailsIncludeOpeningRulesAndProvenance() throws Exception {
        String id = anonymous
                .body(anonymous.get("/api/v1/places?q=tokyo-national-museum"))
                .get("content")
                .get(0)
                .get("id")
                .asString();
        JsonNode detail = anonymous.body(anonymous.get("/api/v1/places/" + id));

        assertThat(detail.get("holidayPolicy").asString()).isEqualTo("OPEN_ON_HOLIDAY_CLOSE_NEXT_DAY");
        assertThat(detail.get("openingHours")).hasSize(6);
        detail.get("openingHours")
                .forEach(hours -> assertThat(hours.get("dayOfWeek").asInt()).isNotEqualTo(1));
        assertThat(detail.get("closures").toString()).contains("2026-12-31");
    }

    @Test
    void invalidParametersAreRejected() throws Exception {
        MvcResult tooLarge = anonymous.get("/api/v1/places?size=500");
        assertThat(tooLarge.getResponse().getStatus()).isEqualTo(400);
        assertThat(anonymous.body(tooLarge).get("code").asString()).isEqualTo("VALIDATION_FAILED");

        assertThat(anonymous.get("/api/v1/places?area=tokyo;drop").getResponse().getStatus())
                .isEqualTo(400);
        assertThat(anonymous.get("/api/v1/places/not-a-uuid").getResponse().getStatus())
                .isEqualTo(400);

        MvcResult missing = anonymous.get("/api/v1/places/" + UUID.randomUUID());
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        assertThat(anonymous.body(missing).get("code").asString()).isEqualTo("PLACE_NOT_FOUND");
    }

    private List<String> slugs(String query) throws Exception {
        List<String> slugs = new ArrayList<>();
        anonymous
                .body(anonymous.get("/api/v1/places?size=50&" + query))
                .get("content")
                .forEach(place -> slugs.add(place.get("slug").asString()));
        return slugs;
    }

    private long total(String query) throws Exception {
        return anonymous
                .body(anonymous.get("/api/v1/places?" + query))
                .get("totalElements")
                .asLong();
    }
}
