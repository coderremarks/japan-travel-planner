package dev.tabiji.itinerary.application;

import dev.tabiji.itinerary.domain.PlanDocument;
import dev.tabiji.itinerary.domain.PlanDocument.Day;
import dev.tabiji.itinerary.domain.PlanDocument.Leg;
import dev.tabiji.itinerary.domain.PlanDocument.Visit;
import dev.tabiji.shared.i18n.LocalizedText;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * iCalendar (RFC 5545) export of a plan: one VEVENT per visit, local Japan time with an embedded
 * VTIMEZONE so calendar apps place events correctly wherever the traveller is.
 *
 * <p>Security: every user-influenced value goes through {@link #text(String)}, which escapes
 * {@code \ ; ,} and newlines and drops other control characters. Without it, a title containing
 * {@code "\r\nATTENDEE:..."} could inject arbitrary calendar properties (CRLF injection).
 */
public final class CalendarExporter {

    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT);
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);

    private CalendarExporter() {}

    public static String export(String itineraryTitle, PlanDocument plan, boolean japanese) {
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Tabiji//Japan Travel Planner//EN");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "X-WR-CALNAME:" + text(itineraryTitle));
        line(ics, "X-WR-TIMEZONE:Asia/Tokyo");
        line(ics, "BEGIN:VTIMEZONE");
        line(ics, "TZID:Asia/Tokyo");
        line(ics, "BEGIN:STANDARD");
        line(ics, "DTSTART:19700101T000000");
        line(ics, "TZOFFSETFROM:+0900");
        line(ics, "TZOFFSETTO:+0900");
        line(ics, "TZNAME:JST");
        line(ics, "END:STANDARD");
        line(ics, "END:VTIMEZONE");
        String stamp = UTC.format(plan.generatedAt().atOffset(ZoneOffset.UTC));
        for (Day day : plan.days()) {
            for (int i = 0; i < day.visits().size(); i++) {
                Visit visit = day.visits().get(i);
                line(ics, "BEGIN:VEVENT");
                line(ics, "UID:" + plan.itineraryId() + "-" + day.dayIndex() + "-" + i + "@tabiji.dev");
                line(ics, "DTSTAMP:" + stamp);
                line(ics, "DTSTART;TZID=Asia/Tokyo:" + LOCAL.format(LocalDateTime.of(day.date(), visit.start())));
                line(ics, "DTEND;TZID=Asia/Tokyo:" + LOCAL.format(LocalDateTime.of(day.date(), visit.end())));
                line(ics, "SUMMARY:" + text(name(visit.name(), japanese)));
                line(ics, "LOCATION:" + text(name(visit.name(), japanese)));
                line(ics, String.format(Locale.ROOT, "GEO:%.6f;%.6f", visit.lat(), visit.lng()));
                line(ics, "DESCRIPTION:" + text(description(visit.travel(), japanese)));
                line(ics, "END:VEVENT");
            }
        }
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private static String name(LocalizedText text, boolean japanese) {
        return japanese ? text.ja() : text.en();
    }

    private static String description(Leg leg, boolean japanese) {
        String mode = leg.mode().name().toLowerCase(Locale.ROOT);
        return japanese
                ? "前の場所からの移動（推定）: " + mode + " 約" + leg.minutes() + "分 / 約" + leg.costYen() + "円。"
                        + "所要時間・料金・営業時間は推定値です。出発前に公式情報をご確認ください。"
                : "Travel from previous stop (estimate): " + mode + " ~" + leg.minutes() + " min / ~¥" + leg.costYen()
                        + ". Times, fares and opening hours are estimates; check official sources before you go.";
    }

    /** RFC 5545 §3.3.11 TEXT escaping; strips all other control characters. */
    static String text(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case ';' -> escaped.append("\\;");
                case ',' -> escaped.append("\\,");
                case '\n' -> escaped.append("\\n");
                default -> {
                    if (!Character.isISOControl(c)) {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /** Appends a content line, folded at 75 octets without splitting a UTF-8 character (RFC 5545 §3.1). */
    private static void line(StringBuilder out, String content) {
        int octets = 0;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            int width = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + width > MAX_LINE_OCTETS) {
                out.append(CRLF).append(' ');
                octets = 1;
            }
            out.appendCodePoint(codePoint);
            octets += width;
            i += Character.charCount(codePoint);
        }
        out.append(CRLF);
    }
}
