package dev.tabiji.testsupport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Builds itinerary request bodies from catalogue data fetched through the public API. */
public final class Trips {

    private final TestBrowser browser;

    public Trips(TestBrowser browser) {
        this.browser = browser;
    }

    public String placeId(String slug) throws Exception {
        JsonNode page = browser.body(browser.get("/api/v1/places?size=50&q=" + slug));
        for (JsonNode place : page.get("content")) {
            if (place.get("slug").asString().equals(slug)) {
                return place.get("id").asString();
            }
        }
        throw new IllegalArgumentException("unknown place " + slug);
    }

    public String baseLocationId(String slug) throws Exception {
        for (JsonNode base : browser.body(browser.get("/api/v1/base-locations"))) {
            if (base.get("slug").asString().equals(slug)) {
                return base.get("id").asString();
            }
        }
        throw new IllegalArgumentException("unknown base location " + slug);
    }

    /**
     * A Tokyo trip starting on {@code start}; {@code mustVisitSlugs} are mandatory, the rest optional.
     */
    public Map<String, Object> tokyoTrip(LocalDate start, int days, List<String> slugs, List<String> mustVisitSlugs)
            throws Exception {
        List<Map<String, Object>> stops = new ArrayList<>();
        for (String slug : slugs) {
            stops.add(Map.of("placeId", placeId(slug), "mandatory", mustVisitSlugs.contains(slug)));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Tokyo test trip");
        body.put("startDate", start.toString());
        body.put("endDate", start.plusDays(days - 1L).toString());
        body.put("dayStart", "09:00");
        body.put("dayEnd", "19:00");
        body.put("budgetYen", 30000);
        body.put("interests", List.of("CULTURE", "MUSEUM"));
        body.put("travelModes", List.of("WALK", "TRANSIT"));
        body.put("maxVisitsPerDay", 4);
        body.put("baseLocationId", baseLocationId("ueno"));
        body.put("includeSuggestions", false);
        body.put("stops", stops);
        return body;
    }
}
