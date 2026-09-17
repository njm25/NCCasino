package org.nc.nccasino.games.Slots;

import java.math.BigDecimal;

/**
 * The ledger for one Auto Spin batch: how many paid spins it has actually
 * committed, and its authoritative net position.
 *
 * <p>Net is defined exactly as the redesign requires:
 *
 * <pre>net = total authoritative awarded returns - total successfully paid wagers</pre>
 *
 * so it is driven only by real economic events -- a wager that was actually
 * debited, and a payout the settlement system actually awarded -- never by
 * animation state, never by a rejected spin, and never by a Demo spin.
 *
 * <p>Kept in {@link BigDecimal} rather than {@code long} so the profit-target
 * and loss-limit comparisons against a fractional configured threshold are
 * exact at any magnitude, and so an enormous batch can never silently
 * overflow the very ledger meant to bound the player's losses.
 *
 * <p>A fresh instance is created every time Auto Spin starts and discarded
 * when it stops, which is what makes each batch's limits independent.
 */
public final class SlotsAutoSpinBatch {

    private long committedSpins;
    private BigDecimal net = BigDecimal.ZERO;

    /**
     * Records a wager that has actually been debited and accepted, starting
     * a new committed spin. Demo spins and rejected attempts never reach
     * here, so {@link #committedSpins()} counts exactly what the redesign
     * says it counts.
     */
    public void recordCommittedWager(long totalBetUnits) {
        committedSpins++;
        net = net.subtract(BigDecimal.valueOf(totalBetUnits));
    }

    /**
     * Records an authoritative award. Called once per settled spin with the
     * payout the settlement system actually awarded (delivered live, banked,
     * or durably queued -- all three are awarded under this game's settlement
     * semantics); a payout that could not be awarded at all leaves the ledger
     * alone and stops Auto Spin through
     * {@link SlotsAutoSpinRules#afterSettlement}.
     */
    public void recordAward(long payoutUnits) {
        if (payoutUnits <= 0) {
            return;
        }
        net = net.add(BigDecimal.valueOf(payoutUnits));
    }

    /** How many paid spins this batch has successfully committed. */
    public long committedSpins() {
        return committedSpins;
    }

    /** The batch's authoritative net: awarded returns minus paid wagers. */
    public BigDecimal net() {
        return net;
    }

    /** Clears the ledger completely, for the start of a brand-new batch. */
    public void reset() {
        committedSpins = 0;
        net = BigDecimal.ZERO;
    }

    @Override
    public String toString() {
        return "SlotsAutoSpinBatch[spins=" + committedSpins + ", net=" + net.toPlainString() + "]";
    }
}
