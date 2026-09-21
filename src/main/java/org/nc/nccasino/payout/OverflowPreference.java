package org.nc.nccasino.payout;

/**
 * What a player wants done with item winnings that do not fit in their
 * inventory. Deliberately only two values: the server never authorizes
 * deleting excess winnings, so there is no "discard" option, and even
 * {@link #DROP} only drops up to the configured safety cap before the
 * remainder is banked.
 */
public enum OverflowPreference {
    BANK,
    DROP;

    public static OverflowPreference parse(String raw, OverflowPreference fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
