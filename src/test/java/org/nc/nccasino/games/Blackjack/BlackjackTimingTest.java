package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks down the production scheduler-delay constants BlackjackInventory
 * actually schedules with (BlackjackTiming), and the ordering relationships
 * between them that drive real sequencing behavior -- including the
 * double-down race documented on BlackjackInventory#handleDoubleDown.
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

    @Test
    void doubleDownAlwaysAdvancesTheTurnBeforeItsHitIsEvaluated() {
        // Characterizes the preserved double-down quirk documented on
        // handleDoubleDown: it advances the turn on TURN_ADVANCE_DELAY_TICKS
        // regardless of the extra card it just dealt via handleHit, whose
        // evaluation callback only fires later, on
        // HIT_EVALUATION_DELAY_TICKS. As long as this inequality holds, the
        // turn has already moved on (to the next player, or the dealer)
        // by the time that callback runs and mutates
        // playerDone/playerTurnActive/re-advances the turn for a hand that
        // is no longer current. This is a regression guard for the
        // ordering, not an endorsement of the behavior -- see the
        // handleDoubleDown doc comment for why it's deferred to the
        // splitting work rather than fixed here.
        assertTrue(
            BlackjackTiming.TURN_ADVANCE_DELAY_TICKS < BlackjackTiming.HIT_EVALUATION_DELAY_TICKS,
            "double-down's turn advance must fire before its own hit's evaluation for the documented race to reproduce"
        );
    }

    @Test
    void doubleDownsExtraCardLandsOnTheSameTickTheTurnAdvances() {
        // The card itself (CARD_DEAL_DELAY_TICKS) visually lands at the
        // same tick the turn advances (TURN_ADVANCE_DELAY_TICKS) -- it's
        // only the *evaluation* of that card that lags behind.
        assertEquals(BlackjackTiming.TURN_ADVANCE_DELAY_TICKS, BlackjackTiming.CARD_DEAL_DELAY_TICKS);
    }
}
