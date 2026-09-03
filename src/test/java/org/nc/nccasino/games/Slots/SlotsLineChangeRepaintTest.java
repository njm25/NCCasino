package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The full repaint-decision matrix for a Paylines change, across both views and both wrap directions. */
class SlotsLineChangeRepaintTest {

    @Test
    void gameOrdinaryChangeFlashesTheSingleLine() {
        assertEquals(SlotsLineChangeRepaint.Action.FLASH_SINGLE_LINE,
            SlotsLineChangeRepaint.decide(SlotsLineChangeRepaint.Mode.GAME, false));
    }

    @Test
    void gameWrapAnnouncesAndRepaintsRatherThanFlashingOneLine() {
        assertEquals(SlotsLineChangeRepaint.Action.ANNOUNCE_WRAP_AND_REPAINT_CANVAS,
            SlotsLineChangeRepaint.decide(SlotsLineChangeRepaint.Mode.GAME, true));
    }

    @Test
    void paytableAlwaysRepaintsRegardlessOfWrapAndNeverFlashes() {
        // Paylines changes must never show the green/black path flash over
        // the paytable -- every change (wrap or not) is just an immediate
        // repaint of the paytable for the new line count.
        assertEquals(SlotsLineChangeRepaint.Action.REPAINT_CANVAS,
            SlotsLineChangeRepaint.decide(SlotsLineChangeRepaint.Mode.PAYTABLE, false));
        assertEquals(SlotsLineChangeRepaint.Action.REPAINT_CANVAS,
            SlotsLineChangeRepaint.decide(SlotsLineChangeRepaint.Mode.PAYTABLE, true));
    }

    @Test
    void everyModeHasADefinedDecisionForBothWrapValues() {
        for (SlotsLineChangeRepaint.Mode mode : SlotsLineChangeRepaint.Mode.values()) {
            for (boolean wrapped : new boolean[] {true, false}) {
                // Must not throw for any combination.
                SlotsLineChangeRepaint.decide(mode, wrapped);
            }
        }
    }
}
