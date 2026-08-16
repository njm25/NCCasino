package org.nc.nccasino.games.Slots;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure payout evaluator. All wager/payout arithmetic uses {@code long} with
 * checked ({@link Math#addExact}/{@link Math#multiplyExact}) operations so
 * an absurd configured wager can never silently overflow into a wrong (or
 * negative) payout -- it throws {@link ArithmeticException} instead.
 */
public final class SlotsMath {

    private SlotsMath() {
    }

    public record LineResult(SlotsPayline payline, boolean winning, SlotsSymbol symbol, int multiplier) {
    }

    public static LineResult evaluateLine(SlotsOutcome outcome, SlotsPayline payline) {
        int[][] cells = payline.cells();
        SlotsSymbol first = outcome.symbolAt(cells[0][0], cells[0][1]);
        for (int i = 1; i < cells.length; i++) {
            SlotsSymbol current = outcome.symbolAt(cells[i][0], cells[i][1]);
            if (current != first) {
                return new LineResult(payline, false, null, 0);
            }
        }
        return new LineResult(payline, true, first, first.multiplier());
    }

    public static List<LineResult> evaluateAllLines(SlotsOutcome outcome) {
        List<LineResult> results = new ArrayList<>(SlotsPayline.ALL.length);
        for (SlotsPayline payline : SlotsPayline.ALL) {
            results.add(evaluateLine(outcome, payline));
        }
        return results;
    }

    /**
     * Total credit for the whole spin: {@code perLineWager} multiplied by
     * the sum of every winning line's multiplier. Multipliers are total
     * returns -- the stake is never separately returned on top of this.
     */
    public static long totalPayout(SlotsOutcome outcome, long perLineWager) {
        if (perLineWager < 0) {
            throw new IllegalArgumentException("perLineWager must not be negative");
        }
        long multiplierSum = 0;
        for (LineResult result : evaluateAllLines(outcome)) {
            if (result.winning()) {
                multiplierSum = Math.addExact(multiplierSum, (long) result.multiplier());
            }
        }
        return Math.multiplyExact(perLineWager, multiplierSum);
    }

    /** Total debit for the spin: every one of the five lines wagers {@code perLineWager}. */
    public static long totalBet(long perLineWager) {
        if (perLineWager < 0) {
            throw new IllegalArgumentException("perLineWager must not be negative");
        }
        return Math.multiplyExact(perLineWager, (long) SlotsPayline.ALL.length);
    }
}
