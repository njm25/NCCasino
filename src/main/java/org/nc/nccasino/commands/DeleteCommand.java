package org.nc.nccasino.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.*;

public class DeleteCommand implements CasinoCommand {

    private final Nccasino plugin;

    public DeleteCommand(Nccasino plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /ncc delete ")
                    .color(NamedTextColor.AQUA)
                    .append(Component.text("<name>").color(NamedTextColor.YELLOW)));
            return true;
        }

        String internalName = args[1];

        if (internalName.equals("*")) {
            // Delete all known dealers
            
            int countt=killAllDealerVillagers();
            sender.sendMessage(Component.text("Deleted " + countt + " dealers.")
                    .color(NamedTextColor.GREEN));
            return true;
        }

      

        Villager villager = plugin.getDealerByInternalName(internalName);

        if (villager == null) {
            sender.sendMessage(Component.text("Dealer with internal name '")
                    .color(NamedTextColor.RED)
                    .append(Component.text(internalName).color(NamedTextColor.YELLOW))
                    .append(Component.text("' not found.").color(NamedTextColor.RED)));
            return true;
        }

        DealerVillager.removeDealer(villager);
        sender.sendMessage(Component.text("Dealer '")
                .color(NamedTextColor.GREEN)
                .append(Component.text(internalName).color(NamedTextColor.YELLOW))
                .append(Component.text("' has been deleted.").color(NamedTextColor.GREEN)));

        return true;
    }

    private int  killAllDealerVillagers() {
        int deletedCount = 0;
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    if (DealerVillager.isDealerVillager(villager)) {
                        DealerVillager.removeDealer(villager);
                        deletedCount++;
                    }
                }
            }
        }
        return deletedCount;
    }
}
