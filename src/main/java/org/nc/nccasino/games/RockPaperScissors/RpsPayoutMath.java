package org.nc.nccasino.games.RockPaperScissors;

/** Overflow-safe arithmetic for the wager units used by RPS. */
final class RpsPayoutMath {

    /**
     * 2^53 -- the largest integral value a double can represent exactly.
     * Every payout eventually crosses a double-typed boundary (Client/Server
     * #creditPlayer(Player, double), PendingPayout's double amount, Vault's
     * Economy API), so this is the real ceiling of what this currency
     * system can carry without silently losing precision, not an arbitrary
     * choice. A chain that would compound past it is capped here instead of
     * being handed a value that would come back rounded on the other side
     * of those APIs.
     */
    static final long MAX_SAFE_POT = 1L << 53;

    private RpsPayoutMath() {
    }

    static long compound(long currentPot, double multiplier) {
        if (currentPot <= 0) {
            return 0;
        }
        double compounded = Math.round((double) currentPot * multiplier);
        return compounded >= MAX_SAFE_POT ? MAX_SAFE_POT : (long) compounded;
    }

    /**
     * Whether compounding {@code currentPot} one more time would need to
     * clamp -- i.e. whether it's still safe to offer another round from
     * this pot. Callers must check this BEFORE offering that next round
     * (not after playing it): a round that's offered when this is already
     * true would have its own true win silently clamped by {@link #compound}
     * once it resolves, underpaying a real win instead of never letting the
     * player reach it.
     */
    static boolean wouldExceedSafeMaxIfCompoundedAgain(long currentPot, double multiplier) {
        if (currentPot <= 0) {
            return false;
        }
        double compounded = Math.round((double) currentPot * multiplier);
        return compounded > MAX_SAFE_POT;
    }
}
