package org.nc.nccasino.games.Slots;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * The pure parsers behind every Slots chat prompt, so the accepted syntax is
 * one testable definition rather than a per-prompt regex.
 *
 * <p>Every prompt shares the literal keyword {@code cancel} (abandon the
 * prompt, changing nothing) and, where meaningful, the {@code -1} unbounded
 * sentinel or the literal {@code off}. They are matched case-insensitively
 * and are always checked before the numeric parse, so a locale that renders
 * numbers unusually can never make {@code cancel} unreachable -- and so
 * {@code -1} is read as the sentinel rather than falling through to the
 * numeric parse, which rejects non-positive values.
 *
 * <p>Numbers are parsed defensively rather than with a bare
 * {@code Long.parseLong}/{@code Double.parseDouble}: input is length-capped
 * and shape-checked first, so a pasted thousand-digit number is rejected as
 * out of range instead of being allowed to overflow, produce infinity, or
 * silently round to something the player did not type.
 */
public final class SlotsPromptValues {

    /** Abandons any prompt. */
    public static final String CANCEL = "cancel";
    /**
     * Spin Limit only: no cap on how many spins one batch may commit. This is
     * the {@code -1} sentinel the rest of the plugin already prompts for --
     * Blackjack's max hands and the Rock Paper Scissors / Coin Flip max chain
     * all read the same way -- so an unbounded figure is typed identically
     * everywhere rather than being a word here and a number there. The value
     * is still displayed as "Unlimited"; only the input is the sentinel.
     */
    public static final String UNLIMITED = "-1";
    /** Big-Win Multiplier, Profit Target, Loss Limit: switch the stop condition off. */
    public static final String OFF = "off";
    /** Profile naming only: replace the existing profile with the same name. */
    public static final String OVERWRITE = "overwrite";

    /** Longer than any legal number here; anything above this is rejected before parsing. */
    private static final int MAX_NUMERIC_LENGTH = 20;

    /** The largest spin limit that can be stored and counted safely. */
    public static final long MAX_SPIN_LIMIT = Long.MAX_VALUE;

    /** The largest currency amount / multiplier a prompt accepts. */
    private static final BigDecimal MAX_DECIMAL = BigDecimal.valueOf(1e15);

    private SlotsPromptValues() {
    }

    /** What one submitted line turned out to be. */
    public enum Kind {
        /** A real value; read it from the result. */
        VALUE,
        /** The literal {@code unlimited}. */
        UNLIMITED,
        /** The literal {@code off}. */
        OFF,
        /** The literal {@code cancel}. */
        CANCEL,
        /** Unparseable, out of range, or the wrong shape. */
        INVALID
    }

    public record SpinLimit(Kind kind, long value) {
    }

    public record Amount(Kind kind, double value) {
    }

    public static boolean isCancel(String input) {
        return matches(input, CANCEL);
    }

    public static boolean isOverwrite(String input) {
        return matches(input, OVERWRITE);
    }

    private static boolean matches(String input, String keyword) {
        return input != null && input.trim().toLowerCase(Locale.ROOT).equals(keyword);
    }

    /**
     * Spin Limit: any positive whole number, the {@code -1} unbounded
     * sentinel, or {@code cancel}. There is deliberately no small UI
     * maximum -- only the storage range itself bounds the value.
     */
    public static SpinLimit parseSpinLimit(String input) {
        if (input == null) {
            return new SpinLimit(Kind.INVALID, 0L);
        }
        String trimmed = input.trim();
        if (isCancel(trimmed)) {
            return new SpinLimit(Kind.CANCEL, 0L);
        }
        if (matches(trimmed, UNLIMITED)) {
            return new SpinLimit(Kind.UNLIMITED, SlotsAutoSpinSettings.UNLIMITED_SPINS);
        }
        String digits = stripLeadingZeros(trimmed);
        if (digits == null || digits.length() > MAX_NUMERIC_LENGTH) {
            return new SpinLimit(Kind.INVALID, 0L);
        }
        try {
            long value = Long.parseLong(digits);
            if (value <= 0) {
                return new SpinLimit(Kind.INVALID, 0L);
            }
            return new SpinLimit(Kind.VALUE, value);
        } catch (NumberFormatException e) {
            return new SpinLimit(Kind.INVALID, 0L);
        }
    }

    /**
     * Big-Win Multiplier, Profit Target and Loss Limit all share this shape:
     * a positive decimal, {@code off}, or {@code cancel}.
     */
    public static Amount parsePositiveAmount(String input) {
        if (input == null) {
            return new Amount(Kind.INVALID, 0.0);
        }
        String trimmed = input.trim();
        if (isCancel(trimmed)) {
            return new Amount(Kind.CANCEL, 0.0);
        }
        if (matches(trimmed, OFF)) {
            return new Amount(Kind.OFF, 0.0);
        }
        if (trimmed.isEmpty() || trimmed.length() > MAX_NUMERIC_LENGTH) {
            return new Amount(Kind.INVALID, 0.0);
        }
        if (!trimmed.matches("\\d+(\\.\\d+)?")) {
            return new Amount(Kind.INVALID, 0.0);
        }
        try {
            BigDecimal parsed = new BigDecimal(trimmed);
            if (parsed.signum() <= 0 || parsed.compareTo(MAX_DECIMAL) > 0) {
                return new Amount(Kind.INVALID, 0.0);
            }
            double value = parsed.doubleValue();
            if (!Double.isFinite(value) || value <= 0.0) {
                return new Amount(Kind.INVALID, 0.0);
            }
            return new Amount(Kind.VALUE, value);
        } catch (NumberFormatException e) {
            return new Amount(Kind.INVALID, 0.0);
        }
    }

    /** @return the digits of a plain non-negative integer with leading zeros removed, or {@code null} if it is not one */
    private static String stripLeadingZeros(String trimmed) {
        if (trimmed.isEmpty() || !trimmed.matches("\\d+")) {
            return null;
        }
        int firstNonZero = 0;
        while (firstNonZero < trimmed.length() - 1 && trimmed.charAt(firstNonZero) == '0') {
            firstNonZero++;
        }
        return trimmed.substring(firstNonZero);
    }
}
