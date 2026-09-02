package org.nc.nccasino.commands;

import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.payout.OverflowBankService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandTabCompleterTest {

    private final UUID playerId = UUID.randomUUID();
    private Nccasino plugin;
    private OverflowBankService overflowBank;
    private Player player;
    private CommandTabCompleter completer;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        overflowBank = mock(OverflowBankService.class);
        player = mock(Player.class);
        completer = new CommandTabCompleter(plugin);

        when(plugin.getOverflowBankService()).thenReturn(overflowBank);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.hasPermission("nccasino.commands.create")).thenReturn(true);
        when(player.hasPermission("nccasino.commands.claim")).thenReturn(true);
    }

    @Test
    void hidesClaimWhenPlayerHasNothingBanked() {
        when(overflowBank.isBlocked(playerId)).thenReturn(false);

        List<String> completions = completeFirstArgument();

        assertTrue(completions.contains("create"));
        assertFalse(completions.contains("claim"));
    }

    @Test
    void showsClaimWhenPlayerHasBankedWinnings() {
        when(overflowBank.isBlocked(playerId)).thenReturn(true);

        List<String> completions = completeFirstArgument();

        assertTrue(completions.contains("create"));
        assertTrue(completions.contains("claim"));
    }

    @Test
    void hidesClaimWithoutPermissionEvenWhenPlayerHasBankedWinnings() {
        when(overflowBank.isBlocked(playerId)).thenReturn(true);
        when(player.hasPermission("nccasino.commands.claim")).thenReturn(false);

        assertFalse(completeFirstArgument().contains("claim"));
    }

    @Test
    void hidesClaimWhenOverflowBankIsUnavailable() {
        when(plugin.getOverflowBankService()).thenReturn(null);

        assertFalse(completeFirstArgument().contains("claim"));
    }

    private List<String> completeFirstArgument() {
        return completer.onTabComplete(player, mock(Command.class), "ncc", new String[]{""});
    }
}
