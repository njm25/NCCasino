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
    TERMINATED
}
