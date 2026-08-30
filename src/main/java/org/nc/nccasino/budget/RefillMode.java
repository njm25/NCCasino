package org.nc.nccasino.budget;

/**
 * How a server-owned limited dealer is re-funded over time.
 *
 * <p>All three are evaluated lazily, when the dealer is next accessed, rather
 * than by a ticking task: a casino with many dealers should not pay for a
 * scheduler tick per dealer per period, and a server that was offline for a
 * week must still end up in the same state as one that was not.
 */
public enum RefillMode {

    /** No funding beyond the starting balance and what players lose. */
    NONE,

    /**
     * Adds a fixed amount per elapsed period, optionally up to a cap. Behaves
     * like a token bucket, so house earnings accumulate on top.
     */
    ADD,

    /**
     * Sets the dealer's live balance to a configured target each period.
     *
     * <p>The target is deliberately defined as <em>total</em> live balance,
     * not free balance: see {@link RefillPolicy} for why, and for what happens
     * when active reservations already exceed it.
     */
    RESET;

    public static RefillMode parse(String raw, RefillMode fallback) {
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
