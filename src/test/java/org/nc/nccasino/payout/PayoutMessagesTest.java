package org.nc.nccasino.payout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.session.ExitReason;

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
    void decidedResultsPaidDuringACurrencyChangeNameTheChangeAndTheOldCurrency() {
        String[] stored = new String[1];
        PayoutMessages.duringCurrencyChange("Vault",
            () -> stored[0] = PayoutMessages.committedResultContext("Roulette"));

        PayoutMessages.StoredContext context = PayoutMessages.decodeContext(stored[0]);
        assertEquals("payout.context-currency-change-committed", context.localizationKey());
        assertEquals("Roulette", context.gameType());
        assertEquals("Vault", context.currency());
        PayoutMessages.StoredContext restart = PayoutMessages.decodeContext(
            PayoutMessages.committedResultContext("Roulette"));
        assertEquals("payout.context-committed-result", restart.localizationKey(),
            "real restarts and reloads keep the restart message");
        assertNull(restart.currency());
    }

    @Test
    void aGameEndedMidRoundNamesWhyItEnded() {
        assertEquals("payout.context-left-mid-game", PayoutMessages.decodeContext(
            PayoutMessages.endedMidGameContext(ExitReason.VOLUNTARY_INVENTORY_CLOSE, "Slots")).localizationKey(),
            "closing the window while online is not a disconnect");
        assertEquals("payout.context-disconnected", PayoutMessages.decodeContext(
            PayoutMessages.endedMidGameContext(ExitReason.DISCONNECTED, "Slots")).localizationKey());
        assertEquals("payout.context-committed-result", PayoutMessages.decodeContext(
            PayoutMessages.endedMidGameContext(ExitReason.PLUGIN_DISABLE, "Mines")).localizationKey(),
            "a restart or reload is not a disconnect");

        String[] stored = new String[1];
        PayoutMessages.duringCurrencyChange("Vault",
            () -> stored[0] = PayoutMessages.endedMidGameContext(ExitReason.PLUGIN_DISABLE, "Dragon Descent"));
        PayoutMessages.StoredContext context = PayoutMessages.decodeContext(stored[0]);
        assertEquals("payout.context-currency-change-committed", context.localizationKey(),
            "a currency change mid-round names the change, not a disconnect");
        assertEquals("Dragon Descent", context.gameType());
        assertEquals("Vault", context.currency());
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
