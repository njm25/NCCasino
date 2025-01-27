package org.nc.nccasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminInventory;
import org.nc.nccasino.components.AnimationTable;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.helpers.SoundHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DealerInteractListener implements Listener {
    private final Nccasino plugin;
    private final Set<Player> activeAnimations = new HashSet<>();
    private Villager villager;

    public DealerInteractListener(Nccasino plugin) {
        this.plugin = plugin;

    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity clickedEntity = event.getRightClicked();
        if (!(clickedEntity instanceof Villager)) return;
        this.villager =  (Villager) clickedEntity;
        if (!DealerVillager.isDealerVillager(villager)) return;
        Player player = event.getPlayer();
        UUID dealerId = DealerVillager.getUniqueId(villager);
        
        List<String> occupations = AdminInventory.playerOccupations(player.getUniqueId());
        List<Villager> villagers = AdminInventory.getOccupiedVillagers(player.getUniqueId());
        
        if (!occupations.isEmpty()) {
            if (SoundHelper.getSoundSafely("entity.villager.no") != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            for (int i = 0; i < occupations.size(); i++) {
                String occupation = occupations.get(i);
                Villager villager = (i < villagers.size()) ? villagers.get(i) : null;
                
                String villagerName = (villager != null) ? DealerVillager.getInternalName(villager) : "unknown villager";
                Nccasino.sendErrorMessage(player, "Please finish editing " + occupation + " for " + villagerName);
            }
            return;
        }
        
        else if (player.isSneaking() && player.hasPermission("nccasino.use")) {
            handleAdminInventory(player, dealerId);
        } else {
            handleDealerInventory(player, dealerId);
        }

        event.setCancelled(true);
    }

    private void handleAdminInventory(Player player, UUID dealerId) {
        if (AdminInventory.adminInventories.containsKey(player.getUniqueId())) {
            AdminInventory adminInventory = AdminInventory.adminInventories.get(player.getUniqueId());
            player.openInventory(adminInventory.getInventory());
        } else {
            AdminInventory adminInventory = new AdminInventory(dealerId, player, plugin);
            player.openInventory(adminInventory.getInventory());
        }
    }

    private void handleDealerInventory(Player player, UUID dealerId) {
        DealerInventory dealerInventory = DealerInventory.inventories.get(dealerId);
        if (shouldPlayAnimation(player, dealerId)) {
            startAnimation(player, dealerInventory, dealerId);
        } else {
            player.openInventory(dealerInventory.getInventory());
        }
    }

    private boolean shouldPlayAnimation(Player player, UUID dealerId) {
        // Logic to determine if an animation should play before opening the inventory
        return !activeAnimations.contains(player) && plugin.getConfig().contains("dealers." + DealerVillager.getInternalName(villager) + ".animation-message");
    }

    private void startAnimation(Player player, DealerInventory dealerInventory, UUID dealerId) {
        String animationMessage = plugin.getConfig().getString("dealers." + DealerVillager.getInternalName(villager) + ".animation-message");
        activeAnimations.add(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            AnimationTable animationTable = new AnimationTable(player, plugin, animationMessage, 0);
            player.openInventory(animationTable.getInventory());

            animationTable.animateMessage(player, () -> afterAnimationComplete(player, dealerInventory));
        }, 1L);
    }

    private void afterAnimationComplete(Player player, DealerInventory dealerInventory) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player != null && player.isOnline()) {
                activeAnimations.remove(player);
                player.openInventory(dealerInventory.getInventory());
            }
        }, 1L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (event.getInventory().getHolder() instanceof DealerInventory) {
            event.setCancelled(true);
            DealerInventory dealerInventory = (DealerInventory) event.getInventory().getHolder();
            dealerInventory.handleClick(event.getSlot(), player, event);
        }
    }
}
