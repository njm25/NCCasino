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
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.helpers.SoundHelper;
import net.md_5.bungee.api.ChatColor;

public class RouletteMenu extends Menu {
    private UUID dealerId;
    private Nccasino plugin;
    private String returnName;
    private Mob dealer;
    public static final Map<UUID, RouletteMenu> RAInventories = new HashMap<>();

    public RouletteMenu(UUID dealerId, Player player, String title, Consumer<Player> ret, Nccasino plugin, String returnName) {
        super(player, plugin, dealerId, title, 9, title, ret);
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName = returnName;
        this.dealer = Dealer.findDealer(dealerId, player.getLocation());

        RAInventories.put(this.ownerId, this);
        
        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.RETURN, 0);
        slotMapping.put(SlotOption.EDIT_TIMER, 1);
        initializeMenu();
    }

    private void unregisterListener() {
        InventoryCloseEvent.getHandlerList().unregister(this);
    }

    @Override
    public void cleanup() {
        // 1) Unregister all event handlers for this instance
        HandlerList.unregisterAll(this);

        // 2) Remove from adminInventories
        RAInventories.remove(ownerId);

        // 3) Remove player references from the specialized maps
        AdminMenu.timerEditMode.remove(ownerId);
        this.delete();
    }

    @Override
    protected void initializeMenu(){
        String internalName = Dealer.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int currentTimer = config.contains("dealers." + internalName + ".timer")
        ? config.getInt("dealers." + internalName + ".timer")
        : 10; // Default to 10 if missing
        addItemAndLore(Material.CLOCK, currentTimer, "Edit Timer",  slotMapping.get(SlotOption.EDIT_TIMER), "Current: §a" + currentTimer);
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to "+returnName,  slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if(event.getInventory().getHolder() instanceof RouletteMenu){
        // Check if the player has an active AdminInventory
            if (RAInventories.containsKey(playerId)) {
                    // Check if the player is currently editing something
                if (!AdminMenu.timerEditMode.containsKey(playerId)) {
                    // Remove the AdminInventory and clean up references
                    RouletteMenu inventory = RAInventories.remove(playerId);

                    if (inventory != null) {
                        inventory.cleanup();
                        inventory.delete();
                    }
                
                    // Unregister this listener if no more AdminInventories exist
                    if (RAInventories.isEmpty()) {
                        unregisterListener();
                    }
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof AdminMenu) {
                        return;
                    }
                    AdminMenu temp=AdminMenu.adminInventories.get(player.getUniqueId());
                    if(temp!=null){
                        if(temp.getDealerId()==dealerId){
                            temp.delete();
                        }
                    }
                }
                , 5L);
            }
        }
    }


    @Override
    public void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        if (!RAInventories.containsKey(playerId)) return;

        switch (option) {
            case EDIT_TIMER:
                handleEditTimer(player);
                playDefaultSound(player);
                break;
            default:
                if(SoundHelper.getSoundSafely("entity.villager.no",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid option selected.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cInvalid roulette settings option selected.");
                        break;}
                    case NONE:{
                        break;
                    }
                }                    
                break;
        }
    }

    private void handleEditTimer(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(player.getUniqueId(), dealer);
        AdminMenu.timerEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§aType the new number in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType the new timer in chat.");
                break;}
            case NONE:{
                player.sendMessage("§aType the value.");
                break;
            }
        }  
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!RAInventories.containsKey(playerId)) {
            cleanup();
            return;
        }

        if (AdminMenu.timerEditMode.get(playerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, event.getMessage().trim(), "timer", 1, 10000, "Dealer timer updated");
        }
    }

    private void handleNumericInput(Player player, String input, String configPath, long min, long max, String standardMessage) {
        if (input.isEmpty() || !input.matches("\\d+")) {
            denyAction(player, "Please enter a valid positive integer.");
            return;
        }

        long value;
        try {
            value = Long.parseLong(input);
        } catch (NumberFormatException e) {
            denyAction(player, "Invalid number format.");
            return;
        }

        if (value < min || value > max) {
            denyAction(player, "Please enter a number between " + min + " and " + max + ".");
            return;
        }

        if (dealer != null) {
            String internalName = Dealer.getInternalName(dealer);
            plugin.getConfig().set("dealers." + internalName + "." + configPath, value);
            plugin.saveConfig();
            plugin.reloadDealer(dealer);

            if (SoundHelper.getSoundSafely("entity.villager.work_cartographer", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER, 1.0f, 1.0f);
            }

            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage("§a" + standardMessage + ".");
                    break;
                case VERBOSE:
                    player.sendMessage("§a" + standardMessage + " to: " + ChatColor.YELLOW + value + "§a.");
                    break;
                case NONE:
                    break;
            }

            AdminMenu.localMob.remove(player.getUniqueId());
        } else {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage("§cCould not find dealer.");
                    break;
                case VERBOSE:
                    player.sendMessage("§cCould not find roulette settings dealer.");
                    break;
                case NONE:
                    break;
            }
        }

        plugin.deleteAssociatedInventories(dealer);
        cleanup();
    }

}
