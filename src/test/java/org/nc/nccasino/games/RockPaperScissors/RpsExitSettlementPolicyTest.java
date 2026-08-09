package org.nc.nccasino.games.RockPaperScissors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nc.nccasino.games.RockPaperScissors.RpsExitSettlementPolicy.Action.CASH_OUT;
import static org.nc.nccasino.games.RockPaperScissors.RpsExitSettlementPolicy.Action.CONTINUE;
import static org.nc.nccasino.games.RockPaperScissors.RpsExitSettlementPolicy.Action.RESOLVE;
import static org.nc.nccasino.games.RockPaperScissors.RpsExitSettlementPolicy.Outcome.LOSS;
import static org.nc.nccasino.games.RockPaperScissors.RpsExitSettlementPolicy.Outcome.TIE;
import static org.nc.nccasino.games.RockPaperScissors.RpsExitSettlementPolicy.Outcome.WIN;

import org.junit.jupiter.api.Test;

class RpsExitSettlementPolicyTest {

    @Test
    void ownerExitMakesAnUncappedWinOrTieCashOut() {
        assertEquals(CASH_OUT, RpsExitSettlementPolicy.afterReveal(WIN, true, false));
        assertEquals(CASH_OUT, RpsExitSettlementPolicy.afterReveal(TIE, true, false));
    }

    @Test
    void ownerExitStillResolvesLossAndAutomaticCappedWin() {
        assertEquals(RESOLVE, RpsExitSettlementPolicy.afterReveal(LOSS, true, false));
        assertEquals(RESOLVE, RpsExitSettlementPolicy.afterReveal(WIN, true, true));
    }

    @Test
    void attachedOwnerContinuesUncappedChainsAndTieRethrows() {
        assertEquals(CONTINUE, RpsExitSettlementPolicy.afterReveal(WIN, false, false));
        assertEquals(CONTINUE, RpsExitSettlementPolicy.afterReveal(TIE, false, false));
        assertEquals(RESOLVE, RpsExitSettlementPolicy.afterReveal(WIN, false, true));
    }
}
