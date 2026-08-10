package org.nc.nccasino.games.CoinFlip;

/** Overflow-safe arithmetic for the integer wager units used by Coin Flip. */
final class CoinFlipPayoutMath {

    private CoinFlipPayoutMath() {
    }

    static int compound(int currentPot, double multiplier) {
        if (currentPot <= 0) {
            return 0;
        }
        long compounded = Math.round(currentPot * multiplier);
        return compounded >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) compounded;
    }
}
