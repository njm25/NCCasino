package org.nc.nccasino.games.Slots;

import java.math.BigDecimal;

/**
 * The pure Auto Spin stop decisions, split into the two moments they can
 * actually be made at.
 *
 * <p>{@link #beforeNextSpin} runs <em>before</em> the next wager is
 * committed. The loss-limit guard has to live here rather than only after
 * settlement: stopping only once the limit has already been passed would
 * knowingly overshoot the player's chosen maximum loss by one whole wager.
 *
 * <p>{@link #afterSettlement} runs once a committed spin has actually
 * settled, against the same batch ledger, and is what stops on a win, a big
 * win, a reached profit target, an exhausted spin limit, or a settlement
 * failure.
 *
 * <p>Nothing here can cancel or refund a spin that is already committed --
 * these decide only whether the loop may start <em>another</em> one.
 */
public final class SlotsAutoSpinRules {

    private SlotsAutoSpinRules() {
    }

    /** Why Auto Spin stopped, so the player can be told something specific. */
    public enum StopReason {
        SPIN_LIMIT_REACHED,
        WIN,
        BIG_WIN,
        PROFIT_TARGET_REACHED,
        LOSS_LIMIT_REACHED,
        SETTLEMENT_FAILED,
        SPIN_REJECTED;

        /** This reason's {@code slots.auto-stop-*} localization key. */
        public String messageKey() {
            return "slots.auto-stop-" + name().toLowerCase().replace('_', '-');
        }
    }

    /**
     * Whether the loop may commit one more wager.
     *
     * @param settings the batch's settings snapshot
     * @param batch the batch's live ledger
     * @param nextTotalBetUnits the exact total bet the next spin would place
     * @return {@code null} to proceed, or the reason to stop instead
     */
    public static StopReason beforeNextSpin(
        SlotsAutoSpinSettings settings, SlotsAutoSpinBatch batch, long nextTotalBetUnits) {

        if (settings == null || batch == null) {
            return StopReason.SPIN_REJECTED;
        }
        if (settings.hasSpinLimit() && batch.committedSpins() >= settings.spinLimit()) {
            return StopReason.SPIN_LIMIT_REACHED;
        }
        if (settings.hasLossLimit()) {
            // Would paying this wager put the batch past the chosen maximum
            // loss? net - nextBet < -lossLimit.
            BigDecimal projected = batch.net().subtract(BigDecimal.valueOf(nextTotalBetUnits));
            if (projected.compareTo(settings.lossLimitExact().negate()) < 0) {
                return StopReason.LOSS_LIMIT_REACHED;
            }
        }
        return null;
    }

    /**
     * Whether the just-settled spin ends the batch.
     *
     * @param settings the batch's settings snapshot
     * @param batch the batch's live ledger, already updated with this spin's
     *     wager and award
     * @param spinTotalBetUnits the total bet of the spin that just settled
     * @param spinPayoutUnits the payout that spin was awarded
     * @param result the authoritative settlement result
     * @return {@code null} to keep going, or the reason to stop
     */
    public static StopReason afterSettlement(
        SlotsAutoSpinSettings settings,
        SlotsAutoSpinBatch batch,
        long spinTotalBetUnits,
        long spinPayoutUnits,
        SlotsSettlementResult result) {

        if (settings == null || batch == null) {
            return StopReason.SETTLEMENT_FAILED;
        }
        if (SlotsAutoSpinLifecycle.stopsOn(result)) {
            return StopReason.SETTLEMENT_FAILED;
        }
        if (settings.stopOnAnyWin() && spinPayoutUnits > 0) {
            return StopReason.WIN;
        }
        if (isBigWin(settings, spinTotalBetUnits, spinPayoutUnits)) {
            return StopReason.BIG_WIN;
        }
        if (settings.hasProfitTarget()
            && batch.net().compareTo(settings.profitTargetExact()) >= 0) {
            return StopReason.PROFIT_TARGET_REACHED;
        }
        if (settings.hasLossLimit()
            && batch.net().compareTo(settings.lossLimitExact().negate()) <= 0) {
            return StopReason.LOSS_LIMIT_REACHED;
        }
        if (settings.hasSpinLimit() && batch.committedSpins() >= settings.spinLimit()) {
            return StopReason.SPIN_LIMIT_REACHED;
        }
        return null;
    }

    /**
     * A big win is a settled spin whose <em>total returned payout</em> is at
     * least the configured multiple of that same spin's total bet. Compared
     * exactly, so an enormous payout can never be misjudged by a
     * double-precision rounding step.
     */
    public static boolean isBigWin(SlotsAutoSpinSettings settings, long totalBetUnits, long payoutUnits) {
        if (settings == null || !settings.hasBigWinMultiplier() || payoutUnits <= 0 || totalBetUnits <= 0) {
            return false;
        }
        BigDecimal threshold = BigDecimal.valueOf(settings.bigWinMultiplier())
            .multiply(BigDecimal.valueOf(totalBetUnits));
        return BigDecimal.valueOf(payoutUnits).compareTo(threshold) >= 0;
    }
}
