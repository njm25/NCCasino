package org.nc.nccasino.games.RockPaperScissors;

/** Overflow-safe arithmetic for the integer wager units used by RPS. */
final class RpsPayoutMath {

    private RpsPayoutMath() {
    }

    static int compound(int currentPot, double multiplier) {
        if (currentPot <= 0) {
            return 0;
        }
        long compounded = Math.round(currentPot * multiplier);
        return compounded >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) compounded;
    }
}
