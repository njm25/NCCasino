package org.nc.nccasino.games.Slots;

/**
 * Pure, Bukkit-free schedule for one spin's reel motion: exactly which ticks
 * each reel advances a symbol on, when it lands, and whether the final reel
 * should hang for a beat first.
 *
 * <p>Nothing here can change a result. The committed {@link SlotsOutcome}
 * already exists before this plan is built; the plan only decides how it gets
 * revealed.
 */
public final class SlotsReelPlan {

    /**
     * Symbols at or above this pay weight are worth building tension around.
     * Anything cheaper stopping one reel short is not a near-miss anybody
     * cares about, and pausing for it would train players to ignore the pause.
     */
    private static final double ANTICIPATION_PAY_WEIGHT = SlotsSymbol.DIAMOND.payWeight();

    private final int columns;
    private final long[][] advanceTicks;
    private final long[] landingTicks;
    private final boolean anticipated;

    private SlotsReelPlan(int columns, long[][] advanceTicks, long[] landingTicks, boolean anticipated) {
        this.columns = columns;
        this.advanceTicks = advanceTicks;
        this.landingTicks = landingTicks;
        this.anticipated = anticipated;
    }

    public static SlotsReelPlan build(SlotsOutcome outcome, int activeLines) {
        int columns = outcome.columns();
        boolean anticipate = shouldAnticipate(outcome, activeLines);

        long[][] advances = new long[columns][];
        long[] landings = new long[columns];
        for (int reel = 0; reel < columns; reel++) {
            boolean isLast = reel == columns - 1;
            long extra = (isLast && anticipate) ? SlotsTiming.ANTICIPATION_TICKS : 0L;
            advances[reel] = buildSchedule(reel, extra);
            landings[reel] = advances[reel][advances[reel].length - 1] + SlotsTiming.REEL_LANDING_BOUNCE_TICKS;
        }
        return new SlotsReelPlan(columns, advances, landings, anticipate);
    }

    /**
     * Ticks (from spin start) on which this reel swaps to its next cosmetic
     * symbol. Full speed first, then {@link SlotsTiming#DECELERATION_STEPS}
     * progressively longer gaps so the reel visibly loses momentum.
     */
    private static long[] buildSchedule(int reel, long extraHangTicks) {
        long fullSpeedUntil = SlotsTiming.FIRST_REEL_SPIN_TICKS + ((long) reel * SlotsTiming.REEL_STAGGER_TICKS);

        int fullSpeedAdvances = (int) (fullSpeedUntil / SlotsTiming.SPIN_STEP_TICKS);
        long[] schedule = new long[fullSpeedAdvances + SlotsTiming.DECELERATION_STEPS];

        long tick = 0L;
        int index = 0;
        for (int i = 0; i < fullSpeedAdvances; i++) {
            schedule[index++] = tick;
            tick += SlotsTiming.SPIN_STEP_TICKS;
        }
        // The hang happens before the reel starts slowing, so an anticipated
        // reel keeps spinning at full speed rather than freezing mid-air.
        tick += extraHangTicks;
        for (int step = 1; step <= SlotsTiming.DECELERATION_STEPS; step++) {
            schedule[index++] = tick;
            tick += SlotsTiming.SPIN_STEP_TICKS + ((long) step * SlotsTiming.DECELERATION_GROWTH_TICKS);
        }
        return schedule;
    }

    /**
     * True when every reel but the last already shows the same high-value
     * symbol along an active line -- one reel away from a full-width win.
     */
    static boolean shouldAnticipate(SlotsOutcome outcome, int activeLines) {
        int columns = outcome.columns();
        if (columns < 3) {
            return false;
        }
        for (SlotsPaylineCatalog.Line line : SlotsPaylineCatalog.active(columns, outcome.rows(), activeLines)) {
            int[] rows = line.rows();
            SlotsSymbol first = outcome.symbolAt(rows[0], 0);
            if (first == null || !first.pays() || first.payWeight() < ANTICIPATION_PAY_WEIGHT) {
                continue;
            }
            boolean unbroken = true;
            for (int col = 1; col < columns - 1; col++) {
                if (outcome.symbolAt(rows[col], col) != first) {
                    unbroken = false;
                    break;
                }
            }
            if (unbroken) {
                return true;
            }
        }
        return false;
    }

    /** Whether this reel advances a cosmetic symbol on the given elapsed tick. */
    public boolean advancesAt(int reel, long elapsedTicks) {
        for (long tick : advanceTicks[reel]) {
            if (tick == elapsedTicks) {
                return true;
            }
        }
        return false;
    }

    public long landingTick(int reel) {
        return landingTicks[reel];
    }

    /**
     * How many scheduled advance events this reel has in total, from spin
     * start through landing. Anticipation only shifts <em>when</em> the
     * deceleration-phase advances happen (the extra hang before them), never
     * how many advances exist -- {@code buildSchedule}'s array length is
     * {@code fullSpeedAdvances + DECELERATION_STEPS} regardless of the hang.
     * This is what lets a reel's cosmetic starting position be computed
     * purely from its committed landing stop and this count: seeding at
     * {@code floorMod(committedStop - advanceCount(reel), SIZE)} and
     * advancing once per scheduled tick reaches the committed stop exactly
     * on the last advance, with no separate snap needed.
     */
    public int advanceCount(int reel) {
        return advanceTicks[reel].length;
    }

    public boolean isStopped(int reel, long elapsedTicks) {
        return elapsedTicks >= landingTicks[reel];
    }

    public boolean allStopped(long elapsedTicks) {
        return elapsedTicks >= landingTicks[columns - 1];
    }

    /** Tick at which the last reel has landed and win presentation may begin. */
    public long revealStartTick() {
        return landingTicks[columns - 1] + SlotsTiming.PRE_REVEAL_PAUSE_TICKS;
    }

    /** Whether this spin earned an anticipation pause on its final reel. */
    public boolean isAnticipated() {
        return anticipated;
    }

    public int columns() {
        return columns;
    }
}
