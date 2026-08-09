package org.nc.nccasino.games.RockPaperScissors;

/** Pure decision table for the terminal reveal after a PvE owner exits. */
final class RpsExitSettlementPolicy {

    enum Outcome {
        WIN,
        TIE,
        LOSS
    }

    enum Action {
        CASH_OUT,
        CONTINUE,
        RESOLVE
    }

    private RpsExitSettlementPolicy() {
    }

    static Action afterReveal(Outcome outcome, boolean exitPending, boolean cappedWin) {
        if (outcome == Outcome.LOSS || (outcome == Outcome.WIN && cappedWin)) {
            return Action.RESOLVE;
        }
        return exitPending ? Action.CASH_OUT : Action.CONTINUE;
    }
}
