package org.nc.nccasino.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nc.nccasino.session.GameTerminationPolicy.MinesPhase.GAME_OVER_DEPOSIT_PENDING;
import static org.nc.nccasino.session.GameTerminationPolicy.MinesPhase.PLAYING;
import static org.nc.nccasino.session.GameTerminationPolicy.MinesPhase.PREGAME;
import static org.nc.nccasino.session.GameTerminationPolicy.MinesPhase.RESOLVED;
import static org.nc.nccasino.session.TerminationAction.CASH_OUT;
import static org.nc.nccasino.session.TerminationAction.FORFEIT;
import static org.nc.nccasino.session.TerminationAction.NO_ACTION;
import static org.nc.nccasino.session.TerminationAction.QUEUE_KNOWN_PAYOUT;
import static org.nc.nccasino.session.TerminationAction.REFUND;
import static org.nc.nccasino.session.TerminationAction.RIDE_TO_RESULT;

class GameTerminationPolicyTest {

    @Test
    void kicksAlwaysForfeitAcrossEveryGameAndPhase() {
        assertEquals(FORFEIT, GameTerminationPolicy.blackjack(ExitReason.KICKED, false, false));
        assertEquals(FORFEIT, GameTerminationPolicy.blackjack(ExitReason.KICKED, false, true));
        assertEquals(FORFEIT, GameTerminationPolicy.blackjack(ExitReason.KICKED, true, true));
        assertEquals(FORFEIT, GameTerminationPolicy.mines(ExitReason.KICKED, PREGAME));
        assertEquals(FORFEIT, GameTerminationPolicy.mines(ExitReason.KICKED, PLAYING));
        assertEquals(FORFEIT, GameTerminationPolicy.dragon(ExitReason.KICKED, true, false));
        assertEquals(FORFEIT, GameTerminationPolicy.dragon(ExitReason.KICKED, false, false));
        assertEquals(FORFEIT, GameTerminationPolicy.roulette(ExitReason.KICKED, false));
        assertEquals(FORFEIT, GameTerminationPolicy.roulette(ExitReason.KICKED, true));
        assertEquals(FORFEIT, GameTerminationPolicy.baccarat(ExitReason.KICKED, true));
        assertEquals(FORFEIT, GameTerminationPolicy.baccarat(ExitReason.KICKED, false));
        assertEquals(FORFEIT, GameTerminationPolicy.coinFlip(ExitReason.KICKED, false));
        assertEquals(FORFEIT, GameTerminationPolicy.coinFlip(ExitReason.KICKED, true));
        assertEquals(FORFEIT, GameTerminationPolicy.rockPaperScissors(ExitReason.KICKED, false));
        assertEquals(FORFEIT, GameTerminationPolicy.rockPaperScissors(ExitReason.KICKED, true));
        for (GameTerminationPolicy.SlotsPhase phase : GameTerminationPolicy.SlotsPhase.values()) {
            assertEquals(FORFEIT, GameTerminationPolicy.slots(ExitReason.KICKED, phase));
        }
    }

