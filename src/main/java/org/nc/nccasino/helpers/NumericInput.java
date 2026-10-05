package org.nc.nccasino.helpers;

import java.util.OptionalLong;

/**
 * Chat input normalization shared by every numeric prompt, so a player can
 * type numbers the way their keyboard produces them.
 *
 * <p>Every Unicode decimal digit (Arabic-Indic, Persian, Devanagari, Thai,
 * fullwidth, supplementary-plane ones, ...) becomes its ASCII digit, the
 * Arabic decimal separator and fullwidth punctuation become ASCII, and
 * nothing else changes. Keywords and the {@code -1} sentinel are checked by
 * callers before or after this, against the same ASCII text.
 */
public final class NumericInput {
    private static final int ARABIC_DECIMAL_SEPARATOR = 0x066B;
    private static final int FULLWIDTH_COMMA = 0xFF0C;
    private static final int FULLWIDTH_FULL_STOP = 0xFF0E;
    private static final int FULLWIDTH_HYPHEN_MINUS = 0xFF0D;
    private static final int FULLWIDTH_PERCENT = 0xFF05;
    /** Longest digit run that always fits in a {@code long}. */
    private static final int MAX_LONG_DIGITS = 18;

    private NumericInput() {
    }

    /** Trimmed input with every decimal digit and number mark in ASCII form. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        raw.trim().codePoints().forEach(codePoint -> {
            if (Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER) {
                out.append((char) ('0' + Character.digit(codePoint, 10)));
            } else if (codePoint == ARABIC_DECIMAL_SEPARATOR || codePoint == FULLWIDTH_FULL_STOP) {
                out.append('.');
            } else if (codePoint == FULLWIDTH_COMMA) {
                out.append(',');
            } else if (codePoint == FULLWIDTH_HYPHEN_MINUS) {
                out.append('-');
            } else if (codePoint == FULLWIDTH_PERCENT) {
                out.append('%');
            } else {
                out.appendCodePoint(codePoint);
            }
        });
        return out.toString();
    }

    /**
     * {@link #normalize} plus a decimal comma read as a decimal point when the
     * text has exactly one comma and no point ({@code 2,5} is 2.5). Anything
     * with both, or several commas, is left alone so the caller rejects it
     * rather than guessing at digit grouping.
     */
    public static String normalizeDecimal(String raw) {
        String text = normalize(raw);
        if (text.indexOf('.') < 0 && text.indexOf(',') == text.lastIndexOf(',')) {
            text = text.replace(',', '.');
        }
        return text;
    }

    /** A plain non-negative whole number in any script, or empty (never throws). */
    public static OptionalLong parseNonNegativeLong(String raw) {
        String text = normalize(raw);
        int start = 0;
        while (start < text.length() - 1 && text.charAt(start) == '0') {
            start++;
        }
        String digits = text.substring(start);
        if (digits.isEmpty() || digits.length() > MAX_LONG_DIGITS || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(digits));
    }
}
