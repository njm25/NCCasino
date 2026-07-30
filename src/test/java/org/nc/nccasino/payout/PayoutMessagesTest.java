package org.nc.nccasino.payout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PayoutMessagesTest {

    @Test
    void newDisconnectContextsStoreReasonAndGameInsteadOfEnglishSentence() {
        PayoutMessages.StoredContext context = PayoutMessages.decodeContext(
            PayoutMessages.disconnectedMidGameContext("Roulette")
        );

        assertEquals("payout.context-disconnected", context.localizationKey());
        assertEquals("Roulette", context.gameType());
    }

    @Test
    void newRestartContextsStoreReasonAndGameInsteadOfEnglishSentence() {
        PayoutMessages.StoredContext context = PayoutMessages.decodeContext(
            PayoutMessages.serverRestartRefundContext("Mines")
        );

        assertEquals("payout.context-server-restart", context.localizationKey());
        assertEquals("Mines", context.gameType());
    }

    @Test
    void legacyEnglishContextsRemainBackwardCompatible() {
        assertNull(PayoutMessages.decodeContext(
            "You disconnected during an active Roulette game."
        ));
    }

    @Test
    void gameTypesResolveToExistingLocalizedGameNameKeys() {
        assertEquals("game-options.coin-flip", PayoutMessages.gameLocalizationKey("Coin Flip"));
        assertNull(PayoutMessages.gameLocalizationKey("Custom Game"));
    }
}
