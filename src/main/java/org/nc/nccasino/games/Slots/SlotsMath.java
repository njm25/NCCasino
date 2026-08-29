package org.nc.nccasino.games.Slots;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure payout evaluator.
 *
 * <p>Wins are scored the way a real machine scores them: the longest run of
 * identical symbols starting at the <em>leftmost</em> reel and running
 * consecutively rightward. A line pays when that run reaches the symbol's
 * {@link SlotsSymbol#minimumRun()}, and pays more the longer it continues. A
 * matching pair sitting in the middle of a line is not a win, and never has
 * been on a real machine.
 *
 * <p>All wager/payout arithmetic uses {@code long} with checked
 * ({@link Math#addExact}/{@link Math#multiplyExact}) operations so an absurd
 * configured wager can never silently overflow into a wrong (or negative)
 * payout -- it throws {@link ArithmeticException} instead.
 */
public final class SlotsMath {

    /**
     * Item-backed currencies (raw material fallback, or {@code STANDARD}/
     * {@code CUSTOM} providers) only ever move whole physical items through
     * {@code int}-typed deposit calls -- there is no exact-precision path
     * for them the way {@code VAULT} has {@link java.math.BigDecimal}.
     * Rather than silently clamp an oversized payout to {@code Integer.MAX_VALUE}
     * and report it delivered (underpaying the player), or synchronously
     * hand out millions of item stacks, a spin whose worst-case payout could
     * exceed this ceiling is rejected up front, before any wager is
     * withdrawn. Mirrors {@code BettingTable.MAX_ITEM_MODE_PAYOUT} in
     * Roulette, the currently-endorsed fix (commit c632881) after an earlier
     * chunking approach there was found to still risk main-thread hangs and
     * silent loss on a failed queue.
     */
    public static final long MAX_ITEM_MODE_PAYOUT = 10_000L;

    private SlotsMath() {
    }

    /**
     * One line's result. {@code runLength} is 0 on a losing line; on a winner
     * it is how many reels actually matched, which drives both the payout and
     * how much of the line the win animation should light up.
     */
    public record LineResult(
        SlotsPayline payline,
        boolean winning,
        SlotsSymbol symbol,
        int runLength,
        double multiplier
    ) {
        public static LineResult losing(SlotsPayline payline) {
            return new LineResult(payline, false, null, 0, 0.0);
        }
    }

    /**
     * Measures the leftmost consecutive run on a line and prices it.
     * {@link SlotsSymbol#BLANK} never pays, so a blank in the first column is
     * an immediate loss for that line.
     */
    public static LineResult evaluateLine(SlotsOutcome outcome, SlotsPayline payline, SlotsPaytable paytable) {
        int columns = outcome.columns();
        SlotsSymbol first = outcome.symbolAt(payline.rowAt(0, columns), 0);
        if (first == null || !first.pays()) {
            return LineResult.losing(payline);
        }

        int run = 1;
        for (int col = 1; col < columns; col++) {
            if (outcome.symbolAt(payline.rowAt(col, columns), col) != first) {
                break;
            }
            run++;
        }

        if (run < first.minimumRun()) {
            return LineResult.losing(payline);
        }
        double multiplier = paytable.multiplier(first, run);
        if (multiplier <= 0.0) {
            return LineResult.losing(payline);
        }
        return new LineResult(payline, true, first, run, multiplier);
    }

    /** Evaluates only the lines the player actually activated, in canonical order. */
    public static List<LineResult> evaluateActiveLines(SlotsOutcome outcome, int activeLines, SlotsPaytable paytable) {
        SlotsPayline[] lines = SlotsPayline.active(activeLines);
        List<LineResult> results = new ArrayList<>(lines.length);
        for (SlotsPayline payline : lines) {
            results.add(evaluateLine(outcome, payline, paytable));
        }
        return results;
    }

    /**
     * Total credit for the whole spin: {@code perLineWager} multiplied by the
     * summed multiplier of every winning active line, rounded down to whole
     * currency units. Multipliers are total returns -- the stake is never
     * separately returned on top of this.
     */
    public static long totalPayout(SlotsOutcome outcome, int activeLines, long perLineWager, SlotsPaytable paytable) {
        if (perLineWager < 0) {
            throw new IllegalArgumentException("perLineWager must not be negative");
        }
        double multiplierSum = 0.0;
        for (LineResult result : evaluateActiveLines(outcome, activeLines, paytable)) {
            if (result.winning()) {
                multiplierSum += result.multiplier();
            }
        }
        if (multiplierSum <= 0.0) {
            return 0L;
        }
        double raw = (double) perLineWager * multiplierSum;
        if (raw > (double) Long.MAX_VALUE) {
            throw new ArithmeticException("Slots payout overflows a long: " + raw);
        }
        return (long) Math.floor(raw);
    }

    /** Total debit for the spin: every active line stakes {@code perLineWager}. */
    public static long totalBet(long perLineWager, int activeLines) {
        if (perLineWager < 0) {
            throw new IllegalArgumentException("perLineWager must not be negative");
        }
        int lines = SlotsPayline.normalizeLineCount(activeLines);
        return Math.multiplyExact(perLineWager, (long) lines);
    }

    /**
     * Worst-case payout a spin could produce, used to probe exposure before a
     * wager is accepted. Every active line hitting the top symbol at full
     * width is the ceiling.
     */
    public static long maxPossiblePayout(long perLineWager, int activeLines, SlotsPaytable paytable) {
        if (perLineWager < 0) {
            throw new IllegalArgumentException("perLineWager must not be negative");
        }
        int lines = SlotsPayline.normalizeLineCount(activeLines);
        double raw = (double) perLineWager * paytable.maxLineMultiplier() * lines;
        if (raw > (double) Long.MAX_VALUE) {
            throw new ArithmeticException("Slots worst-case payout overflows a long: " + raw);
        }
        return (long) Math.ceil(raw);
    }
}
