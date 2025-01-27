package org.nc.nccasino.commands;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminInventory;
import org.nc.nccasino.components.GameOptionsInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.helpers.SoundHelper;

public class CreateCommand implements CasinoCommand {
    private final JavaPlugin plugin;

    public CreateCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /ncc create " + ChatColor.YELLOW + "<name>");
            return true;
        }

        Player player = (Player) sender;
        String internalName = args[1];

        // Check if the internal name already exists
        if (plugin.getConfig().contains("dealers." + internalName)) {
            sender.sendMessage(ChatColor.RED + "A dealer with the internal name '" +
                    ChatColor.YELLOW + internalName + ChatColor.RED + "' already exists.");
            return true;
        }

                
        List<String> occupations = AdminInventory.playerOccupations(player.getUniqueId());
        List<Villager> villagers = AdminInventory.getOccupiedVillagers(player.getUniqueId())
            .stream()
            .filter(v -> v != null && !v.isDead() && v.isValid()) // Ensure valid villagers
            .toList();

        if (!occupations.isEmpty() && !villagers.isEmpty()) {
            if (SoundHelper.getSoundSafely("entity.villager.no") != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            for (int i = 0; i < occupations.size(); i++) {
                if (i >= villagers.size()) {
                    break; // Prevent index mismatch
                }
                String occupation = occupations.get(i);
                Villager villager = villagers.get(i);
                
                String villagerName = (villager != null) ? DealerVillager.getInternalName(villager) : "unknown villager";
                Nccasino.sendErrorMessage(player, "Please finish editing " + occupation + " for " + villagerName);
            }
            return true;
        }
        // Open the Game Options Inventory
        GameOptionsInventory inventory = new GameOptionsInventory((Nccasino)plugin, internalName);
        player.openInventory(inventory.getInventory());

        sender.sendMessage(ChatColor.GREEN + "Choose a game type for the dealer '" +
                ChatColor.YELLOW + internalName + ChatColor.GREEN + "'.");

        return true;
    }
}
