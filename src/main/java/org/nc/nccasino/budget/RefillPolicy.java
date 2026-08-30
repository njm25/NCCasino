package org.nc.nccasino.budget;

import java.math.BigDecimal;

/**
 * Lazy refill arithmetic: what a dealer's balance should be when it is next
 * touched, given how long it has been since the last applied period.
 *
 * <p>Pure and total. It reads a clock value passed in rather than calling one,
 * so "the server was down for three days" and "someone changed the period" are
 * ordinary test cases rather than things that need a running server.
 *
 * <h2>Why lazy</h2>
 *
 * <p>A ticking task would cost a tick per dealer per period forever, and would
 * silently do nothing while the server was offline -- so a dealer would end up
 * with a different balance depending on whether anyone happened to be online.
 * Evaluating elapsed periods on access makes downtime irrelevant: the result
 * depends only on wall-clock time and the stored boundary.
 *
 * <h2>The boundary, and why it is not simply "now"</h2>
 *
 * <p>The stored boundary advances by whole periods only, never to the current
 * instant. Advancing to {@code now} would let frequent access drift the
 * schedule forward and quietly skip refills: a dealer touched at 59 minutes
 * past every hour would never complete an hour. Advancing by whole periods
 * keeps the schedule phase-stable and makes replaying the same period twice
 * impossible, which is what protects against a restart re-applying a refill.
 *
 * <h2>RESET and active reservations</h2>
 *
 * <p>The reset target is defined as the dealer's <em>total live balance</em>,
 * not its free balance. So a reset sets the balance to the target, except that
 * it never drops below what is already reserved -- money promised to games in
 * flight is never invalidated. Deliberately, reservations do not earn extra
 * free funds: a dealer with 900 reserved and a target of 1000 ends with 1000
 * live and 100 free, not 1900. The alternative (target as free balance) would
 * let a player with a large open commitment mint the house extra funding by
 * timing a reset, which is the more dangerous of the two readings.
 */
public final class RefillPolicy {

    private RefillPolicy() {
    }

    /**
     * @param liveBalance the balance after the refill
     * @param boundaryEpochSeconds the new last-applied boundary to persist
     * @param applied whether anything actually changed and must be written
     * @param periodsElapsed how many whole periods were consumed, for logging
     */
    public record Result(
        BigDecimal liveBalance,
        long boundaryEpochSeconds,
        boolean applied,
        long periodsElapsed
    ) {
    }

    /** Nothing to do; leaves both the balance and the stored boundary alone. */
    private static Result unchanged(BigDecimal liveBalance, long boundary) {
        return new Result(Money.of(liveBalance), boundary, false, 0L);
    }

    /**
     * Applies every whole period that has elapsed since
     * {@code boundaryEpochSeconds}.
     *
     * @param boundaryEpochSeconds the last applied boundary, or a
     *     non-positive value if this dealer has never refilled. In that case
     *     the clock starts now and nothing is granted -- a dealer created
     *     today must not immediately receive a year of back-dated refills.
     * @param reservedTotal what is currently promised to live commitments,
     *     which a RESET may never drop below
     * @param nowEpochSeconds the current time, passed in for testability
     */
    public static Result apply(
        DealerBudgetSettings settings,
        BigDecimal liveBalance,
        BigDecimal reservedTotal,
        long boundaryEpochSeconds,
        long nowEpochSeconds
    ) {
        BigDecimal live = Money.clampNonNegative(liveBalance);
        if (settings == null || !settings.hasRefill()) {
            return unchanged(live, boundaryEpochSeconds);
        }

        long period = settings.refillPeriodSeconds();
        if (period <= 0) {
            return unchanged(live, boundaryEpochSeconds);
        }

        if (boundaryEpochSeconds <= 0L) {
            // First contact: start the clock rather than back-dating.
            return new Result(live, nowEpochSeconds, true, 0L);
        }
        if (nowEpochSeconds < boundaryEpochSeconds) {
            // The system clock moved backwards. Re-anchor instead of granting
            // a refill for negative elapsed time or waiting out the drift.
            return new Result(live, nowEpochSeconds, true, 0L);
        }

        long elapsed = nowEpochSeconds - boundaryEpochSeconds;
        long periods = elapsed / period;
        if (periods <= 0) {
            return unchanged(live, boundaryEpochSeconds);
        }

        // Whole periods only, so the schedule keeps its phase and a period can
        // never be applied twice.
        long newBoundary = boundaryEpochSeconds + periods * period;

        BigDecimal updated = switch (settings.refillMode()) {
            case ADD -> applyAdd(settings, live, periods);
            case RESET -> applyReset(settings, live, Money.clampNonNegative(reservedTotal));
            case NONE -> live;
        };

        return new Result(updated, newBoundary, true, periods);
    }

    /**
     * Adds {@code amount} per elapsed period in one multiplication rather than
     * a loop, so a dealer untouched for a very long time costs the same as one
     * touched every hour.
     */
    private static BigDecimal applyAdd(DealerBudgetSettings settings, BigDecimal live, long periods) {
        BigDecimal amount = Money.clampNonNegative(settings.refillAmount());
        if (!Money.isPositive(amount)) {
            return live;
        }

        BigDecimal cap = settings.refillCap();
        if (cap != null && Money.atLeast(live, cap)) {
            // Already at or above the cap. A cap bounds growth; it never
            // confiscates a balance the dealer earned above it.
            return live;
        }

        long effectivePeriods = Math.min(periods, DealerBudgetSettings.MAX_CATCHUP_PERIODS);
        BigDecimal added = Money.multiply(amount, Money.of(effectivePeriods));
        BigDecimal grown = Money.min(Money.add(live, added), Money.MAX);
        return cap == null ? grown : Money.min(grown, Money.max(cap, live));
    }

    /**
     * Sets the live balance to the configured target, honoring active
     * reservations. See the class documentation for why the target is total
     * live balance rather than free balance.
     */
    private static BigDecimal applyReset(
        DealerBudgetSettings settings, BigDecimal live, BigDecimal reservedTotal) {

        BigDecimal target = Money.clampNonNegative(settings.resetTarget());
        if (!Money.isPositive(target)) {
            // A misconfigured target must not wipe a funded dealer.
            return live;
        }
        return Money.min(Money.max(target, reservedTotal), Money.MAX);
    }
}
