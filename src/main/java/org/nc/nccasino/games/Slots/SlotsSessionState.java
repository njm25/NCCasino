package org.nc.nccasino.games.Slots;

/**
 * The explicit per-player spin lifecycle. See {@link SlotsStateMachine} for
 * the centralized, testable set of legal transitions between these states.
 */
public enum SlotsSessionState {
    IDLE,
    DEBIT_ACCEPTED,
    RESULT_COMMITTED,
    ANIMATING,
    SETTLING,
    RESOLVED,
    /**
     * A committed positive payout could neither be delivered live nor
     * durably queued. The amount is retained (never cleared) and no new
     * spin is permitted until a retry resolves it to {@link #RESOLVED}.
     */
    SETTLEMENT_FAILED,
    TERMINATED
}
