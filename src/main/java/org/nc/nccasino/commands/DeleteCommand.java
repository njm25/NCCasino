package org.nc.nccasino.commands;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminMenu;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.JockeyManager;
import org.nc.nccasino.entities.JockeyNode;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.listeners.DealerEventListener;

public class DeleteCommand implements CasinoCommand {

    private final Nccasino plugin;
    private final File dealersFile;
    private final YamlConfiguration dealersConfig;

    public DeleteCommand(Nccasino plugin) {
        this.plugin = plugin;
        this.dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");
        this.dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLocalization().text(sender, "commands.usage-delete"));
            return true;
        }

        String internalName = args[1];    
        Player player = (Player) sender;

        List<String> occupations = AdminMenu.playerOccupations(player.getUniqueId());
        List<LivingEntity> mobs = AdminMenu.getOccupiedDealers(player.getUniqueId())
            .stream()
            .filter(v -> v != null && !v.isDead() && v.isValid()) // Ensure valid mobs
            .toList();

        if (!occupations.isEmpty() && !mobs.isEmpty()) {
            if (SoundHelper.getSoundSafely("entity.villager.no",player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            for (int i = 0; i < occupations.size(); i++) {
                if (i >= mobs.size()) {
                    break; // Prevent index mismatch
                }
                String occupation = plugin.getLocalization().text(player, occupations.get(i));
                LivingEntity mob = mobs.get(i);
                
                String mobName = (mob != null) ? Dealer.getInternalName(mob) : "unknown mob";
                player.sendMessage(plugin.getLocalization().text(
                    player,
                    "commands.finish-editing",
                    "occupation",
                    occupation,
                    "name",
                    mobName
                ));
            }
            return true;
        }
        
        AdminMenu.deleteAssociatedAdminInventories(player);

        if (internalName.equals("*")) {
            plugin.executeOnAllDealers(sender, true);
            return true;
        }
        

        plugin.executeOnDealer(internalName, () -> {
            LivingEntity mob = plugin.getDealerByInternalName(internalName);
            if (mob == null) {
                sender.sendMessage(plugin.getLocalization().text(
                    sender,
                    "commands.dealer-not-found",
                    "name",
                    internalName
                ));
                return;
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                // First clean up all associated inventories
                plugin.deleteAssociatedInventories(mob);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Jockey stacks and armor-stand riders only exist on mob
                    // dealers; a Citizens-backed dealer has neither.
                    if (mob instanceof Mob mobEntity) {
                        // Create a JockeyManager to handle stack cleanup
                        JockeyManager jockeyManager = new JockeyManager(mobEntity);

                        // Clean up all jockeys in the stack
                        jockeyManager.cleanup();

                        // Remove all jockeys and vehicles
                        List<JockeyNode> jockeys = jockeyManager.getJockeys();
                        for (int i = jockeys.size() - 1; i > 0; i--) {
                            JockeyNode jockey = jockeys.get(i);
                            // First unmount to prevent any issues
                            jockey.unmount();
                            // Then remove the physical entity
                            jockey.getMob().remove();
                        }

                        // Check for and remove any armor stand passengers
                        for (Entity passenger : mobEntity.getPassengers()) {
                            if (passenger instanceof ArmorStand) {
                                passenger.remove();
                            }
                        }
                    }

                    // Remove the dealer and all its data
                    Dealer.removeDealer(mob);
                    DealerInventory.unregisterAllListeners(mob);
                    removeDealerData(internalName);
                    
                    // Clear any remaining references
                    AdminMenu.clearAllEditModes(mob);
                    AdminMenu.deleteAssociatedAdminInventories(player);
                    
                    // Remove from jockey manager cache
                    DealerEventListener.clearJockeyManagerCache(mob.getUniqueId());
                    
                    sender.sendMessage(plugin.getLocalization().text(
                        sender,
                        "commands.dealer-deleted",
                        "name",
                        internalName
                    ));
                });
            });
        });

        return true;
    }

    private void removeDealerData(String internalName) {
        internalName = internalName.trim(); // Sanitize input
        String path = "dealers." + internalName;

        try {
            dealersConfig.load(dealersFile); // Reload the YAML file
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Failed to load dealers.yaml: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (dealersConfig.contains(path)) {
            dealersConfig.set(path, null); // Remove the specific dealer
            try {
                dealersConfig.save(dealersFile); // Save the updated configuration
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save dealers.yaml while removing dealer: " + internalName);
                e.printStackTrace();
            }
        }
    }
}
