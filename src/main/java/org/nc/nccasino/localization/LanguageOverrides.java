package org.nc.nccasino.localization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates an administrator's sparse override file for one locale.
 *
 * <p>Overrides replace bundled text key by key, so a bad value would replace
 * good text. A value is accepted only if its key still exists in English, it
 * is non-blank text, its placeholders appear in the same order as English
 * (the same rule the translation tooling enforces), and it has no bidi
 * control characters, which Minecraft draws as visible boxes.
 */
final class LanguageOverrides {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_-]*)}");

    record Result(Map<String, String> accepted, List<String> problems) {
    }

    private LanguageOverrides() {
    }

    /**
     * @param locale the override file's locale, for messages
     * @param values every leaf of the file by dotted key
     * @param english bundled English text for a key, or {@code null} if no such key
     */
    static Result validate(String locale, Map<String, Object> values, Function<String, String> english) {
        Map<String, String> accepted = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_meta")) {
                continue;
            }
            String source = english.apply(key);
            String reason = problem(source, entry.getValue());
            if (reason == null) {
                accepted.put(key, (String) entry.getValue());
            } else {
                problems.add(locale + ":" + key + " " + reason);
            }
        }
        return new Result(Collections.unmodifiableMap(accepted), List.copyOf(problems));
    }

    private static String problem(String source, Object value) {
        if (source == null) {
            return "is not a known key";
        }
        if (!(value instanceof String text)) {
            return "is not text";
        }
        if (text.isBlank()) {
            return "is blank";
        }
        if (!placeholderSequence(text).equals(placeholderSequence(source))) {
            return "must use the placeholders " + placeholderSequence(source) + " in that order";
        }
        if (text.codePoints().anyMatch(LanguageOverrides::isBidiControl)) {
            return "contains a bidi control character, which Minecraft shows as a box";
        }
        return null;
    }

    static List<String> placeholderSequence(String text) {
        List<String> names = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static boolean isBidiControl(int codePoint) {
        return codePoint == 0x200D || codePoint == 0x200E || codePoint == 0x200F || codePoint == 0x061C
            || (codePoint >= 0x202A && codePoint <= 0x202E)
            || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }
}
