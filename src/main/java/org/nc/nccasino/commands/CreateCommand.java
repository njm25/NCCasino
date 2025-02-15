package org.nc.nccasino.commands;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminInventory;
import org.nc.nccasino.components.GameOptionsInventory;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.helpers.SoundHelper;
import org.bukkit.entity.Mob;

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

        Player player = (Player) sender;
                
        List<String> occupations = AdminInventory.playerOccupations(player.getUniqueId());
        List<Mob> mobs = AdminInventory.getOccupiedDealers(player.getUniqueId())
            .stream()
            .filter(v -> v != null && !v.isDead() && v.isValid()) // Ensure valid mob
            .toList();

        if (!occupations.isEmpty() && !mobs.isEmpty()) {
            if (SoundHelper.getSoundSafely("entity.villager.no",player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            for (int i = 0; i < occupations.size(); i++) {
                if (i >= mobs.size()) {
                    break; // Prevent index mismatch
                }
                String occupation = occupations.get(i);
                Mob mob = mobs.get(i);
                
                String mobName = (mob != null) ? Dealer.getInternalName(mob) : "unknown dealer";
                Nccasino.sendErrorMessage(player, "Please finish editing " + occupation + " for '" +
                    ChatColor.YELLOW + mobName + ChatColor.RED + "'.");
            }
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /ncc create " + ChatColor.YELLOW + "<name>");
            return true;
        }

        String internalName = args[1];

        // Check if the internal name already exists
        if (plugin.getConfig().contains("dealers." + internalName)) {
            sender.sendMessage(ChatColor.RED + "A dealer with the internal name '" +
                    ChatColor.YELLOW + internalName + ChatColor.RED + "' already exists.");
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
