package dev.tabiji.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsLongPassphrasesWithoutCompositionRules() {
        assertThat(PasswordPolicy.check("correct horse battery staple", "user@example.test"))
                .isEmpty();
        assertThat(PasswordPolicy.check("さくらさくらやよいのそらは", "user@example.test")).isEmpty();
    }

    @Test
    void rejectsShortCommonAndEmailDerivedPasswords() {
        assertThat(PasswordPolicy.check("short", "a@example.test")).containsExactly("TOO_SHORT");
        assertThat(PasswordPolicy.check("Password1234", "a@example.test")).contains("TOO_COMMON");
        assertThat(PasswordPolicy.check("aaaaaaaaaaaaaaab", "a@example.test")).contains("TOO_COMMON");
        assertThat(PasswordPolicy.check("hanako-in-kyoto-2026", "Hanako@example.test"))
                .contains("CONTAINS_EMAIL");
        assertThat(PasswordPolicy.check("x".repeat(129), "a@example.test")).contains("TOO_LONG");
    }

    @Test
    void countsCharactersNotUtf16Units() {
        String elevenEmoji = "🗻🍣🚄🏯🌸🍵🎎🗾🦌🍜🎐"; // 22 UTF-16 units, but only 11 characters
        assertThat(elevenEmoji.length()).isEqualTo(22);
        assertThat(PasswordPolicy.check(elevenEmoji, "a@example.test")).containsExactly("TOO_SHORT");
        assertThat(PasswordPolicy.check(elevenEmoji + "🐟", "a@example.test")).isEmpty();
    }
}
