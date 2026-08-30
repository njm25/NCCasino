package org.nc.nccasino.budget;

/**
 * Whether a dealer's payouts are constrained by a token inventory.
 *
 * <p>{@link #UNLIMITED} is the default and is exactly the pre-Phase-2
 * behavior: no balance, no reservations, no persistence, and no arithmetic on
 * the wager path. It is deliberately <em>not</em> modelled as a very large
 * balance -- a fake enormous number would still pay the full cost of every
 * reservation, settlement and disk write, and would eventually overflow or
 * drift. Callers should branch on this before doing any budget work.
 */
public enum DealerBudgetMode {
    UNLIMITED,
    LIMITED;

    public static DealerBudgetMode parse(String raw, DealerBudgetMode fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public boolean isLimited() {
        return this == LIMITED;
    }
}
