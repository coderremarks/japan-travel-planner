package dev.tabiji.account.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Password rules aligned with NIST SP 800-63B and OWASP ASVS 5.0 V6:
 * <ul>
 *   <li>length 12–128 characters (ASVS requires ≥ 8 and recommends 15; 12 balances demo usability);</li>
 *   <li>no composition rules (no "must contain a symbol"), any Unicode allowed, spaces allowed;</li>
 *   <li>reject commonly used passwords and passwords derived from the email address.</li>
 * </ul>
 * Violations are reported as codes, never by echoing the password.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    /** A small offline blocklist; a production system would add a breached-password check (k-anonymity API). */
    private static final Set<String> COMMON = Set.of(
            "password1234",
            "passwordpassword",
            "123456789012",
            "1234567890123",
            "qwertyuiop12",
            "qwertyuiopas",
            "iloveyou1234",
            "letmeinletmein",
            "adminadmin12",
            "welcome12345",
            "tabijitabiji",
            "japantravel1",
            "000000000000",
            "111111111111",
            "aaaaaaaaaaaa");

    private PasswordPolicy() {}

    /** Returns violation codes; empty means the password is acceptable. */
    public static List<String> check(String password, String email) {
        List<String> violations = new ArrayList<>();
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            violations.add("TOO_SHORT");
            return violations;
        }
        if (password.codePointCount(0, password.length()) > MAX_LENGTH) {
            violations.add("TOO_LONG");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || lower.chars().distinct().count() <= 2) {
            violations.add("TOO_COMMON");
        }
        String localPart = EmailAddress.normalize(email).split("@", 2)[0];
        if (localPart.length() >= 4 && lower.contains(localPart)) {
            violations.add("CONTAINS_EMAIL");
        }
        return violations;
    }
}
