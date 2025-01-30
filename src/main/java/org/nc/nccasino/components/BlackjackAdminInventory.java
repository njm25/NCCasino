package org.nc.nccasino.components;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerVillager;
//import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.helpers.SoundHelper;

import net.md_5.bungee.api.ChatColor;

public class BlackjackAdminInventory extends LinkedInventory {
    private final UUID ownerId;
    private final Consumer<UUID> ret;
    private UUID dealerId;
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player
    private Nccasino plugin;
    private String returnName;
    private Villager dealer;
    public static final Map<UUID, Villager> localBAVillager = new HashMap<>();
    private static final Map<UUID, Villager> timerEditMode = new HashMap<>();
    public static final Map<UUID, BlackjackAdminInventory> BAInventories = new HashMap<>();


    private enum SlotOption {
        RETURN,
        EDIT_TIMER
     }
     private final Map<SlotOption, Integer> slotMapping = new HashMap<>() {{
         put(SlotOption.RETURN, 0);
         put(SlotOption.EDIT_TIMER, 1);
     }};

    public BlackjackAdminInventory(UUID dealerId,Player player, String title, Consumer<UUID> ret, Nccasino plugin,String returnName) {
        super(dealerId, player,title,ret,plugin,returnName,9);
       // super(dealerId, 9, title);
        this.ret = ret;
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName=returnName;
        this.dealer = DealerVillager.getVillagerFromId(dealerId);
        if (this.dealer == null) {
            // Attempt to find a nearby Dealer if not found above
            this.dealer = (Villager) player.getWorld()
                .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                .filter(entity -> entity instanceof Villager)
                .map(entity -> (Villager) entity)
                .filter(v -> DealerVillager.isDealerVillager(v)
                             && DealerVillager.getUniqueId(v).equals(this.dealerId))
                .findFirst().orElse(null);
        }
        registerListener();
        this.ownerId = player.getUniqueId();  // Store the player's ID

        BAInventories.put(this.ownerId, this);
        initalizeMenu();
        
    }

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

        public void cleanup() {
        // 1) Unregister all event handlers for this instance
        HandlerList.unregisterAll(this);

        // 2) Remove from adminInventories
        BAInventories.remove(ownerId);

        // 3) Remove player references from the specialized maps
        timerEditMode.remove(ownerId);
        clickAllowed.remove(ownerId);
    }

    private void initalizeMenu(){
        String internalName = DealerVillager.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int currentTimer = config.contains("dealers." + internalName + ".timer")
        ? config.getInt("dealers." + internalName + ".timer")
        : 10; // Default to 10 if missing
        addItemAndLore(Material.CLOCK, currentTimer, "Edit Timer",  slotMapping.get(SlotOption.EDIT_TIMER), "Current: " + currentTimer);
        addItem(createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Return to "+returnName), 0);
    }



    public void executeReturn() {
        ret.accept(dealerId);
    }


    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        if (!BAInventories.containsKey(playerId)) return;
        //event.setCancelled(true); // Default behavior: prevent unintended interactions

        if (event.getClickedInventory() == null) return; 
        if (clickAllowed.getOrDefault(playerId, true)) {
            clickAllowed.put(playerId, false); // Prevent rapid clicking
            Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L);

            SlotOption option = getKeyByValue(slotMapping, slot);
            if(option!=null){
            switch (option) {
                case RETURN:
                    if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    executeReturn();
                    break;
                case EDIT_TIMER:
                handleEditTimer(player);
                if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                    break;
                default:
                    if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                    player.sendMessage("§cInvalid option selected.");
                    break;
            }}
        } else {
            player.sendMessage("§cPlease wait before clicking again!");
        }
    }

    private void handleEditTimer(Player player) {
        UUID playerId = player.getUniqueId();
        localBAVillager.put(playerId, dealer);
        timerEditMode.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aType the new timer in chat.");
    }

      @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (BAInventories.get(playerId) == null){
            cleanup();
            return;
        }
         if (timerEditMode.get(playerId) != null) {
            event.setCancelled(true);
            String newTimer = event.getMessage().trim();

            if (newTimer.isEmpty() || !newTimer.matches("\\d+") || Integer.parseInt(newTimer) <= 0) {
                denyAction(player, "Please enter a positive number.");
                return;
            }
            if (dealer != null) {
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".timer", Integer.parseInt(newTimer));
                plugin.saveConfig();
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aDealer timer updated to: " + ChatColor.YELLOW + newTimer + "§a.");
            } else {
                player.sendMessage("§cCould not find dealer.");
            }

                
            cleanup();
        }

    }
    private void denyAction(Player player, String message) {
        if (SoundHelper.getSoundSafely("entity.villager.no") != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        player.sendMessage("§c" + message);
    }
    
    public void delete() {
        cleanup();
        super.delete(); 
        // super.delete() removes from DealerInventory.inventories & clears the Inventory
    }

}
