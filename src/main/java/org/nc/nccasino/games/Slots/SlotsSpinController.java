package org.nc.nccasino.games.Slots;

import java.util.function.LongPredicate;

/**
 * The whole per-player spin/settlement lifecycle as one pure, Bukkit-free
 * state holder. {@link SlotsMachine} (the inventory/UI layer) owns exactly
 * one of these and supplies the actual debit/credit/queue side effects as
 * predicates; this class is solely responsible for sequencing them against
 * {@link SlotsStateMachine} correctly and for holding the committed
 * outcome/payout in between.
 *
 * <p>Two financial invariants are enforced here rather than left to callers:
 * <ul>
 *   <li>a spin that is about to debit the player first normalizes a
 *   {@link SlotsSessionState#RESOLVED} table back to {@link SlotsSessionState#IDLE}
 *   -- so a successful debit can never be immediately followed by an
 *   {@link IllegalStateException} merely because the previous round had
 *   already resolved;</li>
 *   <li>a committed positive payout that neither the live-delivery nor the
 *   durable-queue predicate can accept moves to
 *   {@link SlotsSessionState#SETTLEMENT_FAILED} and keeps
 *   {@link #pendingPayoutAmount()} intact rather than clearing it -- so the
 *   obligation is never silently dropped and no new spin can be accepted
 *   until {@link #retrySettlement} resolves it.</li>
 * </ul>
 */
public final class SlotsSpinController {

    public enum RejectReason {
        NOT_READY,
        INVALID_DENOMINATION,
        WAGER_OVERFLOW,
        BET_TOO_LARGE_FOR_MODE,
        INSUFFICIENT_FUNDS
    }

    public sealed interface SpinAttempt permits SpinAttempt.Accepted, SpinAttempt.Rejected {
        record Accepted(SlotsOutcome outcome, long totalBetUnits, long payout, long generation) implements SpinAttempt {
        }

        record Rejected(RejectReason reason) implements SpinAttempt {
        }
    }

    private SlotsSessionState state = SlotsSessionState.IDLE;
    private long generation = 0;
    private SlotsOutcome currentOutcome;
    private long pendingPayoutAmount = 0;
    private long lastWinAmount = -1;

    public SlotsSessionState state() {
        return state;
    }

    public long generation() {
        return generation;
    }

    public SlotsOutcome currentOutcome() {
        return currentOutcome;
    }

    public long pendingPayoutAmount() {
        return pendingPayoutAmount;
    }

    public long lastWinAmount() {
        return lastWinAmount;
    }

    public boolean isReadyForSpin() {
        return state == SlotsSessionState.IDLE || state == SlotsSessionState.RESOLVED;
    }

    /**
     * Validates and, if accepted, fully resolves one spin's outcome and
     * payout synchronously. {@code debit} is invoked at most once, and only
     * after every pre-debit validation (readiness, denomination, overflow,
     * item-mode ceiling) has already passed -- a rejected attempt never
     * touches the player's balance.
     *
     * @param denomUnits per-line wager, in whole currency units
     * @param columns machine width for this spin (3, 5, or 7)
     * @param activeLines how many paylines the player has switched on
     * @param itemMode whether payouts for this table can only be delivered
     *     as physical items/int-precision balances (no exact large-payout
     *     path), so the item-mode payout ceiling applies
     * @param paytable the multipliers derived from this dealer's configured
     *     house edge; also supplies the worst-case exposure probe
     * @param rng the (production or test) randomness source for outcome generation
     * @param debit attempts to withdraw the given total wager; returning
     *     {@code false} means insufficient funds and nothing was withdrawn
     */
    public SpinAttempt trySpin(
        long denomUnits,
        int columns,
        int activeLines,
        boolean itemMode,
        SlotsPaytable paytable,
        SlotsRandomSource rng,
        LongPredicate debit
    ) {
        if (!isReadyForSpin()) {
            return new SpinAttempt.Rejected(RejectReason.NOT_READY);
        }
        if (denomUnits <= 0) {
            return new SpinAttempt.Rejected(RejectReason.INVALID_DENOMINATION);
        }

        long totalBetUnits;
        long maxPossiblePayout;
        try {
            totalBetUnits = SlotsMath.totalBet(denomUnits, activeLines);
            maxPossiblePayout = SlotsMath.maxPossiblePayout(denomUnits, activeLines, paytable);
        } catch (ArithmeticException e) {
            return new SpinAttempt.Rejected(RejectReason.WAGER_OVERFLOW);
        }
        if (itemMode && maxPossiblePayout > SlotsMath.MAX_ITEM_MODE_PAYOUT) {
            return new SpinAttempt.Rejected(RejectReason.BET_TOO_LARGE_FOR_MODE);
        }

        if (state == SlotsSessionState.RESOLVED) {
            state = SlotsStateMachine.transition(state, SlotsSessionState.IDLE);
        }

        if (!debit.test(totalBetUnits)) {
            return new SpinAttempt.Rejected(RejectReason.INSUFFICIENT_FUNDS);
        }

        state = SlotsStateMachine.transition(state, SlotsSessionState.DEBIT_ACCEPTED);
        generation++;
        SlotsOutcome outcome = SlotsSpinGenerator.generate(columns, rng);
        currentOutcome = outcome;
        state = SlotsStateMachine.transition(state, SlotsSessionState.RESULT_COMMITTED);
        pendingPayoutAmount = SlotsMath.totalPayout(outcome, activeLines, denomUnits, paytable);

        return new SpinAttempt.Accepted(outcome, totalBetUnits, pendingPayoutAmount, generation);
    }

