package dev.tabiji.account.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link AppLocale} as its language tag, matching the {@code ck_app_user_locale} constraint. */
@Converter(autoApply = true)
public class AppLocaleConverter implements AttributeConverter<AppLocale, String> {

    @Override
    public String convertToDatabaseColumn(AppLocale locale) {
        return locale == null ? null : locale.tag();
    }

    @Override
    public AppLocale convertToEntityAttribute(String tag) {
        return tag == null ? null : AppLocale.fromTag(tag);
    }
}
