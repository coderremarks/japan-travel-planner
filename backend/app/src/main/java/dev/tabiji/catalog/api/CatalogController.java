package dev.tabiji.catalog.api;

import dev.tabiji.catalog.api.CatalogDtos.AreaResponse;
import dev.tabiji.catalog.api.CatalogDtos.BaseLocationResponse;
import dev.tabiji.catalog.api.CatalogDtos.PlaceDetailResponse;
import dev.tabiji.catalog.api.CatalogDtos.PlaceSummaryResponse;
import dev.tabiji.catalog.application.CatalogService;
import dev.tabiji.catalog.domain.PlaceCategory;
import dev.tabiji.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only catalogue. Rate-limited per IP like every API route. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalogue")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/areas")
    @Operation(summary = "Destination areas")
    public List<AreaResponse> areas() {
        return catalog.areas().stream().map(AreaResponse::from).toList();
    }

    @GetMapping("/base-locations")
    @Operation(summary = "Places to stay (start and end of every trip day)")
    public List<BaseLocationResponse> baseLocations(
            @RequestParam(required = false) @Pattern(regexp = "[A-Z_]{1,32}") String area) {
        return catalog.baseLocations(area).stream()
                .map(BaseLocationResponse::from)
                .toList();
    }

    @GetMapping("/places")
    @Operation(
            summary = "Search places",
            description = "Filters combine with AND; q matches English or Japanese names.")
    public PageResponse<PlaceSummaryResponse> places(
            @RequestParam(required = false) @Pattern(regexp = "[A-Z_]{1,32}") String area,
            @RequestParam(required = false) PlaceCategory category,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return PageResponse.of(catalog.search(area, category, q, page, size), PlaceSummaryResponse::from);
    }

    @GetMapping("/places/{id}")
    @Operation(summary = "Place details with opening hours and data provenance")
    public PlaceDetailResponse place(@PathVariable UUID id) {
        return PlaceDetailResponse.from(catalog.place(id));
    }
}
