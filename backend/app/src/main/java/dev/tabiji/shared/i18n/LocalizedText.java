package dev.tabiji.shared.i18n;

/** A text in both supported languages; the client picks the one matching its locale. */
public record LocalizedText(String en, String ja) {

    public static LocalizedText of(String en, String ja) {
        return new LocalizedText(en, ja);
    }
}
