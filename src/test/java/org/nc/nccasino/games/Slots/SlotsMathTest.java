package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsMathTest {

    private static SlotsOutcome grid(SlotsSymbol[][] cells) {
        return new SlotsOutcome(cells);
    }

    private static SlotsOutcome uniform(SlotsSymbol symbol) {
        return grid(new SlotsSymbol[][] {
            {symbol, symbol, symbol},
            {symbol, symbol, symbol},
            {symbol, symbol, symbol}
        });
    }

    @Test
    void topPaylineWinsOnMatchingTopRow() {
        SlotsOutcome outcome = grid(new SlotsSymbol[][] {
            {SlotsSymbol.BELL, SlotsSymbol.BELL, SlotsSymbol.BELL},
            {SlotsSymbol.CHERRY, SlotsSymbol.LEMON, SlotsSymbol.DIAMOND},
            {SlotsSymbol.SEVEN, SlotsSymbol.CHERRY, SlotsSymbol.LEMON}
        });
        SlotsMath.LineResult result = SlotsMath.evaluateLine(outcome, SlotsPayline.TOP);
        assertTrue(result.winning());
        assertEquals(SlotsSymbol.BELL, result.symbol());
        assertEquals(21, result.multiplier());
    }

    @Test
    void middlePaylineWinsOnMatchingMiddleRow() {
        SlotsOutcome outcome = grid(new SlotsSymbol[][] {
            {SlotsSymbol.CHERRY, SlotsSymbol.LEMON, SlotsSymbol.DIAMOND},
            {SlotsSymbol.DIAMOND, SlotsSymbol.DIAMOND, SlotsSymbol.DIAMOND},
            {SlotsSymbol.SEVEN, SlotsSymbol.CHERRY, SlotsSymbol.LEMON}
        });
        SlotsMath.LineResult result = SlotsMath.evaluateLine(outcome, SlotsPayline.MIDDLE);
        assertTrue(result.winning());
        assertEquals(SlotsSymbol.DIAMOND, result.symbol());
        assertEquals(39, result.multiplier());
    }

    @Test
    void bottomPaylineWinsOnMatchingBottomRow() {
        SlotsOutcome outcome = grid(new SlotsSymbol[][] {
            {SlotsSymbol.CHERRY, SlotsSymbol.LEMON, SlotsSymbol.DIAMOND},
            {SlotsSymbol.SEVEN, SlotsSymbol.CHERRY, SlotsSymbol.LEMON},
            {SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.SEVEN}
        });
        SlotsMath.LineResult result = SlotsMath.evaluateLine(outcome, SlotsPayline.BOTTOM);
        assertTrue(result.winning());
        assertEquals(SlotsSymbol.SEVEN, result.symbol());
        assertEquals(104, result.multiplier());
    }

    @Test
    void downDiagonalWinsTopLeftToBottomRight() {
        SlotsOutcome outcome = grid(new SlotsSymbol[][] {
            {SlotsSymbol.CHERRY, SlotsSymbol.LEMON, SlotsSymbol.DIAMOND},
            {SlotsSymbol.SEVEN, SlotsSymbol.CHERRY, SlotsSymbol.LEMON},
            {SlotsSymbol.SEVEN, SlotsSymbol.SEVEN, SlotsSymbol.CHERRY}
        });
        SlotsMath.LineResult result = SlotsMath.evaluateLine(outcome, SlotsPayline.DOWN_DIAGONAL);
        assertTrue(result.winning());
        assertEquals(SlotsSymbol.CHERRY, result.symbol());
        assertEquals(8, result.multiplier());
    }

    @Test
    void upDiagonalWinsBottomLeftToTopRight() {
        SlotsOutcome outcome = grid(new SlotsSymbol[][] {
            {SlotsSymbol.CHERRY, SlotsSymbol.LEMON, SlotsSymbol.LEMON},
            {SlotsSymbol.SEVEN, SlotsSymbol.LEMON, SlotsSymbol.DIAMOND},
            {SlotsSymbol.LEMON, SlotsSymbol.SEVEN, SlotsSymbol.CHERRY}
        });
        SlotsMath.LineResult result = SlotsMath.evaluateLine(outcome, SlotsPayline.UP_DIAGONAL);
        assertTrue(result.winning());
        assertEquals(SlotsSymbol.LEMON, result.symbol());
        assertEquals(13, result.multiplier());
    }

    @Test
    void everySymbolPaysItsOwnAuditedMultiplierOnAUniformGrid() {
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            SlotsOutcome outcome = uniform(symbol);
            for (SlotsMath.LineResult result : SlotsMath.evaluateAllLines(outcome)) {
                assertTrue(result.winning());
                assertEquals(symbol, result.symbol());
                assertEquals(symbol.multiplier(), result.multiplier());
            }
        }
    }

    @Test
    void noWinningLinesYieldsZeroMultiplierSumAndZeroPayout() {
        SlotsOutcome outcome = grid(new SlotsSymbol[][] {
            {SlotsSymbol.CHERRY, SlotsSymbol.LEMON, SlotsSymbol.BELL},
            {SlotsSymbol.DIAMOND, SlotsSymbol.SEVEN, SlotsSymbol.CHERRY},
            {SlotsSymbol.LEMON, SlotsSymbol.BELL, SlotsSymbol.DIAMOND}
        });
        List<SlotsMath.LineResult> results = SlotsMath.evaluateAllLines(outcome);
        assertEquals(5, results.size());
        for (SlotsMath.LineResult result : results) {
            assertFalse(result.winning());
        }
        assertEquals(0L, SlotsMath.totalPayout(outcome, 10L));
    }

    @Test
    void multipleSimultaneousWinningLinesSumTheirMultipliers() {
        // TOP (CHERRY x3, mult 8) and MIDDLE (LEMON x3, mult 13) both win;
        // BOTTOM and both diagonals deliberately break.
        SlotsOutcome outcome = grid(new SlotsSymbol[][] {
            {SlotsSymbol.CHERRY, SlotsSymbol.CHERRY, SlotsSymbol.CHERRY},
            {SlotsSymbol.LEMON, SlotsSymbol.LEMON, SlotsSymbol.LEMON},
            {SlotsSymbol.BELL, SlotsSymbol.DIAMOND, SlotsSymbol.SEVEN}
        });
        long payout = SlotsMath.totalPayout(outcome, 10L);
        assertEquals(10L * (8 + 13), payout);
    }

    @Test
    void maximumPayoutIsAllFiveLinesHittingSevens() {
        SlotsOutcome outcome = uniform(SlotsSymbol.SEVEN);
        long perLineWager = 50L;
        long payout = SlotsMath.totalPayout(outcome, perLineWager);
        assertEquals(perLineWager * 5 * 104, payout);
    }

    @Test
    void totalBetIsFiveTimesThePerLineWager() {
        assertEquals(500L, SlotsMath.totalBet(100L));
        assertEquals(0L, SlotsMath.totalBet(0L));
    }

    @Test
    void negativePerLineWagerIsRejected() {
        SlotsOutcome outcome = uniform(SlotsSymbol.CHERRY);
        assertThrows(IllegalArgumentException.class, () -> SlotsMath.totalPayout(outcome, -1L));
        assertThrows(IllegalArgumentException.class, () -> SlotsMath.totalBet(-1L));
    }

    @Test
    void overflowingWagerIsRejectedRatherThanWrapping() {
        SlotsOutcome outcome = uniform(SlotsSymbol.SEVEN);
        assertThrows(ArithmeticException.class, () -> SlotsMath.totalPayout(outcome, Long.MAX_VALUE / 10));
        assertThrows(ArithmeticException.class, () -> SlotsMath.totalBet(Long.MAX_VALUE));
    }

    /**
     * Production-delegate proof of the audited RTP: computed purely from
     * {@link SlotsSymbol}'s own weight/multiplier fields (not restated
     * constants), so a future accidental change to the paytable fails this
     * test instead of silently drifting the payback percentage.
     */
    @Test
    void theoreticalRtpMatchesAuditedFigure() {
        double rtp = 0.0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            double p = symbol.weight() / (double) SlotsSymbol.TOTAL_WEIGHT;
            rtp += p * p * p * symbol.multiplier();
        }
        assertEquals(0.91197, rtp, 1e-9);
    }
}
