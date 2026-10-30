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
    void refundsDuringACurrencyChangeNameTheChangeAndTheOldCurrency() {
        String[] stored = new String[1];
        PayoutMessages.duringCurrencyChange("Emerald",
            () -> stored[0] = PayoutMessages.serverRestartRefundContext("Coin Flip"));

        PayoutMessages.StoredContext context = PayoutMessages.decodeContext(stored[0]);
        assertEquals("payout.context-currency-change", context.localizationKey());
        assertEquals("Coin Flip", context.gameType());
        assertEquals("Emerald", context.currency());
        assertEquals("payout.context-server-restart", PayoutMessages.decodeContext(
            PayoutMessages.serverRestartRefundContext("Coin Flip")).localizationKey());
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
