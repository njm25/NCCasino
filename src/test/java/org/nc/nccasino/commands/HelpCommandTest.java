package org.nc.nccasino.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.localization.LocalizationService;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every player may use /ncc help (the bare /ncc reply points them to it), so it
 * lists only the commands the asking player can actually use.
 */
class HelpCommandTest {

    private static List<String> helpFor(Set<String> granted) {
        Nccasino plugin = mock(Nccasino.class);
        LocalizationService localization = mock(LocalizationService.class);
        when(plugin.getLocalization()).thenReturn(localization);
        when(localization.text(any(CommandSender.class), anyString()))
            .thenAnswer(call -> call.getArgument(1));
        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenAnswer(call -> granted.contains(call.<String>getArgument(0)));

        new HelpCommand(plugin).execute(player, new String[] {"help"});

        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(lines.capture());
        return lines.getAllValues();
    }

    @Test
    void anOrdinaryPlayerSeesOnlyClaim() {
        assertEquals(List.of("commands.help-title", "commands.help-claim"),
            helpFor(Set.of("nccasino.commands.help", "nccasino.commands.claim")));
    }

    @Test
    void anAdminSeesEveryCommand() {
        assertEquals(List.of("commands.help-title", "commands.help-create", "commands.help-list",
                "commands.help-delete", "commands.help-reload", "commands.help-claim"),
            helpFor(Set.of("nccasino.commands.help", "nccasino.commands.create", "nccasino.commands.list",
                "nccasino.commands.delete", "nccasino.commands.reload", "nccasino.commands.claim")));
    }
}
