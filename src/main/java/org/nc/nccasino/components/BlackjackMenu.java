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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.helpers.SoundHelper;
import net.md_5.bungee.api.ChatColor;

public class BlackjackMenu extends Menu {
    private UUID dealerId;
    private Nccasino plugin;
    private String returnName;
    private Mob dealer;
    public static final Map<UUID, BlackjackMenu> BAInventories = new HashMap<>();

    public BlackjackMenu(UUID dealerId,Player player, String title, Consumer<Player> ret, Nccasino plugin,String returnName) {
        super(player, plugin, dealerId, title, 9, returnName, ret);
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName=returnName;
        this.dealer = Dealer.getMobFromId(dealerId);
        if (this.dealer == null) {
            // Attempt to find a nearby Dealer if not found above
            this.dealer = (Mob) player.getWorld()
                .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                .filter(entity -> entity instanceof Mob)
                .map(entity -> (Mob) entity)
                .filter(v -> Dealer.isDealer(v)
                             && Dealer.getUniqueId(v).equals(this.dealerId))
                .findFirst().orElse(null);
        }

        slotMapping.put(SlotOption.EXIT, 0);
        slotMapping.put(SlotOption.RETURN, 1);
        slotMapping.put(SlotOption.EDIT_TIMER, 2);
        slotMapping.put(SlotOption.STAND_17, 3);
        slotMapping.put(SlotOption.NUMBER_OF_DECKS, 4);

        BAInventories.put(this.ownerId, this);
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
        BAInventories.remove(ownerId);

        // 3) Remove player references from the specialized maps
        AdminMenu.timerEditMode.remove(ownerId);
        AdminMenu.standOn17Mode.remove(ownerId);
        AdminMenu.decksEditMode.remove(ownerId);
        this.delete();
    }

    @Override
    protected void initializeMenu(){
        String internalName = Dealer.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        int currentTimer = config.contains("dealers." + internalName + ".timer")? config.getInt("dealers." + internalName + ".timer"): 10; 
        int standOn17Chance = config.getInt("dealers." + internalName + ".stand-on-17", 100);
        int numberOfDecks = config.getInt("dealers." + internalName + ".number-of-decks", 6);
        addItemAndLore(Material.CLOCK, currentTimer, "Edit Timer",  slotMapping.get(SlotOption.EDIT_TIMER), "Current: §a" + currentTimer);
        addItemAndLore(Material.SHIELD, standOn17Chance, "Edit Stand on 17 Chance", slotMapping.get(SlotOption.STAND_17), "Current: §a" + standOn17Chance + "%");
        addItemAndLore(Material.RED_STAINED_GLASS_PANE, numberOfDecks, "Edit Number of Decks", slotMapping.get(SlotOption.NUMBER_OF_DECKS), "Current: §a" + numberOfDecks);
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to "+returnName,  slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit",  slotMapping.get(SlotOption.EXIT));

    }

    public boolean isPlayerOccupied(UUID playerId){
        return 
            !AdminMenu.timerEditMode.containsKey(playerId) &&
            !AdminMenu.standOn17Mode.containsKey(playerId) &&
            !AdminMenu.decksEditMode.containsKey(playerId);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        if(event.getInventory().getHolder() instanceof BlackjackMenu){
        // Check if the player has an active AdminInventory
            if (BAInventories.containsKey(playerId)) {
                    // Check if the player is currently editing something
                if (isPlayerOccupied(playerId)) {
                    // Remove the AdminInventory and clean up references
                    BlackjackMenu inventory = BAInventories.remove(playerId);

                    if (inventory != null) {
                        inventory.cleanup();
                        inventory.delete();
                    }
                
                    // Unregister this listener if no more AdminInventories exist
                    if (BAInventories.isEmpty()) {
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
    protected void handleCustomClick(SlotOption option, Player player) {
        UUID playerId = player.getUniqueId();
        if (!BAInventories.containsKey(playerId)) return;
        switch (option) {
            case EDIT_TIMER:
                handleEditTimer(player);
                playDefaultSound(player);
                break;
            case STAND_17:
                handleEditStand(player);
                playDefaultSound(player);
                break;   
            case NUMBER_OF_DECKS:
                handleEditDecks(player);
                playDefaultSound(player);
                break;  
            default:
                if(SoundHelper.getSoundSafely("entity.villager.no",player)!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                    case STANDARD:{
                        player.sendMessage("§cInvalid option selected.");
                        break;}
                    case VERBOSE:{
                        player.sendMessage("§cInvalid blackjack settings option selected.");
                        break;}
                    case NONE:{
                        break;
                    }
                }
                break;
        }
    }

    private void handleEditStand(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(playerId, dealer);
        AdminMenu.standOn17Mode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§aType new stand on 17 percent in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType new stand on 17 percent value between 0 and 100 in chat.");
                break;}
            case NONE:{
                player.sendMessage("§aType new value.");
                break;
            }
        }
    }

    private void handleEditDecks(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(playerId, dealer);
        AdminMenu.decksEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§aType new number in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType the new number of decks.");
                break;}
            case NONE:{
                player.sendMessage("§aType new value.");
                break;
            }
        }
    }


    private void handleEditTimer(Player player) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(playerId, dealer);
        AdminMenu.timerEditMode.put(playerId, dealer);
        player.closeInventory();
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage("§aType new number in chat.");
                break;}
            case VERBOSE:{
                player.sendMessage("§aType the new timer in chat.");
                break;}
            case NONE:{
                player.sendMessage("§aType new value.");
                break;
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String message = event.getMessage().trim();

        if (BAInventories.get(playerId) == null) {
            cleanup();
            return;
        }
         if (AdminMenu.timerEditMode.get(playerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, message, "timer", 1, 10000, "Dealer timer updated");
        } 
        else if (AdminMenu.standOn17Mode.get(playerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, message, "stand-on-17", 0, 100, "Dealer stand on 17 chance updated");
        } 
        else if (AdminMenu.decksEditMode.get(playerId) != null) {
            event.setCancelled(true);
            handleNumericInput(player, message, "number-of-decks", 1, 10000, "Dealer number of decks updated");
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
                    player.sendMessage("§a" + standardMessage+".");
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
                    player.sendMessage("§cCould not find blackjack settings dealer.");
                    break;
                case NONE:
                    break;
            }
        }

        cleanup();
    }
}
