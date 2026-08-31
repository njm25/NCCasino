package org.nc.nccasino.games.Slots;

import java.util.EnumMap;
import java.util.Map;

/**
 * How a machine's return arrives: through frequent smaller payouts, or rarer
 * larger ones. House edge and variance are independent settings -- every
 * level here is renormalized by {@link SlotsPaytable} to the same configured
 * return-to-player target, so choosing a level changes the shape of the
 * paytable, never the house edge.
 *
 * <p>Two parameters distinguish one level from another:
 *
 * <ul>
 *   <li>{@link #weights()} -- each symbol's sampling weight, which sets hit
 *       frequency directly. A level with more weight on paying symbols and
 *       less on {@link SlotsSymbol#BLANK} hits more often.
 *   <li>{@link #lengthBase()} -- how much more valuable each additional
 *       matched reel is (the paytable's own {@code shape()} exponent base).
 *       A higher value concentrates return into full-width runs, which
 *       {@link SlotsPaytable}'s renormalization step compresses everything
 *       else to compensate -- raising this is what produces a lower hit rate
 *       feeling and a higher realized jackpot ceiling.
 * </ul>
 *
 * <p>{@link #BALANCED} uses exactly {@link SlotsSymbol}'s own weights and the
 * historical length base of 6.0, so it reproduces the machine's original
 * behavior byte-for-byte -- this is asserted by
 * {@code SlotsVarianceTest.balancedMatchesTheOriginalFixedShapeExactly}.
 */
public enum SlotsVariance {

    /** Frequent, modest returns; the lowest top multiplier. */
    STEADY(3.0, weights(20, 30, 22, 15, 9, 4)),

    /** Somewhat less steady than {@link #STEADY}. */
    LOW(4.5, weights(25, 26, 20, 14, 10, 5)),

    /** The general-purpose default and the original design's reference shape. */
    BALANCED(6.0, weights(30, 22, 18, 14, 10, 6)),

    /** Rarer wins, substantially larger jackpots. */
    HIGH(9.0, weights(36, 18, 15, 13, 11, 7)),

    /** Lowest hit frequency, largest supported prizes. */
    HIGH_ROLLER(14.0, weights(45, 12, 12, 11, 11, 9));

    private final double lengthBase;
    private final Map<SlotsSymbol, Integer> weights;

    SlotsVariance(double lengthBase, Map<SlotsSymbol, Integer> weights) {
        this.lengthBase = lengthBase;
        this.weights = weights;
    }

    /** In {@link SlotsSymbol} declaration order: BLANK, CHERRY, LEMON, BELL, DIAMOND, SEVEN. */
    private static Map<SlotsSymbol, Integer> weights(
        int blank, int cherry, int lemon, int bell, int diamond, int seven) {

        Map<SlotsSymbol, Integer> map = new EnumMap<>(SlotsSymbol.class);
        map.put(SlotsSymbol.BLANK, blank);
        map.put(SlotsSymbol.CHERRY, cherry);
        map.put(SlotsSymbol.LEMON, lemon);
        map.put(SlotsSymbol.BELL, bell);
        map.put(SlotsSymbol.DIAMOND, diamond);
        map.put(SlotsSymbol.SEVEN, seven);
        return map;
    }

    /** This level's sampling weight for {@code symbol}, out of {@link SlotsSymbol#TOTAL_WEIGHT}. */
    public int weight(SlotsSymbol symbol) {
        return weights.getOrDefault(symbol, 0);
    }

    /** Probability of {@code symbol} at any single reel position under this level. */
    public double probability(SlotsSymbol symbol) {
        return (double) weight(symbol) / SlotsSymbol.TOTAL_WEIGHT;
    }

    public double lengthBase() {
        return lengthBase;
    }

    public static SlotsVariance parse(String raw, SlotsVariance fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    static {
        for (SlotsVariance variance : values()) {
            int sum = 0;
            for (SlotsSymbol symbol : SlotsSymbol.values()) {
                sum += variance.weight(symbol);
            }
            if (sum != SlotsSymbol.TOTAL_WEIGHT) {
                throw new ExceptionInInitializerError("SlotsVariance." + variance
                    + " weights sum to " + sum + ", not " + SlotsSymbol.TOTAL_WEIGHT);
            }
        }
    }
}
