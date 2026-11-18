package dev.tabiji.itinerary.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * SHA-256 over a canonical representation of every input that affects a plan. If the stored plan's
 * fingerprint differs from the current itinerary's, the plan is out of date. The title is excluded on
 * purpose: renaming a trip does not invalidate its schedule.
 */
public final class InputFingerprint {

    private InputFingerprint() {}

    public static String of(Itinerary itinerary) {
        String canonical = String.join(
                "|",
                itinerary.startDate().toString(),
                itinerary.endDate().toString(),
                Integer.toString(itinerary.dayStartMinute()),
                Integer.toString(itinerary.dayEndMinute()),
                Integer.toString(itinerary.budgetYen()),
                Integer.toString(itinerary.maxVisitsPerDay()),
                itinerary.baseLocationId().toString(),
                Boolean.toString(itinerary.includeSuggestions()),
                itinerary.interests().stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
                itinerary.travelModes().stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
                itinerary.stops().stream()
                        .map(stop -> stop.placeId() + ":" + stop.mandatory())
                        .collect(Collectors.joining(",")));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java SE specification", impossible);
        }
    }
}
