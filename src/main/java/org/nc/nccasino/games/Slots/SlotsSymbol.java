package org.nc.nccasino.games.Slots;

import org.bukkit.Material;

/**
 * The five reel symbols, their independent-sample weights (summing to
 * {@link #TOTAL_WEIGHT}), and the fixed three-of-a-kind payout multiplier
 * each awards on a winning payline. The paytable is audited (see
 * {@code SlotsMathTest#theoreticalRtpMatchesAuditedFigure}) -- values here
 * must never be changed without re-deriving the documented 91.197% RTP.
 */
public enum SlotsSymbol {
    CHERRY(40, 8, Material.SWEET_BERRIES),
    LEMON(25, 13, Material.YELLOW_DYE),
    BELL(18, 21, Material.BELL),
    DIAMOND(11, 39, Material.DIAMOND),
    SEVEN(6, 104, Material.REDSTONE_BLOCK);

    public static final int TOTAL_WEIGHT = 100;

    private final int weight;
    private final int multiplier;
    private final Material material;

    SlotsSymbol(int weight, int multiplier, Material material) {
        this.weight = weight;
        this.multiplier = multiplier;
        this.material = material;
    }

    public int weight() {
        return weight;
    }

    public int multiplier() {
        return multiplier;
    }

    public Material material() {
        return material;
    }
}
