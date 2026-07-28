package org.nc.nccasino.session;

/**
 * Money-bearing action a game must take when an active session terminates.
 * Keeping these outcomes explicit makes the policy independently testable
 * from Bukkit inventories, animations, and schedulers.
 */
public enum TerminationAction {
    NO_ACTION,
    FORFEIT,
    REFUND,
    CASH_OUT,
    RIDE_TO_RESULT,
    QUEUE_KNOWN_PAYOUT
}
