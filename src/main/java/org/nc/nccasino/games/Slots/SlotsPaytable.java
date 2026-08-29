package org.nc.nccasino.games.Slots;

import java.util.EnumMap;
import java.util.Map;

/**
 * Derives the machine's actual payout multipliers from a configured house
 * edge, instead of hardcoding them.
 *
 * <p>This mirrors the convention the rest of the plugin already uses: Mines
 * and Dragon Descent both pin the edge as a constant and compute the payout
 * multiplier as {@code 0.99 / probability} rather than storing a payout table.
 * The difference here is only that the edge is per-dealer configurable rather
 * than a fixed 1%, and that a slot machine needs a whole table of multipliers
 * rather than a single one.
 *
 * <h2>How the derivation works</h2>
 * {@link SlotsSymbol#payWeight()} and {@link #lengthFactor(int)} fix only the
 * <em>shape</em> of the table -- how much a seven is worth relative to a
 * cherry, and how steeply a longer run outpays a shorter one. Those are design
 * choices. The absolute scale is then solved for:
 *
 * <pre>
 *   rawRtp = SUM over (symbol, runLength) of  P(run) * shape(symbol, runLength)
 *   scale  = targetRtp / rawRtp
 *   payout(symbol, runLength) = scale * shape(symbol, runLength)
 * </pre>
 *
 * so the finished table returns exactly {@code 1 - houseEdge} by construction,
 * at every supported machine width.
 *
 * <h2>Why the edge is invariant to line count</h2>
 * Every {@link SlotsPayline} visits each column exactly once, and reel weights
 * depend only on the column. So each line's run-length distribution is
 * identical no matter its shape, and the machine's RTP equals one line's
 * expected return. Activating more lines multiplies both the stake and the
 * expected return by the same factor -- the edge does not move. That is what
 * lets column count and line count both be player-selectable without
 * re-deriving anything.
 */
public final class SlotsPaytable {

    /** Matches the plugin-wide convention used by Mines, Dragon Descent, Coin Flip, and RPS. */
    public static final double MIN_HOUSE_EDGE = 0.01;
    /** Roughly the generosity of a real online or high-limit slot; well short of a floor machine. */
    public static final double MAX_HOUSE_EDGE = 0.06;
    public static final double DEFAULT_HOUSE_EDGE = 0.03;

    /**
     * How much each additional matched reel multiplies a win. Chosen so the
     * top symbol at full width is a genuine jackpot without the short runs
     * becoming worthless.
     */
    private static final double LENGTH_BASE = 6.0;

    private final int columns;
    private final double houseEdge;
    private final Map<SlotsSymbol, double[]> multipliers;
    private final double theoreticalRtp;

    private SlotsPaytable(int columns, double houseEdge, Map<SlotsSymbol, double[]> multipliers, double theoreticalRtp) {
        this.columns = columns;
        this.houseEdge = houseEdge;
        this.multipliers = multipliers;
        this.theoreticalRtp = theoreticalRtp;
    }

    /** Clamps an arbitrary configured edge into the supported band. */
    public static double normalizeHouseEdge(double houseEdge) {
        if (Double.isNaN(houseEdge)) {
            return DEFAULT_HOUSE_EDGE;
        }
        return Math.max(MIN_HOUSE_EDGE, Math.min(MAX_HOUSE_EDGE, houseEdge));
    }

    public static SlotsPaytable forConfig(int columns, double houseEdge) {
        SlotsGeometry.requireSupportedColumnCount(columns);
        double edge = normalizeHouseEdge(houseEdge);
        double targetRtp = 1.0 - edge;

        double rawRtp = 0.0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            if (!symbol.pays()) {
                continue;
            }
            for (int run = symbol.minimumRun(); run <= columns; run++) {
                rawRtp += runProbability(symbol, run, columns) * shape(symbol, run);
            }
        }
        if (rawRtp <= 0.0) {
            throw new IllegalStateException("Paytable shape yields a zero-return machine; check SlotsSymbol pay weights.");
        }

        double scale = targetRtp / rawRtp;
        Map<SlotsSymbol, double[]> table = new EnumMap<>(SlotsSymbol.class);
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            double[] byRun = new double[columns + 1];
            if (symbol.pays()) {
                for (int run = symbol.minimumRun(); run <= columns; run++) {
                    byRun[run] = scale * shape(symbol, run);
                }
            }
            table.put(symbol, byRun);
        }

        return new SlotsPaytable(columns, edge, table, targetRtp);
    }

    /** Relative (unscaled) worth of one symbol at one run length. */
    private static double shape(SlotsSymbol symbol, int run) {
        return symbol.payWeight() * lengthFactor(run);
    }

    /** Each extra matched reel is worth {@link #LENGTH_BASE} times the last. */
    private static double lengthFactor(int run) {
        return Math.pow(LENGTH_BASE, run - SlotsSymbol.GLOBAL_MIN_RUN);
    }

    /**
     * Probability that a single payline shows a maximal left-to-right run of
     * <em>exactly</em> {@code run} copies of {@code symbol}.
     *
     * <p>A run shorter than the full width must be terminated by a different
     * symbol in the next column; a full-width run has nothing to terminate it.
     */
    public static double runProbability(SlotsSymbol symbol, int run, int columns) {
        SlotsGeometry.requireSupportedColumnCount(columns);
        if (run < 1 || run > columns) {
            return 0.0;
        }
        double p = symbol.probability();
        double matched = Math.pow(p, run);
        return (run == columns) ? matched : matched * (1.0 - p);
    }

    /**
     * Total return per unit staked on one line, for a run of {@code run}
     * copies of {@code symbol}. Zero when that run does not pay. The stake is
     * never separately returned on top of this.
     */
    public double multiplier(SlotsSymbol symbol, int run) {
        if (symbol == null || run < 1 || run > columns) {
            return 0.0;
        }
        double[] byRun = multipliers.get(symbol);
        return byRun == null ? 0.0 : byRun[run];
    }

    /** The largest single-line multiplier this table can award. */
    public double maxLineMultiplier() {
        double best = 0.0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            best = Math.max(best, multiplier(symbol, columns));
        }
        return best;
    }

    /**
     * Probability that a single line pays anything at all. Equal to the
     * chance its first {@code minimumRun} columns all match, summed over the
     * paying symbols.
     */
    public static double lineHitProbability() {
        double total = 0.0;
        for (SlotsSymbol symbol : SlotsSymbol.values()) {
            if (symbol.pays()) {
                total += Math.pow(symbol.probability(), symbol.minimumRun());
            }
        }
        return total;
    }

    public int columns() {
        return columns;
    }

    public double houseEdge() {
        return houseEdge;
    }

    /** Exactly {@code 1 - houseEdge} by construction; asserted in tests against a full enumeration. */
    public double theoreticalRtp() {
        return theoreticalRtp;
    }
}
