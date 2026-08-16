package org.nc.nccasino.session;

import static org.nc.nccasino.session.TerminationAction.CASH_OUT;
import static org.nc.nccasino.session.TerminationAction.FORFEIT;
import static org.nc.nccasino.session.TerminationAction.NO_ACTION;
import static org.nc.nccasino.session.TerminationAction.QUEUE_KNOWN_PAYOUT;
import static org.nc.nccasino.session.TerminationAction.REFUND;
import static org.nc.nccasino.session.TerminationAction.RIDE_TO_RESULT;

/**
 * Pure, deterministic disconnect policy for each game.
 *
 * <p>The game objects remain responsible for translating their live state
 * into these small phase inputs and carrying out the returned action. This
 * class deliberately knows nothing about Bukkit, inventories, or timers.
 */
public final class GameTerminationPolicy {

    public enum MinesPhase {
        PREGAME,
        PLAYING,
        GAME_OVER_DEPOSIT_PENDING,
        RESOLVED
    }

    /**
     * Slots resolves a spin's debit, outcome, and payout amount entirely
     * synchronously before any animation ever runs, so there is no
     * meaningful window between "debit accepted" and "result committed" --
     * both collapse into {@link #RESULT_COMMITTED}, which also covers the
     * purely cosmetic animating/settling window since the payout is already
     * fixed by then.
     */
    public enum SlotsPhase {
        PREGAME,
        RESULT_COMMITTED,
        RESOLVED
    }

    private GameTerminationPolicy() {
    }

    /**
     * @param hasCommittedWager whether this player currently has real money
     *     already at stake for the round in progress -- a pregame/countdown
     *     or start-transition committed wager, or (once {@code gameActive})
     *     always true by construction. Distinguishes "money is already on
     *     the line, ride it out" from "nothing committed yet, just free the
     *     seat" during the two phases before {@code gameActive} -- without
     *     this, a merely-seated spectator who closes their GUI without ever
     *     betting would stay seated forever instead of freeing the seat.
     */
    public static TerminationAction blackjack(ExitReason reason, boolean gameActive, boolean hasCommittedWager) {
        if (reason == ExitReason.GAME_COMPLETED) {
            return NO_ACTION;
        }
        if (reason == ExitReason.KICKED) {
            return FORFEIT;
        }
        if (reason == ExitReason.PLUGIN_DISABLE) {
            return REFUND;
        }
        if (gameActive || hasCommittedWager) {
            return RIDE_TO_RESULT;
        }
        return REFUND;
    }

    public static TerminationAction mines(ExitReason reason, MinesPhase phase) {
        if (reason == ExitReason.GAME_COMPLETED) {
            return NO_ACTION;
        }
        if (reason == ExitReason.KICKED) {
            return FORFEIT;
        }
        return switch (phase) {
            case PREGAME -> REFUND;
            case PLAYING -> CASH_OUT;
            case GAME_OVER_DEPOSIT_PENDING -> CASH_OUT;
            case RESOLVED -> NO_ACTION;
        };
    }

    public static TerminationAction dragon(
        ExitReason reason,
        boolean bettingEnabled,
        boolean playerLost
    ) {
        if (reason == ExitReason.GAME_COMPLETED) {
            return NO_ACTION;
        }
        if (reason == ExitReason.KICKED) {
            return FORFEIT;
        }
        if (bettingEnabled) {
            return REFUND;
        }
        return playerLost ? NO_ACTION : CASH_OUT;
    }

    public static TerminationAction roulette(ExitReason reason, boolean knownPayoutPending) {
        if (reason == ExitReason.GAME_COMPLETED) {
            return NO_ACTION;
        }
        if (reason == ExitReason.KICKED) {
            return FORFEIT;
        }
        if (reason == ExitReason.PLUGIN_DISABLE || knownPayoutPending) {
            return knownPayoutPending ? QUEUE_KNOWN_PAYOUT : REFUND;
        }
        return RIDE_TO_RESULT;
    }

    public static TerminationAction baccarat(ExitReason reason, boolean waitingForRound) {
        if (reason == ExitReason.GAME_COMPLETED) {
            return NO_ACTION;
        }
        if (reason == ExitReason.KICKED) {
            return FORFEIT;
        }
        if (waitingForRound || reason == ExitReason.PLUGIN_DISABLE) {
            return REFUND;
        }
        return RIDE_TO_RESULT;
    }

    /**
     * Covers the whole accepted-bet span. The game performs the final
     * phase-aware settlement: PvP rides through to a single decisive flip,
     * while PvE marks the current flip terminal so a chain win cashes out
     * instead of reopening an untimed match without its owner.
     */
    public static TerminationAction coinFlip(ExitReason reason, boolean gameActive) {
        if (reason == ExitReason.GAME_COMPLETED) {
            return NO_ACTION;
        }
        if (reason == ExitReason.KICKED) {
            return FORFEIT;
        }
        if (!gameActive || reason == ExitReason.PLUGIN_DISABLE) {
            return REFUND;
        }
        return RIDE_TO_RESULT;
    }

    /**
     * Covers the whole accepted-bet span. The game performs the final
     * phase-aware settlement: PvP rides through tie rethrows, while PvE
     * marks the current reveal terminal so a tie or chain win cashes out
     * instead of reopening an untimed match without its owner.
     */
    /**
     * A committed spin's outcome and payout are immutable and already known
     * -- unlike coinFlip/rockPaperScissors there is nothing left to "ride
     * to a result", so a disconnect or shutdown after commitment always
     * durably queues the exact known payout (zero included, as a valid
     * completed-loss record) rather than refunding or replaying anything.
     */
    public static TerminationAction slots(ExitReason reason, SlotsPhase phase) {
        if (reason == ExitReason.GAME_COMPLETED) {
            return NO_ACTION;
        }
        if (reason == ExitReason.KICKED) {
            return FORFEIT;
        }
        return switch (phase) {
            case PREGAME -> NO_ACTION;
            case RESULT_COMMITTED -> QUEUE_KNOWN_PAYOUT;
            case RESOLVED -> NO_ACTION;
        };
    }

    public static TerminationAction rockPaperScissors(ExitReason reason, boolean gameActive) {
        if (reason == ExitReason.GAME_COMPLETED) {
            return NO_ACTION;
        }
        if (reason == ExitReason.KICKED) {
            return FORFEIT;
        }
        if (!gameActive || reason == ExitReason.PLUGIN_DISABLE) {
            return REFUND;
        }
        return RIDE_TO_RESULT;
    }
}
