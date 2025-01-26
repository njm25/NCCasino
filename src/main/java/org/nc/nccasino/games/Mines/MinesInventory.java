package org.nc.nccasino.games.Mines;

import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminInventory;
import org.nc.nccasino.components.AnimationTable;
import org.bukkit.event.HandlerList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MinesInventory extends DealerInventory implements Listener {
    private final Map<UUID, MinesTable> Tables;
    private final Nccasino plugin;
    private final Map<UUID, Boolean> interactionLocks = new HashMap<>();
    private Boolean firstopen=true;
    private String internalName;
    public MinesInventory(UUID dealerId, Nccasino plugin, String internalName) {
        
        super(dealerId, 54, "Mines Start Menu");
        this.firstopen = true;
        this.internalName = internalName;
        this.plugin = plugin;
        this.Tables = new HashMap<>();
        registerListener();
        plugin.addInventory(dealerId, this);
    }

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void unregisterListener() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public void delete() {

        super.delete();
        Tables.clear();
        interactionLocks.clear();
        unregisterListener();  // Unregister listener when deleting the inventory
    }
   

    @EventHandler
    public void handlePlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        Villager villager = (Villager) event.getRightClicked();
        Player player = event.getPlayer();

        if (DealerVillager.isDealerVillager(villager) && DealerVillager.getUniqueId(villager).equals(this.dealerId)) {
            if (!firstopen) {
                startAnimation(player);

            }
        }
    }

    @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event) {
        Player player = (Player) event.getPlayer();
        if (player.getInventory() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() == this.getInventory().getHolder()) {
                    if (firstopen) {
                        firstopen = false;
                        startAnimation(player);

                    }
                }
            }, 2L);
        }
    }


    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;
        event.setCancelled(true);
    }                                                                                                                      
  
    // Immediately have player open local mines table
    private void setupGameMenu(Player player) {
       
                MinesTable minesTable = new MinesTable(player, plugin, internalName, this);
                Tables.put(player.getUniqueId(), minesTable);

    }

  
    public void removeTable(UUID playerId) {
        Tables.remove(playerId);  // Remove by UUID
        interactionLocks.remove(playerId);  // Clear interaction lock on removal
    }

    private void startAnimation(Player player) {
        // Retrieve the animation message from the config for the current dealer
        String animationMessage = plugin.getConfig().getString("dealers." + internalName + ".animation-message");

        // Delaying the inventory opening to ensure it displays properly
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isSneaking() && player.hasPermission("nccasino.use")) {
                // Open the admin inventory immediately without animation
                if (AdminInventory.adminInventories.get(player.getUniqueId()) != null ){
                    if(AdminInventory.villagerMap.get(player.getUniqueId()) != null){
                        AdminInventory.villagerMap.remove(player.getUniqueId());
                    }
                    System.out.println("opening existing");
                    AdminInventory adminInventory = AdminInventory.adminInventories.get(player.getUniqueId());
                    
                    AdminInventory.villagerMap.put(player.getUniqueId(), DealerVillager.getVillagerFromId(dealerId));
                    player.openInventory(adminInventory.getInventory());
                }
                else{
                    System.out.println("making new");
                    AdminInventory adminInventory = new AdminInventory(dealerId, player, (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class));
                    player.openInventory(adminInventory.getInventory());
                }
            } else {
                // Proceed with the animation for the regular inventory
                setupGameMenu(player);

                AnimationTable animationTable = new AnimationTable(player, plugin, animationMessage, 0);
                player.openInventory(animationTable.getInventory());

                // Start animation and return to MinesTable after completion
                animationTable.animateMessage(player, () -> afterAnimationComplete(player));
            }
        }, 1L); // Delay by 1 tick to ensure smooth inventory opening
    }

    private void afterAnimationComplete(Player player) {
        // Add a slight delay to ensure smooth transition from the animation to the table
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Tables.get(player.getUniqueId()).initializeTable();
            if (player != null) {

                player.openInventory(Tables.get(player.getUniqueId()).getInventory());

                // Inform the player about the default number of mines
            }
        }, 1L); // Delay by 1 tick to ensure clean transition between inventories
    }


}
