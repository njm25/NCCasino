package org.nc.nccasino.games.Slots;

import java.math.BigDecimal;

/**
 * One player's Auto Spin configuration: an immutable value object, so a
 * settings edit can never mutate a batch that is already running off an
 * earlier snapshot.
 *
 * <p>The defaults are exactly the redesign's: a spin limit of 15, and every
 * stop condition off. "Off" is represented as a non-positive value
 * throughout, and {@link #UNLIMITED_SPINS} is the one negative sentinel with
 * its own meaning -- a spin limit of "unlimited" is genuinely allowed, and
 * is not the same thing as a stop condition being off.
 *
 * <p>The gameplay spin speed is deliberately <em>not</em> here. Speed is
 * owned by the main Clock's right-click cycle and is not an Auto Spin
 * setting, which is why {@link #defaults()} (and so Reset Auto Settings)
 * cannot possibly disturb it.
 */
public final class SlotsAutoSpinSettings {

    /** The default spin limit for a fresh session and for Reset Auto Settings. */
    public static final long DEFAULT_SPIN_LIMIT = 15L;

    /** A spin limit of "keep going until something else stops it". */
    public static final long UNLIMITED_SPINS = -1L;

    private static final SlotsAutoSpinSettings DEFAULTS =
        new SlotsAutoSpinSettings(DEFAULT_SPIN_LIMIT, false, 0.0, 0.0, 0.0);

    private final long spinLimit;
    private final boolean stopOnAnyWin;
    private final double bigWinMultiplier;
    private final double profitTarget;
    private final double lossLimit;

    private SlotsAutoSpinSettings(
        long spinLimit, boolean stopOnAnyWin, double bigWinMultiplier, double profitTarget, double lossLimit) {

        this.spinLimit = normalizeSpinLimit(spinLimit);
        this.stopOnAnyWin = stopOnAnyWin;
        this.bigWinMultiplier = normalizeOffable(bigWinMultiplier);
        this.profitTarget = normalizeOffable(profitTarget);
        this.lossLimit = normalizeOffable(lossLimit);
    }

    /** Spin limit 15; Stop on Any Win, Big Win, Profit Target and Loss Limit all off. */
    public static SlotsAutoSpinSettings defaults() {
        return DEFAULTS;
    }

    public static SlotsAutoSpinSettings of(
        long spinLimit, boolean stopOnAnyWin, double bigWinMultiplier, double profitTarget, double lossLimit) {
        return new SlotsAutoSpinSettings(spinLimit, stopOnAnyWin, bigWinMultiplier, profitTarget, lossLimit);
    }

    private static long normalizeSpinLimit(long raw) {
        return raw <= 0 ? UNLIMITED_SPINS : raw;
    }

    /** Any non-finite or non-positive configured threshold means "off", stored canonically as 0. */
    private static double normalizeOffable(double raw) {
        return (!Double.isFinite(raw) || raw <= 0.0) ? 0.0 : raw;
    }

    /** How many spins one batch may commit, or {@link #UNLIMITED_SPINS}. */
    public long spinLimit() {
        return spinLimit;
    }

    public boolean hasSpinLimit() {
        return spinLimit > 0;
    }

    public boolean stopOnAnyWin() {
        return stopOnAnyWin;
    }

    /** The configured big-win multiple of the total bet, or 0 when off. */
    public double bigWinMultiplier() {
        return bigWinMultiplier;
    }

    public boolean hasBigWinMultiplier() {
        return bigWinMultiplier > 0.0;
    }

    /** The configured batch profit target, or 0 when off. */
    public double profitTarget() {
        return profitTarget;
    }

    public boolean hasProfitTarget() {
        return profitTarget > 0.0;
    }

    /** The configured batch loss limit, or 0 when off. */
    public double lossLimit() {
        return lossLimit;
    }

    public boolean hasLossLimit() {
        return lossLimit > 0.0;
    }

    public SlotsAutoSpinSettings withSpinLimit(long newSpinLimit) {
        return new SlotsAutoSpinSettings(newSpinLimit, stopOnAnyWin, bigWinMultiplier, profitTarget, lossLimit);
    }

    public SlotsAutoSpinSettings withStopOnAnyWin(boolean newStopOnAnyWin) {
        return new SlotsAutoSpinSettings(spinLimit, newStopOnAnyWin, bigWinMultiplier, profitTarget, lossLimit);
    }

    public SlotsAutoSpinSettings withBigWinMultiplier(double newBigWinMultiplier) {
        return new SlotsAutoSpinSettings(spinLimit, stopOnAnyWin, newBigWinMultiplier, profitTarget, lossLimit);
    }

    public SlotsAutoSpinSettings withProfitTarget(double newProfitTarget) {
        return new SlotsAutoSpinSettings(spinLimit, stopOnAnyWin, bigWinMultiplier, newProfitTarget, lossLimit);
    }

    public SlotsAutoSpinSettings withLossLimit(double newLossLimit) {
        return new SlotsAutoSpinSettings(spinLimit, stopOnAnyWin, bigWinMultiplier, profitTarget, newLossLimit);
    }

    public SlotsAutoSpinSettings toggleStopOnAnyWin() {
        return withStopOnAnyWin(!stopOnAnyWin);
    }

    /** {@link #profitTarget()} as an exact decimal, for comparison against a long batch ledger. */
    public BigDecimal profitTargetExact() {
        return BigDecimal.valueOf(profitTarget);
    }

    /** {@link #lossLimit()} as an exact decimal, for comparison against a long batch ledger. */
    public BigDecimal lossLimitExact() {
        return BigDecimal.valueOf(lossLimit);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SlotsAutoSpinSettings that)) {
            return false;
        }
        return spinLimit == that.spinLimit
            && stopOnAnyWin == that.stopOnAnyWin
            && Double.compare(bigWinMultiplier, that.bigWinMultiplier) == 0
            && Double.compare(profitTarget, that.profitTarget) == 0
            && Double.compare(lossLimit, that.lossLimit) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(spinLimit, stopOnAnyWin, bigWinMultiplier, profitTarget, lossLimit);
    }

    @Override
    public String toString() {
        return "SlotsAutoSpinSettings[spinLimit=" + spinLimit
            + ", stopOnAnyWin=" + stopOnAnyWin
            + ", bigWinMultiplier=" + bigWinMultiplier
            + ", profitTarget=" + profitTarget
            + ", lossLimit=" + lossLimit + "]";
    }
}
