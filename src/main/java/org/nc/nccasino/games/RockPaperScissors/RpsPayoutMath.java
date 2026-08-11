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
}
