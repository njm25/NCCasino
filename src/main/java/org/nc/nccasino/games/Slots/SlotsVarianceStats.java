package org.nc.nccasino.games.Slots;

/**
 * A settings-preview summary for one variance level at one machine width --
 * exactly the numbers section 14 of the design asks a Slots settings preview
 * to show: relative hit frequency, top multiplier, and maximum possible
 * payout at a given denomination.
 *
 * <p>This is a pure data structure with no Bukkit dependency, produced so a
 * future settings-menu change (not made in this pass -- see the design's GUI
 * approval requirement) has real numbers to render rather than needing to
 * recompute them inline. {@link #forConfig} is the only way to build one, so
 * every field is always self-consistent with an actual derivable paytable.
 *
 * @param variance which level this describes
 * @param columns the machine width the preview is for
 * @param lineHitProbability chance a single active line pays anything at all
 * @param maxLineMultiplier the largest single-line return this level can pay
 * @param theoreticalRtp the exact return-to-player this level reproduces --
 *     identical across every level at the same configured house edge, shown
 *     to make plain that variance does not change it
 * @param maxPossiblePayoutAtDenomination the largest total payout reachable
 *     at one concrete per-line wager and active-line count, using the same
 *     ceiling {@link SlotsMath#maxPossiblePayout} exposes to the budget system
 */
public record SlotsVarianceStats(
    SlotsVariance variance,
    int columns,
    double lineHitProbability,
    double maxLineMultiplier,
    double theoreticalRtp,
    long maxPossiblePayoutAtDenomination
) {

    /**
     * @param perLineWager the denomination and @param activeLines the line
     *     count the preview's "maximum possible payout" figure should use --
     *     callers typically pass the dealer's currently selected values
     */
    public static SlotsVarianceStats forConfig(
        int columns, double houseEdge, SlotsVariance variance, long perLineWager, int activeLines) {

        SlotsPaytable paytable = SlotsPaytable.forConfig(columns, houseEdge, variance);
        long maxPayout = SlotsMath.maxPossiblePayout(perLineWager, activeLines, paytable);
        return new SlotsVarianceStats(
            variance,
            columns,
            SlotsPaytable.lineHitProbability(variance),
            paytable.maxLineMultiplier(),
            paytable.theoreticalRtp(),
            maxPayout);
    }

    /** One row per declared level, for a side-by-side comparison preview. */
    public static SlotsVarianceStats[] allLevels(
        int columns, double houseEdge, long perLineWager, int activeLines) {

        SlotsVariance[] levels = SlotsVariance.values();
        SlotsVarianceStats[] rows = new SlotsVarianceStats[levels.length];
        for (int i = 0; i < levels.length; i++) {
            rows[i] = forConfig(columns, houseEdge, levels[i], perLineWager, activeLines);
        }
        return rows;
    }
}
