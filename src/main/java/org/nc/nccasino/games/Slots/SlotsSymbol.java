package org.nc.nccasino.games.Slots;

import org.bukkit.Material;

/**
 * The reel symbols, their per-reel sampling weights, and the <em>relative</em>
 * shape of what each one pays.
 *
 * <p>Critically, these carry no absolute payout multiplier. Real multipliers
 * are derived at runtime by {@link SlotsPaytable} from the dealer's configured
 * house edge, exactly the way Mines and Dragon Descent derive their payout
 * multipliers from a pinned edge rather than hardcoding them. {@link #payWeight()}
 * only fixes the paytable's <em>shape</em> (how much a seven is worth relative
 * to a cherry); the scale that makes the whole machine land on its configured
 * RTP is computed, never hand-tuned.
 *
 * <p>{@link #BLANK} is a real, weighted, non-paying symbol. It is what lets a
 * run break mid-line, which is what makes a near-miss ("seven, seven, blank")
 * possible at all -- the previous paytable had no blank, so every cell always
 * showed a paying symbol and the grid never read as a machine with a payline
 * running through it.
 */
public enum SlotsSymbol {
    /** Non-paying filler. Breaks runs and creates near-misses. */
    BLANK(30, 0.0, 0, Material.GRAY_STAINED_GLASS_PANE),
    /**
     * Pays from a run of two, the way a real fruit machine pays two cherries.
     * This single exception is what keeps hit frequency in real-slot territory
     * (roughly 26% on five lines, 41% on nine) instead of the ~10% a
     * three-minimum-everywhere table would give.
     */
    CHERRY(22, 1.0, 2, Material.SWEET_BERRIES),
    LEMON(18, 1.9, 3, Material.YELLOW_DYE),
    BELL(14, 3.6, 3, Material.BELL),
    DIAMOND(10, 8.0, 3, Material.DIAMOND),
    SEVEN(6, 22.0, 3, Material.REDSTONE_BLOCK);

    /** Sampling weights sum to this on every reel. */
    public static final int TOTAL_WEIGHT = 100;

    /** The shortest run any symbol can pay on -- the floor for the length-factor curve. */
    public static final int GLOBAL_MIN_RUN = 2;

    private final int weight;
    private final double payWeight;
    private final int minimumRun;
    private final Material material;

    SlotsSymbol(int weight, double payWeight, int minimumRun, Material material) {
        this.weight = weight;
        this.payWeight = payWeight;
        this.minimumRun = minimumRun;
        this.material = material;
    }

    /**
     * Shortest consecutive left-to-right run this symbol pays on, or 0 if it
     * never pays.
     */
    public int minimumRun() {
        return minimumRun;
    }

    public int weight() {
        return weight;
    }

    /**
     * Relative payout weight within the paytable's shape. Zero means the
     * symbol never pays. Absolute value is meaningless on its own -- only the
     * ratio between symbols matters, since {@link SlotsPaytable} rescales the
     * whole table to hit the configured RTP.
     */
    public double payWeight() {
        return payWeight;
    }

    public boolean pays() {
        return payWeight > 0.0;
    }

    public Material material() {
        return material;
    }

    /** Probability of this symbol on any single reel position. */
    public double probability() {
        return (double) weight / TOTAL_WEIGHT;
    }

    /** Symbols that can actually form a winning run, in ascending pay order. */
    public static SlotsSymbol[] payingSymbols() {
        return new SlotsSymbol[] {CHERRY, LEMON, BELL, DIAMOND, SEVEN};
    }

    static {
        int sum = 0;
        for (SlotsSymbol symbol : values()) {
            sum += symbol.weight;
        }
        if (sum != TOTAL_WEIGHT) {
            throw new ExceptionInInitializerError(
                "SlotsSymbol weights must sum to " + TOTAL_WEIGHT + " but sum to " + sum);
        }
    }
}
