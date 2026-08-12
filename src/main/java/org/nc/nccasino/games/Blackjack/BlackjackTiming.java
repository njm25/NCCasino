package org.nc.nccasino.games.Blackjack;

/**
 * Named scheduler delays (in ticks) used by BlackjackInventory's turn
 * sequencing. Extracted so the ordering relationship between them -- in
 * particular the double-down race documented on
 * BlackjackInventory#handleDoubleDown and characterized in
 * BlackjackTimingTest -- is backed by the literal constants production
 * actually schedules with, not copies of them.
 *
 * These values are preserved exactly as they were inline; changing them
 * changes real gameplay pacing and is out of scope here.
 */
public final class BlackjackTiming {

    private BlackjackTiming() {
    }

    /** Delay before a hit card visually lands after being scheduled. */
    public static final long CARD_DEAL_DELAY_TICKS = 20L;

    /**
     * Delay after a hit before its resulting hand value is evaluated --
     * long enough to assume the card has landed (twice the deal delay).
     */
    public static final long HIT_EVALUATION_DELAY_TICKS = 40L;

    /** Delay before advancing to the next player's turn after a stand or a double-down. */
    public static final long TURN_ADVANCE_DELAY_TICKS = 20L;
}
