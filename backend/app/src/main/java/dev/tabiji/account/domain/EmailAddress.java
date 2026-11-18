package dev.tabiji.account.domain;

import java.util.Locale;

/** Email normalisation in one place: trimmed and lower-cased, so lookups and the unique index agree. */
public final class EmailAddress {

    private EmailAddress() {}

    public static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
