package dev.tabiji.itinerary.api;

import dev.tabiji.itinerary.api.ItineraryDtos.ItineraryExport;
import dev.tabiji.itinerary.api.ItineraryDtos.ItineraryRequest;
import dev.tabiji.itinerary.api.ItineraryDtos.ItineraryResponse;
import dev.tabiji.itinerary.api.ItineraryDtos.ItinerarySummaryResponse;
import dev.tabiji.itinerary.api.ItineraryDtos.PlanRequest;
import dev.tabiji.itinerary.api.ItineraryDtos.PlanResponse;
import dev.tabiji.itinerary.application.CalendarExporter;
import dev.tabiji.itinerary.application.ItineraryService;
import dev.tabiji.itinerary.application.ItineraryService.ItineraryView;
import dev.tabiji.itinerary.application.PlanningService;
import dev.tabiji.itinerary.application.PlanningService.PlanView;
import dev.tabiji.itinerary.domain.PlanDocument;
import dev.tabiji.security.AppUserPrincipal;
import dev.tabiji.shared.api.PageResponse;
import dev.tabiji.shared.error.ApiException;
import dev.tabiji.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.regex.Matcher;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Itineraries of the signed-in user. The user ID always comes from the session principal — never from
 * the URL or the body — and is passed to every service call, which scopes all lookups by owner.
 *
 * <p>Concurrency: responses carry {@code ETag: "<version>"}; {@code PUT} requires {@code If-Match}
 * (428 if missing, 412 if stale), so two browser tabs cannot silently overwrite each other.
 */
@RestController
@RequestMapping("/api/v1/itineraries")
@Tag(name = "Itineraries")
public class ItineraryController {

    private static final java.util.regex.Pattern ETAG = java.util.regex.Pattern.compile("^(?:W/)?\"(\\d{1,18})\"$");

    private final ItineraryService itineraries;
    private final PlanningService planning;
    private final Clock clock;

    public ItineraryController(ItineraryService itineraries, PlanningService planning, Clock clock) {
        this.itineraries = itineraries;
        this.planning = planning;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "List my itineraries, most recently updated first")
    public PageResponse<ItinerarySummaryResponse> list(
            @AuthenticationPrincipal AppUserPrincipal user,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return PageResponse.of(itineraries.list(user.id(), page, size), ItinerarySummaryResponse::from);
    }

    @PostMapping
    @Operation(summary = "Create an itinerary")
    public ResponseEntity<ItineraryResponse> create(
            @AuthenticationPrincipal AppUserPrincipal user, @Valid @RequestBody ItineraryRequest body) {
        ItineraryView view = itineraries.create(user.id(), body.toDraft());
        return created(view);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get one of my itineraries",
            description = "404 for unknown IDs and for other users' IDs alike.")
    public ResponseEntity<ItineraryResponse> get(
            @AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return withEtag(ResponseEntity.ok(), itineraries.get(user.id(), id));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Replace an itinerary's editable fields",
            description = "Requires If-Match with the ETag you loaded.")
    public ResponseEntity<ItineraryResponse> update(
            @AuthenticationPrincipal AppUserPrincipal user,
            @PathVariable UUID id,
            @Parameter(description = "ETag of the version being edited, e.g. \"3\"")
                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false)
                    String ifMatch,
            @Valid @RequestBody ItineraryRequest body) {
        long expectedVersion = parseIfMatch(ifMatch);
        ItineraryView view;
        try {
            view = itineraries.update(user.id(), id, body.toDraft(), expectedVersion);
        } catch (org.springframework.dao.OptimisticLockingFailureException stale) {
            throw new ApiException(ErrorCode.VERSION_MISMATCH);
        }
        return withEtag(ResponseEntity.ok(), view);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an itinerary and its plan")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        itineraries.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Copy an itinerary (without its plan)")
    public ResponseEntity<ItineraryResponse> duplicate(
            @AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        return created(itineraries.duplicate(user.id(), id));
    }

    @PostMapping("/{id}/plan")
    @Operation(
            summary = "Generate a plan",
            description = "OPTIMIZE runs the itinerary optimizer; KEEP_ORDER keeps your order.")
    public PlanResponse plan(
            @AuthenticationPrincipal AppUserPrincipal user,
            @PathVariable UUID id,
            @Valid @RequestBody PlanRequest body) {
        PlanView view = planning.plan(user.id(), id, body.strategy().strategy());
        return new PlanResponse(view.plan(), view.stale());
    }

    @GetMapping("/{id}/plan")
    @Operation(summary = "Latest generated plan", description = "stale=true if the itinerary changed since.")
    public PlanResponse latestPlan(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        PlanView view = planning.latest(user.id(), id);
        return new PlanResponse(view.plan(), view.stale());
    }

    @GetMapping(value = "/{id}/calendar.ics", produces = "text/calendar")
    @Operation(summary = "Export the latest plan as iCalendar")
    public ResponseEntity<byte[]> calendar(
            @AuthenticationPrincipal AppUserPrincipal user,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "en") @Pattern(regexp = "en|ja") String lang) {
        ItineraryView view = itineraries.get(user.id(), id);
        PlanDocument plan = planning.latest(user.id(), id).plan();
        String ics = CalendarExporter.export(view.itinerary().title(), plan, "ja".equals(lang));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("tabiji-itinerary.ics"))
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .body(ics.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/{id}/export.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export the itinerary and its plan as JSON")
    public ResponseEntity<ItineraryExport> exportJson(
            @AuthenticationPrincipal AppUserPrincipal user, @PathVariable UUID id) {
        ItineraryView view = itineraries.get(user.id(), id);
        PlanDocument plan =
                view.plan().isPresent() ? planning.latest(user.id(), id).plan() : null;
        ItineraryExport export = new ItineraryExport(1, clock.instant(), ItineraryResponse.from(view), plan);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("tabiji-itinerary.json"))
                .body(export);
    }

    private static ResponseEntity<ItineraryResponse> created(ItineraryView view) {
        URI location = URI.create("/api/v1/itineraries/" + view.itinerary().id());
        return withEtag(ResponseEntity.created(location), view);
    }

    private static ResponseEntity<ItineraryResponse> withEtag(ResponseEntity.BodyBuilder builder, ItineraryView view) {
        return builder.eTag("\"" + view.itinerary().version() + "\"").body(ItineraryResponse.from(view));
    }

    private static long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(ErrorCode.VERSION_REQUIRED);
        }
        Matcher matcher = ETAG.matcher(ifMatch.strip());
        if (!matcher.matches()) {
            throw new ApiException(ErrorCode.VERSION_MISMATCH);
        }
        return Long.parseLong(matcher.group(1));
    }

    /** ASCII file name: titles may contain anything, so they never go into a header. */
    private static String attachment(String fileName) {
        return ContentDisposition.attachment().filename(fileName).build().toString();
    }
}
