package org.nc.nccasino.games.Slots;

/**
 * How a finished spin's return compares to the stake that bought it, and so
 * which result cue the spin ends on. This is the real classifier for both paid
 * and Demo Spins; it began life as the Paytable Sound Lab's six audition
 * bands, which have been removed now that the bands are the actual sounds.
 *
 * <p>Boundaries are inclusive at the bottom, exclusive at the top, so every
 * ratio lands in exactly one band and the named edges belong to the tier they
 * open: 1x begins {@link #SMALL_WIN}, 3x begins {@link #MEDIUM_WIN}, 10x begins
 * {@link #BIG_WIN} and 25x begins {@link #JACKPOT}.
 *
 * <p>The ratio is measured against the total stake, not per line, so a spin
 * that returns less than it cost is a {@link #PARTIAL_RETURN} however many
 * individual lines paid.
 */
enum SlotsPayoutSoundTier {
    /** Nothing came back. */
    NO_RETURN,
    /** Something came back, but less than the stake. */
    PARTIAL_RETURN,
    /** 1x to just under 3x. */
    SMALL_WIN,
    /** 3x to just under 10x. */
    MEDIUM_WIN,
    /** 10x to just under 25x. */
    BIG_WIN,
    /** 25x and above. */
    JACKPOT;

    /**
     * @param payout the total returned for the spin, never profit on top of
     *     the stake
     * @param bet the spin's total stake; a non-positive bet is treated as 1 so
     *     a misconfigured table can never divide by zero
     */
    static SlotsPayoutSoundTier forReturn(long payout, long bet) {
        if (payout <= 0L) {
            return NO_RETURN;
        }
        double ratio = (double) payout / Math.max(1L, bet);
        if (ratio < 1.0) {
            return PARTIAL_RETURN;
        }
        if (ratio < 3.0) {
            return SMALL_WIN;
        }
        if (ratio < 10.0) {
            return MEDIUM_WIN;
        }
        if (ratio < 25.0) {
            return BIG_WIN;
        }
        return JACKPOT;
    }
}
