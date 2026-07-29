package org.nc.nccasino.localization;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocaleIds {
    private static final Pattern LOCALE =
        Pattern.compile("^([A-Za-z]{2,3})(?:[-_]([A-Za-z]{2}))?$");

    private LocaleIds() {
    }

    /**
     * Normalizes common Minecraft locale spellings, e.g. en-us to en_US.
     * Returns {@code null} for malformed identifiers.
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = LOCALE.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        String language = matcher.group(1).toLowerCase(Locale.ROOT);
        String country = matcher.group(2);
        return country == null
            ? language
            : language + "_" + country.toUpperCase(Locale.ROOT);
    }
}
