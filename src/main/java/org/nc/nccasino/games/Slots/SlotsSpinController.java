package org.nc.nccasino.games.Slots;

import org.nc.nccasino.budget.AdmissionDecision;
import org.nc.nccasino.budget.Commitment;

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

    /**
     * A live payout attempt that may only partly succeed.
     *
     * <p>Returning the amount still outstanding rather than a bare
     * success/failure flag is what makes partial delivery safe: item payouts
     * can put some of the win in the player's inventory and bank the rest, and
     * a settlement that reports "failed" while having already handed over part
     * of the money would otherwise be re-paid in full on retry.
     */
    @FunctionalInterface
    public interface PayoutDelivery {
        /**
         * @param owed the amount still committed to the player
         * @return how much of {@code owed} is STILL outstanding afterwards;
         *     {@code 0} means fully delivered
         */
        long deliver(long owed);
    }

    public enum RejectReason {
        NOT_READY,
        INVALID_DENOMINATION,
        WAGER_OVERFLOW,
        BET_TOO_LARGE_FOR_MODE,
        INSUFFICIENT_FUNDS,
        /**
         * The dealer cannot cover this spin's worst case. Distinct from
         * {@link #INSUFFICIENT_FUNDS}, which is about the <em>player's</em>
         * balance -- conflating them would tell a player to top up when the
         * machine is the one that is short.
         */
        DEALER_CANNOT_COVER
    }

    public sealed interface SpinAttempt permits SpinAttempt.Accepted, SpinAttempt.Rejected {
        record Accepted(SlotsOutcome outcome, long totalBetUnits, long payout, long generation) implements SpinAttempt {
        }

        /**
         * @param dealerDecision the budget's own verdict when
         *     {@code reason} is {@link RejectReason#DEALER_CANNOT_COVER}, and
         *     {@code null} otherwise. Carried separately so a permanently
         *     over-tier wager and a temporary shortage stay distinguishable in
         *     player messaging -- telling a player to "try again later" about a
         *     wager this machine will never accept is its own small cruelty.
         */
        record Rejected(RejectReason reason, AdmissionDecision dealerDecision) implements SpinAttempt {
            public Rejected(RejectReason reason) {
                this(reason, null);
            }
        }
    }

    private SlotsSessionState state = SlotsSessionState.IDLE;
    private long generation = 0;
    private SlotsOutcome currentOutcome;
    private long pendingPayoutAmount = 0;
    private long lastWinAmount = -1;
    /**
     * The dealer-budget promise backing the committed spin, held from
     * underwriting until it is settled exactly once. Null between rounds and
     * after settlement, which is what stops a retry touching the budget again.
     */
    private Commitment commitment;

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

    /** The open dealer-budget promise, or {@code null} when nothing is owed. */
    public Commitment commitment() {
        return commitment;
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
     * @param underwriting the dealer-budget gate. Consulted after the cheap
     *     validations but strictly before the player is debited and before any
     *     outcome exists, so a dealer that cannot cover the worst case refuses
     *     the spin rather than discovering the problem once a jackpot is
     *     already committed.
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
        return trySpin(denomUnits, columns, activeLines, itemMode, paytable, rng,
            SlotsUnderwriting.unlimited(), debit);
    }

    /**
     * {@link #trySpin} against an UNLIMITED dealer, which is how every dealer
     * behaves until an administrator opts one into a budget. Exists so the
     * pre-Phase-2 behavior stays directly expressible and directly testable.
     */
    public SpinAttempt trySpin(
        long denomUnits,
        int columns,
        int activeLines,
        boolean itemMode,
        SlotsPaytable paytable,
        SlotsRandomSource rng,
        SlotsUnderwriting underwriting,
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

        // The dealer commits before the player does, and before any random
        // result exists. Ordered this way round because unwinding the dealer
        // side is a clean bookkeeping reversal, whereas refunding a player who
        // has already been debited is not.
        Commitment accepted = underwriting.underwrite(totalBetUnits, maxPossiblePayout);
        if (accepted == null || !accepted.isAccepted()) {
            return new SpinAttempt.Rejected(
                RejectReason.DEALER_CANNOT_COVER,
                accepted == null ? null : accepted.decision());
        }

        if (state == SlotsSessionState.RESOLVED) {
            state = SlotsStateMachine.transition(state, SlotsSessionState.IDLE);
        }

        if (!debit.test(totalBetUnits)) {
            // The player could not pay. Hand the stake back off the dealer's
            // books and release the promise, or the dealer would be left
            // holding money it never actually received.
            underwriting.cancel(accepted, totalBetUnits);
            return new SpinAttempt.Rejected(RejectReason.INSUFFICIENT_FUNDS);
        }

        commitment = accepted;
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
    public SlotsSettlementResult settle(PayoutDelivery liveDeliver, LongPredicate durableQueue) {
        return settle(liveDeliver, durableQueue, SlotsUnderwriting.unlimited());
    }

    /** {@link #settle} with the dealer-budget side made explicit. */
    public SlotsSettlementResult settle(
        PayoutDelivery liveDeliver, LongPredicate durableQueue, SlotsUnderwriting underwriting) {

        state = SlotsStateMachine.transition(state, SlotsSessionState.SETTLING);
        // Captured before resolution: the win the player actually scored is
        // what gets displayed, not whatever remains undelivered afterwards.
        long committed = pendingPayoutAmount;

        // The dealer's books close here, on the awarded amount, before any
        // delivery is attempted. Whether the win reaches the inventory, the
        // overflow bank, or a pending record is a delivery question; the money
        // has left the dealer either way, and exactly once.
        settleBudget(underwriting, committed);

        SlotsSettlementResult result = resolvePayout(liveDeliver, durableQueue);
        lastWinAmount = committed;
        applySettlementResult(result);
        return result;
    }

    /**
     * Closes the dealer's books for the committed round and drops the promise.
     *
     * <p>Clearing {@link #commitment} is what guarantees the budget is touched
     * once: every later path -- a settlement retry, a termination, a duplicated
     * callback -- finds nothing to settle. The store's idempotent reservation
     * id is the second line of defence, not the first.
     */
    private void settleBudget(SlotsUnderwriting underwriting, long payout) {
        if (commitment == null) {
            return;
        }
        Commitment closing = commitment;
        commitment = null;
        underwriting.settle(closing, payout);
    }

    /**
     * Closes the dealer's books for a round being abandoned at shutdown or
     * disconnect, where the payout is preserved rather than replayed. Safe to
     * call when nothing is open.
     */
    public void settleBudgetOnTermination(SlotsUnderwriting underwriting) {
        settleBudget(underwriting, pendingPayoutAmount);
    }

    /**
     * Re-attempts a previously {@link SlotsSettlementResult#FAILED}
     * settlement without re-deriving or re-crediting anything already
     * accounted for -- the retained {@link #pendingPayoutAmount()} is the
     * single source of truth for what is still owed, so a retry can never
     * pay twice. It has no dealer-budget effect either: the dealer was
     * debited when the result was awarded, not when it was delivered.
     */
    public SlotsSettlementResult retrySettlement(PayoutDelivery liveDeliver, LongPredicate durableQueue) {
        if (state != SlotsSessionState.SETTLEMENT_FAILED) {
            throw new IllegalStateException("retrySettlement called outside SETTLEMENT_FAILED: " + state);
        }
        // Deliberately no budget call: the dealer was debited when the payout
        // was awarded. This is a delivery retry, and delivery is not an
        // economic event.
        SlotsSettlementResult result = resolvePayout(liveDeliver, durableQueue);
        applySettlementResult(result);
        return result;
    }

    private SlotsSettlementResult resolvePayout(PayoutDelivery liveDeliver, LongPredicate durableQueue) {
        long payout = pendingPayoutAmount;
        if (payout <= 0) {
            return SlotsSettlementResult.DELIVERED;
        }

        long remaining = liveDeliver.deliver(payout);
        if (remaining <= 0) {
            return SlotsSettlementResult.DELIVERED;
        }

        // Retain only what is genuinely still outstanding. If delivery placed
        // part of the win in the inventory and banked part of it, the queued
        // or retried obligation must cover the remainder alone -- never the
        // original total, which would pay the delivered portion twice.
        pendingPayoutAmount = Math.min(payout, remaining);

        if (durableQueue.test(pendingPayoutAmount)) {
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
