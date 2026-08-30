package org.nc.nccasino.payout;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.localization.LocalizationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The universal wagering block: any banked balance, in any currency, stops
 * every game.
 *
 * <p>The behavioral half of this runs the real gate against a real store. The
 * wiring half checks that each game's currency-debit chokepoint actually calls
 * the gate -- a full in-server integration test would need a live Bukkit
 * inventory to construct the game classes, but the property that matters (no
 * betting path can take money without asking first) is exactly what the source
 * guard pins down, and it fails loudly if a new path is added or a guard is
 * removed.
 */
class WagerGateTest {

    private static final BankedCurrency DIAMONDS =
        new BankedCurrency(CurrencyMode.STANDARD, "DIAMOND", "High Roller Chip");

    @TempDir
    Path tempDir;

    private Nccasino plugin;
    private OverflowBankStore store;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        FileConfiguration config = new YamlConfiguration();
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("WagerGateTest"));
        when(plugin.getConfig()).thenReturn(config);
        config.set(OverflowSettings.PATH_MODE, "BANK");
        config.set(OverflowSettings.PATH_CLEAR_BEFORE_WAGER, true);

        Preferences preferences = mock(Preferences.class);
        when(preferences.getOverflowPreference()).thenReturn(null);
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);

        LocalizationService localization = mock(LocalizationService.class);
        when(localization.text(any(Player.class), anyString(), any(Object[].class))).thenReturn("blocked");
        when(plugin.getLocalization()).thenReturn(localization);

        store = new OverflowBankStore(plugin);
        when(plugin.getOverflowBankStore()).thenReturn(store);
        when(plugin.getOverflowBankService()).thenReturn(new OverflowBankService(plugin, store));
    }

    @Test
    void aBankedDiamondBalanceBlocksAnEmeraldWager() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        // No room anywhere, so the automatic claim cannot clear the balance.
        fixture.fillCompletely();
        assertTrue(store.credit(fixture.playerId, DIAMONDS, 900L));
        WagerGate.clearPlayerState(fixture.playerId);

        // The dealer being played pays emeralds; the bank holds diamonds.
        boolean allowed = WagerGate.allowsWager(plugin, fixture.player);

        assertFalse(allowed, "a banked balance in ANY currency must block the wager");
        verify(fixture.player).sendMessage(anyString());
        assertEquals(900L, store.balanceOf(fixture.playerId, DIAMONDS),
            "a blocked wager must not consume the banked balance");
    }

    @Test
    void theGateAutomaticallyClearsTheBankAndLetsPlayContinue() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.leaveFreeSlots(36);
        store.credit(fixture.playerId, DIAMONDS, 500L);
        WagerGate.clearPlayerState(fixture.playerId);

        assertTrue(WagerGate.allowsWager(plugin, fixture.player),
            "the pre-wager attempt should deliver the balance and allow play");
        assertFalse(store.hasAnyBalance(fixture.playerId));
        verify(fixture.player, never()).sendMessage(anyString());
    }

    @Test
    void anEmptyBankNeverBlocksAnyone() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        WagerGate.clearPlayerState(fixture.playerId);

        assertTrue(WagerGate.allowsWager(plugin, fixture.player));
        verify(fixture.player, never()).sendMessage(anyString());
    }

    @Test
    void repeatedBlockedAttemptsDoNotSpamTheSameNotice() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        fixture.fillCompletely();
        store.credit(fixture.playerId, DIAMONDS, 900L);
        WagerGate.clearPlayerState(fixture.playerId);

        for (int i = 0; i < 5; i++) {
            assertFalse(WagerGate.allowsWager(plugin, fixture.player));
        }

        // Every attempt is refused, but a burst of debits (a Roulette
        // portfolio, a Blackjack split) yields one readable refusal.
        verify(fixture.player).sendMessage(anyString());
    }

    @Test
    void aMissingBankServiceNeverBlocksPlay() {
        OverflowTestSupport.Fixture fixture = OverflowTestSupport.fixture();
        when(plugin.getOverflowBankService()).thenReturn(null);
        WagerGate.clearPlayerState(fixture.playerId);

        assertTrue(WagerGate.allowsWager(plugin, fixture.player));
    }

    // ---- wiring: every game's debit chokepoint consults the gate ----------

    /**
     * Each entry is the file and the exact debit method that must not be able
     * to take a player's currency without asking {@link WagerGate} first.
     */
    private static List<String[]> gatedChokepoints() {
        return List.of(
            new String[] {"entities/Client.java", "tryRemoveCurrencyFromInventory"},
            new String[] {"games/Mines/MinesTable.java", "removeWagerFromInventory"},
            new String[] {"games/Roulette/BettingTable.java", "removeWagerFromInventory"},
            new String[] {"games/Blackjack/BlackjackInventory.java", "tryRemoveWager"},
            new String[] {"games/Slots/SlotsMachine.java", "attemptDebit"});
    }

    @Test
    void everyGameCurrencyDebitChokepointCallsTheWagerGate() throws IOException {
        for (String[] entry : gatedChokepoints()) {
            String source = readSource(entry[0]);
            int methodStart = source.indexOf(entry[1] + "(Player");
            if (methodStart < 0) {
                methodStart = source.indexOf(entry[1] + "(long");
            }
            assertTrue(methodStart >= 0,
                entry[0] + " no longer declares " + entry[1] + "; the wager gate wiring must be revisited");

            // The guard must be the first thing of substance in the method, so
            // it runs before any withdrawal or random outcome.
            String body = source.substring(methodStart, Math.min(source.length(), methodStart + 900));
            assertTrue(body.contains("WagerGate.allowsWager"),
                entry[0] + "#" + entry[1] + " must call WagerGate.allowsWager before debiting currency");
        }
    }

    @Test
    void chokepointCoverageSpansEveryNonSlotsGameFamily() throws IOException {
        // Client covers Baccarat, Coin Flip, Rock Paper Scissors and Dragon
        // Descent, which all debit through it rather than owning a path.
        String client = readSource("entities/Client.java");
        assertTrue(client.contains("tryRemoveWagerFromInventory"),
            "the Client-family games must still funnel wagers through the gated helper");
        assertTrue(client.indexOf("WagerGate.allowsWager")
                < client.indexOf("provider.withdraw(player, internalName, amount)"),
            "the gate must be consulted before the actual withdrawal");
    }

    private static String readSource(String relativePath) throws IOException {
        Path path = Paths.get("src/main/java/org/nc/nccasino").resolve(relativePath);
        assertTrue(Files.isRegularFile(path), "expected source file at " + path.toAbsolutePath());
        return Files.readString(path);
    }
}
