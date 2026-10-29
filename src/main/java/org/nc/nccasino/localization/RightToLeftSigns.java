package org.nc.nccasino.localization;

import java.util.Locale;
import java.util.Set;

/**
 * Keeps a signed amount ("-5 emeralds", "+$10.00") reading as a signed number
 * in right-to-left text.
 *
 * <p>Minecraft's client lays out every line with the Unicode bidi algorithm.
 * In a right-to-left line a sign written before a number takes the line's
 * direction and is drawn on the number's far side: "סך הכול: -5 emeralds"
 * shows as "emeralds 5- :סך הכול". A left-to-right mark before the sign would
 * fix it, but Minecraft draws bidi controls as boxes. Written right after the
 * number instead ("5- emeralds"), the same algorithm draws the sign on the
 * number's left: "-5".
 *
 * <p>Only a sign whose nearest strong letter before it is right-to-left is
 * moved. After a left-to-right word (a player name, the currency's name) the
 * number already joins that word's direction and shows its sign correctly.
 */
final class RightToLeftSigns {

    private static final Set<String> RIGHT_TO_LEFT_LANGUAGES = Set.of("ar", "fa", "he", "iw", "yi", "ji", "ur", "ps", "ckb", "sd", "ug", "dv");

    private RightToLeftSigns() {
    }

    static boolean isRightToLeft(String locale) {
        if (locale == null) {
            return false;
        }
        int separator = locale.indexOf('_');
        String language = (separator < 0 ? locale : locale.substring(0, separator)).toLowerCase(Locale.ROOT);
        return RIGHT_TO_LEFT_LANGUAGES.contains(language);
    }

    /** {@code text} with each such sign moved to just after its number, for a right-to-left locale. */
    static String apply(String locale, String text) {
        if (text == null || !isRightToLeft(locale) || (text.indexOf('-') < 0 && text.indexOf('+') < 0)) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int numberEnd = (c == '-' || c == '+') ? signedNumberEnd(text, i) : -1;
            if (numberEnd < 0 || !rightToLeftBefore(text, i)) {
                out.append(c);
                i++;
                continue;
            }
            out.append(text, i + 1, numberEnd).append(c);
            i = numberEnd;
        }
        return out.toString();
    }

    /**
     * Where the number a sign at {@code sign} belongs to ends: the sign must
     * start a token, and be followed by digits (optionally after a currency
     * symbol, as in "-$5.00"). -1 when it isn't a sign.
     */
    private static int signedNumberEnd(String text, int sign) {
        if (sign > 0) {
            char before = text.charAt(sign - 1);
            if (Character.isLetterOrDigit(before) && !isColorCode(text, sign - 1)) {
                return -1; // a hyphen inside a word or a range ("1-5")
            }
        }
        int i = sign + 1;
        if (i < text.length() && Character.getType(text.charAt(i)) == Character.CURRENCY_SYMBOL) {
            i++;
        }
        if (i >= text.length() || !isAsciiDigit(text.charAt(i))) {
            return -1;
        }
        while (i < text.length()) {
            char c = text.charAt(i);
            if (isAsciiDigit(c)) {
                i++;
            } else if ((c == '.' || c == ',') && i + 1 < text.length() && isAsciiDigit(text.charAt(i + 1))) {
                i += 2;
            } else {
                break;
            }
        }
        return i;
    }

    /** Whether the nearest strong letter before {@code index} is right-to-left; color codes are not text. */
    private static boolean rightToLeftBefore(String text, int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (isColorCode(text, i)) {
                i--; // skip the '§' too
                continue;
            }
            byte direction = Character.getDirectionality(text.charAt(i));
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                return true;
            }
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                return false;
            }
        }
        return false;
    }

    /** Whether the character at {@code i} is the code letter of a "§x" color code. */
    private static boolean isColorCode(String text, int i) {
        return i > 0 && text.charAt(i - 1) == '§';
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
