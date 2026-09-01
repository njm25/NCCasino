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
     * The largest item-mode payout NCCasino will commit to, in whole
     * currency units.
     *
     * <p>This is a <em>numeric precision</em> ceiling, not a delivery one.
     * Physical inventory size no longer constrains a spin: overflow that
     * cannot fit is delivered partially and the remainder is banked by
     * {@code OverflowBankService}, so a win larger than the player's
     * inventory is paid rather than refused. What remains constrained is
     * representability -- an item payout can still travel through a
     * {@code double}-typed durable record ({@code PendingPayout#amount}) if
     * the player disconnects mid-spin, and above 2^53 a {@code double} can no
     * longer represent every whole number, which would silently round the
     * amount owed. A spin whose worst case could exceed that is rejected up
     * front, before any wager is withdrawn, rather than paid inaccurately.
     *
     * <p>Was previously 10,000 -- a physical-delivery limit that made item
     * mode reject most five- and seven-reel wagers outright. That limit was
     * removed once overflow banking guaranteed a safe destination for
     * winnings of any size.
     */
    public static final long MAX_ITEM_MODE_PAYOUT = 9_007_199_254_740_992L;

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
     * Precision the probabilistic-rounding draw in {@link #totalPayout(SlotsOutcome,
     * int, long, SlotsPaytable, SlotsRandomSource)} is made at. {@link
     * SlotsRandomSource} only exposes {@code nextInt(bound)}, so the
     * fractional remainder is compared against a draw from this many equally
     * likely buckets rather than a native {@code [0,1)} draw -- one part in a
     * million is far finer than the smallest fractional remainder this
     * matters for.
     */
    private static final int ROUNDING_DRAW_PRECISION = 1_000_000;

    /**
     * Total credit for the whole spin: {@code perLineWager} multiplied by the
     * summed multiplier of every winning active line, rounded down to whole
     * currency units. Multipliers are total returns -- the stake is never
     * separately returned on top of this.
     *
     * <p>Deterministic floor, kept exactly as before for pure-math testing and
     * any caller that is not settling a real spin. Real gameplay must use
     * {@link #totalPayout(SlotsOutcome, int, long, SlotsPaytable, SlotsRandomSource)}
     * instead -- see that overload for why.
     */
    public static long totalPayout(SlotsOutcome outcome, int activeLines, long perLineWager, SlotsPaytable paytable) {
        double raw = rawPayout(outcome, activeLines, perLineWager, paytable);
        if (raw <= 0.0) {
            return 0L;
        }
        return (long) Math.floor(raw);
    }

    /**
     * Total credit for the whole spin, exactly like the deterministic
     * overload, except the final floor/ceiling choice is made
     * probabilistically: rounds up with probability equal to the fractional
     * remainder, and down otherwise, using {@code rng} rather than a fresh,
     * unaccountable {@link java.util.Random}.
     *
     * <p>A deterministic floor here is a real, structural bias, not a
     * rounding nicety: {@link SlotsPaytable} normalizes its multipliers
     * against the theoretical (pre-floor) return, so always rounding down
     * makes every spin's expected value strictly below the configured
     * house edge target -- worst at denomination 1, where the floored unit is
     * a large fraction of the average payout. Rounding up with probability
     * equal to the fractional part makes this floor unbiased in expectation
     * (its long-run average recovers exactly the configured return), without
     * changing the paytable shown to players or the numbers configured by an
     * administrator.
     *
     * @param rng the spin's own random source -- reusing it (rather than a
     *     second, independent source) keeps every random decision in one
     *     spin attributable to the same auditable draw sequence
     */
    public static long totalPayout(
        SlotsOutcome outcome, int activeLines, long perLineWager, SlotsPaytable paytable, SlotsRandomSource rng) {

        double raw = rawPayout(outcome, activeLines, perLineWager, paytable);
        if (raw <= 0.0) {
            return 0L;
        }
        long floor = (long) Math.floor(raw);
        double fractional = raw - (double) floor;
        if (fractional <= 0.0 || rng == null) {
            return floor;
        }
        int draw = rng.nextInt(ROUNDING_DRAW_PRECISION);
        return draw < fractional * ROUNDING_DRAW_PRECISION ? floor + 1 : floor;
    }

    /** The exact (unrounded) payout a spin's winning lines produce, shared by both {@code totalPayout} overloads. */
    private static double rawPayout(SlotsOutcome outcome, int activeLines, long perLineWager, SlotsPaytable paytable) {
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
            return 0.0;
        }
        double raw = (double) perLineWager * multiplierSum;
        if (raw > (double) Long.MAX_VALUE) {
            throw new ArithmeticException("Slots payout overflows a long: " + raw);
        }
        return raw;
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
