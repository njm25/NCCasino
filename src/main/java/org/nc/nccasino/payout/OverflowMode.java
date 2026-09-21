package org.nc.nccasino.payout;

/**
 * Server-side policy governing whether players may choose their own
 * {@link OverflowPreference}.
 *
 * <p>A player's stored preference is kept independently of this mode: if an
 * administrator temporarily forces {@link #BANK} and later returns to
 * {@link #PLAYER_CHOICE}, the player's prior choice comes back rather than
 * having been overwritten.
 */
public enum OverflowMode {
    PLAYER_CHOICE,
    BANK,
    DROP;

    public static OverflowMode parse(String raw, OverflowMode fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /**
     * Resolves the preference actually in force for one delivery.
     *
     * @param playerPreference the player's stored choice, or {@code null} if
     *     they have never made one (they inherit {@code serverDefault})
     */
    public OverflowPreference effectivePreference(
        OverflowPreference playerPreference,
        OverflowPreference serverDefault
    ) {
        OverflowPreference fallback = serverDefault == null ? OverflowPreference.BANK : serverDefault;
        return switch (this) {
            case BANK -> OverflowPreference.BANK;
            case DROP -> OverflowPreference.DROP;
            case PLAYER_CHOICE -> playerPreference == null ? fallback : playerPreference;
        };
    }
}
