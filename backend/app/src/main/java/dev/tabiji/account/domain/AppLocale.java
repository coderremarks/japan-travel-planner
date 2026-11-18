package dev.tabiji.account.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Supported UI languages; serialised as BCP 47 tags ({@code "en"}, {@code "ja"}). */
public enum AppLocale {
    EN("en"),
    JA("ja");

    private final String tag;

    AppLocale(String tag) {
        this.tag = tag;
    }

    @JsonValue
    public String tag() {
        return tag;
    }

    @JsonCreator
    public static AppLocale fromTag(String tag) {
        for (AppLocale locale : values()) {
            if (locale.tag.equals(tag == null ? null : tag.toLowerCase(Locale.ROOT))) {
                return locale;
            }
        }
        throw new IllegalArgumentException("unsupported locale");
    }
}
