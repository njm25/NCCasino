package org.nc.nccasino.localization.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rendering rules that Minecraft's client imposes on right-to-left text
 * (see {@code TRANSLATION_GUIDE.md}, right-to-left locales).
 *
 * <p>The client reorders every line with ICU bidi, taking the paragraph
 * direction from the line's first strong character, and draws invisible
 * bidi controls as visible boxes. Its font also has no mark positioning, so
 * combining diacritics render beside their letter.
 */
final class RtlRules {
    private static final Set<String> RTL_LANGUAGES = Set.of("ar", "fa", "he", "yi", "ur", "ps", "ckb", "dv");
    /** Yiddish standard spelling needs points; it uses presentation forms but may keep marks. */
    private static final Set<String> MARKS_ALLOWED = Set.of("yi");

    private RtlRules() {
    }

    static boolean isRightToLeft(String locale) {
        return RTL_LANGUAGES.contains(language(locale));
    }

    /** Problems for one value; {@code locale} decides which rules apply. */
    static List<String> problems(String locale, String key, String value) {
        List<String> problems = new ArrayList<>();
        String context = locale + ":" + key;
        value.codePoints().filter(RtlRules::isBidiControl).findFirst().ifPresent(codePoint -> problems.add(
            context + " contains bidi control U+" + String.format("%04X", codePoint)
                + ", which Minecraft draws as a visible box"
        ));
        if (!isRightToLeft(locale)) {
            return problems;
        }
        // Each chat line is laid out as its own paragraph.
        boolean everyLineStartsRightToLeft = true;
        for (String line : value.split("\n", -1)) {
            everyLineStartsRightToLeft &= !containsRightToLeftLetter(line) || startsRightToLeft(line);
        }
        if (!everyLineStartsRightToLeft) {
            problems.add(
                context + " must start with a right-to-left word; a leading placeholder or Latin text "
                    + "makes Minecraft lay the whole line out left-to-right"
            );
        }
        if (!MARKS_ALLOWED.contains(language(locale))
            && value.codePoints().anyMatch(codePoint -> Character.getType(codePoint) == Character.NON_SPACING_MARK)) {
            problems.add(context + " contains a combining diacritic, which Minecraft draws beside its letter");
        }
        return problems;
    }

    /** Placeholders count as left-to-right: they are often player, dealer or item names. */
    static boolean startsRightToLeft(String value) {
        String text = value.replaceAll("(?i)&[0-9a-fk-or]", "").replaceAll("\\{[A-Za-z][A-Za-z0-9_-]*}", "A");
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            byte direction = Character.getDirectionality(codePoint);
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                return false;
            }
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean containsRightToLeftLetter(String value) {
        return value.codePoints().anyMatch(codePoint -> {
            byte direction = Character.getDirectionality(codePoint);
            return direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
        });
    }

    private static boolean isBidiControl(int codePoint) {
        return codePoint == 0x200D || codePoint == 0x200E || codePoint == 0x200F || codePoint == 0x061C
            || (codePoint >= 0x202A && codePoint <= 0x202E)
            || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }

    private static String language(String locale) {
        return locale.split("_", 2)[0];
    }
}
