package org.nc.nccasino.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.localization.LocalizationService;
import org.nc.nccasino.payout.OverflowPreference;
import org.nc.nccasino.payout.OverflowSettings;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The non-GUI player-choice command. Deliberately a command: putting this in
 * the player menu would change an existing inventory layout.
 */
class OverflowCommandTest {

    private Nccasino plugin;
    private FileConfiguration config;
    private Preferences preferences;
    private Player player;
    private OverflowCommand command;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        config = new YamlConfiguration();
        preferences = mock(Preferences.class);
        player = mock(Player.class);

        when(plugin.getConfig()).thenReturn(config);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(plugin.getPreferences(any(UUID.class))).thenReturn(preferences);

        LocalizationService localization = mock(LocalizationService.class);
        when(localization.text(any(Player.class), anyString(), any(Object[].class)))
            .thenAnswer(invocation -> invocation.getArgument(1));
        when(localization.text(any(CommandSender.class), anyString(), any(Object[].class)))
            .thenAnswer(invocation -> invocation.getArgument(1));
        when(plugin.getLocalization()).thenReturn(localization);

        command = new OverflowCommand(plugin);
    }

    @Test
    void choosingBankStoresThePreference() {
        assertTrue(command.execute(player, new String[] {"overflow", "bank"}));

        verify(preferences).setOverflowPreference(OverflowPreference.BANK);
        verify(player).sendMessage("payout.overflow-bank-selected");
    }

    @Test
    void choosingDropStoresThePreference() {
        assertTrue(command.execute(player, new String[] {"overflow", "drop"}));

        verify(preferences).setOverflowPreference(OverflowPreference.DROP);
        verify(player).sendMessage("payout.overflow-drop-selected");
    }

    @Test
    void argumentsAreCaseInsensitive() {
        assertTrue(command.execute(player, new String[] {"overflow", "  BaNk "}));

        verify(preferences).setOverflowPreference(OverflowPreference.BANK);
    }

    @Test
    void aMissingOrUnknownChoiceShowsUsageAndChangesNothing() {
        assertTrue(command.execute(player, new String[] {"overflow"}));
        assertTrue(command.execute(player, new String[] {"overflow", "sideways"}));

        verify(preferences, never()).setOverflowPreference(any());
        verify(player, org.mockito.Mockito.times(2)).sendMessage("payout.overflow-usage");
    }

    @Test
    void aForcedServerModeStillStoresTheChoiceAndSaysSo() {
        config.set(OverflowSettings.PATH_MODE, "BANK");

        assertTrue(command.execute(player, new String[] {"overflow", "drop"}));

        // Stored, not discarded -- returning the server to PLAYER_CHOICE must
        // restore exactly what the player picked.
        verify(preferences).setOverflowPreference(OverflowPreference.DROP);
        verify(player).sendMessage("payout.overflow-forced");
    }

    @Test
    void playerChoiceModeDoesNotShowTheForcedNotice() {
        config.set(OverflowSettings.PATH_MODE, "PLAYER_CHOICE");

        assertTrue(command.execute(player, new String[] {"overflow", "bank"}));

        verify(player, never()).sendMessage("payout.overflow-forced");
    }

    @Test
    void consoleIsToldTheCommandNeedsAPlayer() {
        CommandSender console = mock(CommandSender.class);

        assertTrue(command.execute(console, new String[] {"overflow", "bank"}));

        verify(console).sendMessage("commands.player-only");
        verify(preferences, never()).setOverflowPreference(any());
    }

    @Test
    void tabCompletionOffersExactlyTheAcceptedChoices() {
        assertEquals(List.of("bank", "drop"), List.of(OverflowCommand.choices()));
    }

    @Test
    void everyOfferedChoiceIsActuallyAccepted() {
        for (String choice : OverflowCommand.choices()) {
            assertEquals(choice.toUpperCase(java.util.Locale.ROOT),
                OverflowPreference.parse(choice, null).name(),
                "tab completion must not suggest a value the command rejects");
        }
    }

    @Test
    void aVaultOnlyServerStillAcceptsTheChoiceWithoutAnEconomyLookup() {
        // The preference is pure policy -- it must never depend on the
        // currency mode of any particular dealer.
        assertTrue(command.execute(player, new String[] {"overflow", "bank"}));
        verify(plugin, never()).getCurrencyManager();
        verify(plugin, never()).getOverflowBankService();
        verify(preferences).setOverflowPreference(eq(OverflowPreference.BANK));
    }
}
