package org.nc.nccasino.commands;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public interface CasinoCommand {
    boolean execute(@NotNull CommandSender sender, @NotNull String[] args);
}