    @Test
    void blackjackRefundsOnlyAnUncommittedSpectatorAndRidesToResultOnceAnythingIsAtStake() {
        for (ExitReason reason : new ExitReason[] {
            ExitReason.DISCONNECTED,
            ExitReason.VOLUNTARY_INVENTORY_CLOSE
        }) {
            // Merely seated, nothing committed yet -- free the seat.
            assertEquals(REFUND, GameTerminationPolicy.blackjack(reason, false, false));
            // A wager is already committed for the pregame/countdown or
            // start-transition round about to deal -- ride it out, exactly
            // like an online player's committed wager would.
            assertEquals(RIDE_TO_RESULT, GameTerminationPolicy.blackjack(reason, false, true));
            // Deal has begun -- always rides, regardless of the wager flag.
            assertEquals(RIDE_TO_RESULT, GameTerminationPolicy.blackjack(reason, true, false));
            assertEquals(RIDE_TO_RESULT, GameTerminationPolicy.blackjack(reason, true, true));
        }
        // PLUGIN_DISABLE can never ride through a restart -- always refunds, regardless of phase/wager.
        assertEquals(REFUND,
            GameTerminationPolicy.blackjack(ExitReason.PLUGIN_DISABLE, false, false));
        assertEquals(REFUND,
            GameTerminationPolicy.blackjack(ExitReason.PLUGIN_DISABLE, false, true));
        assertEquals(REFUND,
            GameTerminationPolicy.blackjack(ExitReason.PLUGIN_DISABLE, true, true));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.blackjack(ExitReason.GAME_COMPLETED, false, false));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.blackjack(ExitReason.GAME_COMPLETED, true, true));
    }

    @Test
    void minesRefundsPregameAndCashesOutActivePosition() {
        for (ExitReason reason : nonKickReasons()) {
            assertEquals(REFUND, GameTerminationPolicy.mines(reason, PREGAME));
            assertEquals(CASH_OUT, GameTerminationPolicy.mines(reason, PLAYING));
            assertEquals(NO_ACTION, GameTerminationPolicy.mines(reason, RESOLVED));
        }
        assertEquals(CASH_OUT,
            GameTerminationPolicy.mines(ExitReason.PLUGIN_DISABLE, GAME_OVER_DEPOSIT_PENDING));
        assertEquals(CASH_OUT,
            GameTerminationPolicy.mines(ExitReason.DISCONNECTED, GAME_OVER_DEPOSIT_PENDING));
        for (GameTerminationPolicy.MinesPhase phase : GameTerminationPolicy.MinesPhase.values()) {
            assertEquals(NO_ACTION,
                GameTerminationPolicy.mines(ExitReason.GAME_COMPLETED, phase));
        }
    }

    @Test
    void dragonRefundsBeforeStartCashesOutLiveRunAndDoesNothingAfterResolution() {
        for (ExitReason reason : nonKickReasons()) {
            assertEquals(REFUND, GameTerminationPolicy.dragon(reason, true, false));
            assertEquals(CASH_OUT, GameTerminationPolicy.dragon(reason, false, false));
            assertEquals(NO_ACTION, GameTerminationPolicy.dragon(reason, false, true));
        }
        assertEquals(NO_ACTION,
            GameTerminationPolicy.dragon(ExitReason.GAME_COMPLETED, true, false));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.dragon(ExitReason.GAME_COMPLETED, false, false));
    }

    @Test
    void rouletteRidesOrdinaryDisconnectButRefundsShutdown() {
        assertEquals(RIDE_TO_RESULT,
            GameTerminationPolicy.roulette(ExitReason.DISCONNECTED, false));
        assertEquals(RIDE_TO_RESULT,
            GameTerminationPolicy.roulette(ExitReason.VOLUNTARY_INVENTORY_CLOSE, false));
        assertEquals(REFUND,
            GameTerminationPolicy.roulette(ExitReason.PLUGIN_DISABLE, false));
        assertEquals(QUEUE_KNOWN_PAYOUT,
            GameTerminationPolicy.roulette(ExitReason.PLUGIN_DISABLE, true));
        assertEquals(QUEUE_KNOWN_PAYOUT,
            GameTerminationPolicy.roulette(ExitReason.DISCONNECTED, true));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.roulette(ExitReason.GAME_COMPLETED, false));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.roulette(ExitReason.GAME_COMPLETED, true));
    }

    @Test
    void baccaratRefundsPregameRidesActiveHandAndRefundsShutdown() {
        assertEquals(REFUND, GameTerminationPolicy.baccarat(ExitReason.DISCONNECTED, true));
        assertEquals(RIDE_TO_RESULT,
            GameTerminationPolicy.baccarat(ExitReason.DISCONNECTED, false));
        assertEquals(REFUND,
            GameTerminationPolicy.baccarat(ExitReason.PLUGIN_DISABLE, false));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.baccarat(ExitReason.GAME_COMPLETED, true));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.baccarat(ExitReason.GAME_COMPLETED, false));
    }

    @Test
    void coinFlipRefundsPregameRidesAcceptedFlipAndRefundsShutdown() {
        assertEquals(REFUND, GameTerminationPolicy.coinFlip(ExitReason.DISCONNECTED, false));
        assertEquals(RIDE_TO_RESULT,
            GameTerminationPolicy.coinFlip(ExitReason.DISCONNECTED, true));
        assertEquals(REFUND,
            GameTerminationPolicy.coinFlip(ExitReason.PLUGIN_DISABLE, true));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.coinFlip(ExitReason.GAME_COMPLETED, false));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.coinFlip(ExitReason.GAME_COMPLETED, true));
    }

    @Test
    void rockPaperScissorsRefundsPregameRidesAcceptedRoundIncludingTiesAndRefundsShutdown() {
        assertEquals(REFUND, GameTerminationPolicy.rockPaperScissors(ExitReason.DISCONNECTED, false));
        assertEquals(RIDE_TO_RESULT,
            GameTerminationPolicy.rockPaperScissors(ExitReason.DISCONNECTED, true));
        assertEquals(REFUND,
            GameTerminationPolicy.rockPaperScissors(ExitReason.PLUGIN_DISABLE, true));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.rockPaperScissors(ExitReason.GAME_COMPLETED, false));
        assertEquals(NO_ACTION,
            GameTerminationPolicy.rockPaperScissors(ExitReason.GAME_COMPLETED, true));
    }

    @Test
    void slotsNeverRefundsAndAlwaysQueuesTheKnownPayoutOnceCommitted() {
        for (ExitReason reason : nonKickReasons()) {
            assertEquals(NO_ACTION, GameTerminationPolicy.slots(reason, GameTerminationPolicy.SlotsPhase.PREGAME));
            assertEquals(QUEUE_KNOWN_PAYOUT,
                GameTerminationPolicy.slots(reason, GameTerminationPolicy.SlotsPhase.RESULT_COMMITTED));
            assertEquals(NO_ACTION, GameTerminationPolicy.slots(reason, GameTerminationPolicy.SlotsPhase.RESOLVED));
        }
        for (GameTerminationPolicy.SlotsPhase phase : GameTerminationPolicy.SlotsPhase.values()) {
            assertEquals(NO_ACTION, GameTerminationPolicy.slots(ExitReason.GAME_COMPLETED, phase));
        }
    }

    private static ExitReason[] nonKickReasons() {
        return new ExitReason[] {
            ExitReason.DISCONNECTED,
            ExitReason.VOLUNTARY_INVENTORY_CLOSE,
            ExitReason.PLUGIN_DISABLE
        };
    }
}
