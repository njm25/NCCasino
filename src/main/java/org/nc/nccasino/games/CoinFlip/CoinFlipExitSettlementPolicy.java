package org.nc.nccasino.games.CoinFlip;

/** Pure decision table for the terminal flip after a PvE owner exits. */
final class CoinFlipExitSettlementPolicy {

    enum Outcome {
        WIN,
        LOSS
    }

    enum Action {
        CASH_OUT,
        CONTINUE,
        RESOLVE
    }

    private CoinFlipExitSettlementPolicy() {
    }

    static Action afterReveal(Outcome outcome, boolean exitPending, boolean cappedWin) {
        if (outcome == Outcome.LOSS || (outcome == Outcome.WIN && cappedWin)) {
            return Action.RESOLVE;
        }
        return exitPending ? Action.CASH_OUT : Action.CONTINUE;
    }
}
