package org.nc.nccasino.games.CoinFlip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nc.nccasino.games.CoinFlip.CoinFlipExitSettlementPolicy.Action.CASH_OUT;
import static org.nc.nccasino.games.CoinFlip.CoinFlipExitSettlementPolicy.Action.CONTINUE;
import static org.nc.nccasino.games.CoinFlip.CoinFlipExitSettlementPolicy.Action.RESOLVE;
import static org.nc.nccasino.games.CoinFlip.CoinFlipExitSettlementPolicy.Outcome.LOSS;
import static org.nc.nccasino.games.CoinFlip.CoinFlipExitSettlementPolicy.Outcome.WIN;

import org.junit.jupiter.api.Test;

class CoinFlipExitSettlementPolicyTest {

    @Test
    void ownerExitMakesAnUncappedWinCashOut() {
        assertEquals(CASH_OUT, CoinFlipExitSettlementPolicy.afterReveal(WIN, true, false));
    }

    @Test
    void ownerExitStillResolvesLossAndAutomaticCappedWin() {
        assertEquals(RESOLVE, CoinFlipExitSettlementPolicy.afterReveal(LOSS, true, false));
        assertEquals(RESOLVE, CoinFlipExitSettlementPolicy.afterReveal(WIN, true, true));
    }

    @Test
    void attachedOwnerContinuesUncappedChainsAndResolvesCappedWins() {
        assertEquals(CONTINUE, CoinFlipExitSettlementPolicy.afterReveal(WIN, false, false));
        assertEquals(RESOLVE, CoinFlipExitSettlementPolicy.afterReveal(WIN, false, true));
        assertEquals(RESOLVE, CoinFlipExitSettlementPolicy.afterReveal(LOSS, false, false));
    }
}