    public void beginAnimating() {
        state = SlotsStateMachine.transition(state, SlotsSessionState.ANIMATING);
    }

    /**
     * Resolves the committed payout exactly once. {@code liveDeliver} is
     * tried first; only on its failure is {@code durableQueue} tried. A
     * payout of zero is a valid completed loss and neither predicate is
     * invoked. On a {@link SlotsSettlementResult#FAILED} result the amount
     * is retained (not cleared) and the table moves to
     * {@link SlotsSessionState#SETTLEMENT_FAILED}; on any other result it
     * is cleared and the table moves to {@link SlotsSessionState#RESOLVED}.
     */
    public SlotsSettlementResult settle(LongPredicate liveDeliver, LongPredicate durableQueue) {
        state = SlotsStateMachine.transition(state, SlotsSessionState.SETTLING);
        SlotsSettlementResult result = resolvePayout(liveDeliver, durableQueue);
        lastWinAmount = pendingPayoutAmount;
        applySettlementResult(result);
        return result;
    }

    /**
     * Re-attempts a previously {@link SlotsSettlementResult#FAILED}
     * settlement without re-deriving or re-crediting anything already
     * accounted for -- the retained {@link #pendingPayoutAmount()} is the
     * single source of truth for what is still owed, so a retry can never
     * pay twice.
     */
    public SlotsSettlementResult retrySettlement(LongPredicate liveDeliver, LongPredicate durableQueue) {
        if (state != SlotsSessionState.SETTLEMENT_FAILED) {
            throw new IllegalStateException("retrySettlement called outside SETTLEMENT_FAILED: " + state);
        }
        SlotsSettlementResult result = resolvePayout(liveDeliver, durableQueue);
        applySettlementResult(result);
        return result;
    }

    private SlotsSettlementResult resolvePayout(LongPredicate liveDeliver, LongPredicate durableQueue) {
        long payout = pendingPayoutAmount;
        if (payout <= 0) {
            return SlotsSettlementResult.DELIVERED;
        }
        if (liveDeliver.test(payout)) {
            return SlotsSettlementResult.DELIVERED;
        }
        if (durableQueue.test(payout)) {
            return SlotsSettlementResult.QUEUED;
        }
        return SlotsSettlementResult.FAILED;
    }

    private void applySettlementResult(SlotsSettlementResult result) {
        if (result == SlotsSettlementResult.FAILED) {
            // A retry that fails again is a no-op transition -- the state
            // machine forbids self-transitions, and there is nothing to
            // change: the obligation was already retained.
            if (state != SlotsSessionState.SETTLEMENT_FAILED) {
                state = SlotsStateMachine.transition(state, SlotsSessionState.SETTLEMENT_FAILED);
            }
            return;
        }
        state = SlotsStateMachine.transition(state, SlotsSessionState.RESOLVED);
        pendingPayoutAmount = 0;
    }

    public void terminate() {
        if (state != SlotsSessionState.TERMINATED) {
            state = SlotsStateMachine.transition(state, SlotsSessionState.TERMINATED);
        }
    }
}
