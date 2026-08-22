package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks down the production scheduler-delay constants BlackjackInventory
 * actually schedules with (BlackjackTiming).
 *
 * BlackjackInventory#handleDoubleDown used to race here: it advanced the
 * turn via TURN_ADVANCE_DELAY_TICKS while an independently-scheduled
 * handleHit evaluation callback landed later, on
 * HIT_EVALUATION_DELAY_TICKS, and could mutate a hand that was no longer
 * current. That's fixed by construction now -- handleDoubleDown deals and
 * evaluates its one extra card itself, on a single HIT_EVALUATION_DELAY_TICKS
 * callback guarded by a round-generation/hand-token pair (see
 * BlackjackInventory#isStaleHandCallback) -- so there is no longer a second,
 * independently-timed callback for these constants' relative ordering to
 * race against. These tests only lock down the constants themselves.
 */
class BlackjackTimingTest {

    @Test
    void productionDelayValuesAreUnchanged() {
        assertEquals(20L, BlackjackTiming.CARD_DEAL_DELAY_TICKS);
        assertEquals(40L, BlackjackTiming.HIT_EVALUATION_DELAY_TICKS);
        assertEquals(20L, BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
    }

    @Test
    void hitEvaluationWaitsLongerThanTheCardItselfTakesToLand() {
        // handleHit deals the card at CARD_DEAL_DELAY_TICKS but only reads
        // the resulting hand value at HIT_EVALUATION_DELAY_TICKS, so the
        // card has always landed before its own value is read.
        assertTrue(BlackjackTiming.HIT_EVALUATION_DELAY_TICKS > BlackjackTiming.CARD_DEAL_DELAY_TICKS);
    }
}
