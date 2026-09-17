package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the exact PLAY-vs-Demo cell presentation decision (redesign audit
 * Section 1): the self-audit's demo-cell fix must remain, and this is what
 * proves PLAY and Demo choose different presentations for the same symbol,
 * including SEEDS, without needing a live Bukkit inventory.
 */
class SlotsCellPresentationTest {

    @Test
    void aNullSymbolIsAlwaysNeutralRegardlessOfDemoFlag() {
        assertEquals(SlotsCellPresentation.NEUTRAL, SlotsCellPresentation.of(null, false));
        assertEquals(SlotsCellPresentation.NEUTRAL, SlotsCellPresentation.of(null, true));
    }

    @Test
    void playAndDemoChooseDifferentPresentationsForTheSameSymbol() {
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            SlotsCellPresentation play = SlotsCellPresentation.of(symbol, false);
            SlotsCellPresentation demo = SlotsCellPresentation.of(symbol, true);
            assertEquals(SlotsCellPresentation.PAID, play, symbol + " in a real spin must be PAID");
            assertEquals(SlotsCellPresentation.DEMO, demo, symbol + " in a demo must be DEMO");
            assertFalse(play == demo, symbol + ": PLAY and Demo must never share a presentation");
        }
    }

    @Test
    void seedsIsDemoLabelledInADemoJustLikeAnyOtherSymbol() {
        // SEEDS must not be special-cased out of the demo disclaimer.
        assertTrue(SlotsCellPresentation.of(SlotsSymbol.SEEDS, true).isDemoLabelled());
        assertFalse(SlotsCellPresentation.of(SlotsSymbol.SEEDS, false).isDemoLabelled());
    }

    @Test
    void onlyDemoIsDemoLabelled() {
        assertTrue(SlotsCellPresentation.DEMO.isDemoLabelled());
        assertFalse(SlotsCellPresentation.PAID.isDemoLabelled());
        assertFalse(SlotsCellPresentation.NEUTRAL.isDemoLabelled());
    }

    @Test
    void matchedHypotheticalDemoWinningCellsRemainDemoLabelled() {
        // The exact scenario highlightLine() must preserve: a matched
        // winning symbol during a demo is still DEMO, not PAID -- glint for
        // the win and the demo disclaimer are not mutually exclusive.
        for (SlotsSymbol winner : SlotsSymbol.payingSymbols()) {
            assertTrue(SlotsCellPresentation.of(winner, true).isDemoLabelled(),
                "a matched demo winning symbol (" + winner + ") must keep the demo disclaimer");
        }
    }

    @Test
    void paidSpinCellsAreNeverDemoLabelled() {
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            assertFalse(SlotsCellPresentation.of(symbol, false).isDemoLabelled(),
                symbol + " in a real paid spin must never carry demo lore");
        }
    }
}
