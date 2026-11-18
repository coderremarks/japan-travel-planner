package dev.tabiji.catalog.api;

import dev.tabiji.catalog.domain.Area;
import dev.tabiji.catalog.domain.BaseLocation;
import dev.tabiji.catalog.domain.Closure;
import dev.tabiji.catalog.domain.DataSource;
import dev.tabiji.catalog.domain.HolidayPolicy;
import dev.tabiji.catalog.domain.Place;
import dev.tabiji.catalog.domain.PlaceCategory;
import dev.tabiji.catalog.domain.WeeklyHours;
import dev.tabiji.shared.i18n.LocalizedText;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Response bodies of the catalogue endpoints. */
public final class CatalogDtos {

    private CatalogDtos() {}

    public record AreaResponse(String code, LocalizedText name, double lat, double lng) {

        public static AreaResponse from(Area area) {
            return new AreaResponse(
                    area.code(), LocalizedText.of(area.nameEn(), area.nameJa()), area.centerLat(), area.centerLng());
        }
    }

    public record BaseLocationResponse(
            UUID id, String slug, String areaCode, LocalizedText name, double lat, double lng) {

        public static BaseLocationResponse from(BaseLocation base) {
            return new BaseLocationResponse(
                    base.id(),
                    base.slug(),
                    base.areaCode(),
                    LocalizedText.of(base.nameEn(), base.nameJa()),
                    base.latitude(),
                    base.longitude());
        }
    }

    /**
     * @param admissionYen approximate entrance fee; check {@code dataSource} before relying on it
     */
    public record PlaceSummaryResponse(
            UUID id,
            String slug,
            String areaCode,
            PlaceCategory category,
            LocalizedText name,
            LocalizedText summary,
            double lat,
            double lng,
            int visitMinutes,
            int admissionYen,
            DataSource dataSource) {

        public static PlaceSummaryResponse from(Place place) {
            return new PlaceSummaryResponse(
                    place.id(),
                    place.slug(),
                    place.areaCode(),
                    place.category(),
                    LocalizedText.of(place.nameEn(), place.nameJa()),
                    LocalizedText.of(place.summaryEn(), place.summaryJa()),
                    place.latitude(),
                    place.longitude(),
                    place.visitMinutes(),
                    place.admissionYen(),
                    place.dataSource());
        }
    }

    /** {@code opens}/{@code closes} as {@code HH:mm}; {@code closes} may be {@code 24:00}. */
    public record OpeningHoursResponse(int dayOfWeek, String opens, String closes) {

        static OpeningHoursResponse from(WeeklyHours hours) {
            return new OpeningHoursResponse(hours.dayOfWeek(), clock(hours.opensMinute()), clock(hours.closesMinute()));
        }

        private static String clock(int minute) {
            return "%02d:%02d".formatted(minute / 60, minute % 60);
        }
    }

    public record PlaceDetailResponse(
            PlaceSummaryResponse place,
            List<OpeningHoursResponse> openingHours,
            List<LocalDate> closures,
            HolidayPolicy holidayPolicy,
            LocalDate verifiedOn) {

        public PlaceDetailResponse {
            openingHours = List.copyOf(openingHours);
            closures = List.copyOf(closures);
        }

        public static PlaceDetailResponse from(Place place) {
            return new PlaceDetailResponse(
                    PlaceSummaryResponse.from(place),
                    place.openingHours().stream()
                            .sorted(Comparator.comparingInt(WeeklyHours::dayOfWeek)
                                    .thenComparingInt(WeeklyHours::opensMinute))
                            .map(OpeningHoursResponse::from)
                            .toList(),
                    place.closures().stream().map(Closure::closedOn).sorted().toList(),
                    place.holidayPolicy(),
                    place.verifiedOn());
        }
    }
}
