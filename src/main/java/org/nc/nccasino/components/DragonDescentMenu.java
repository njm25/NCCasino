package org.nc.nccasino.components;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

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
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.helpers.SoundHelper;

public class DragonDescentMenu extends Menu {
    private UUID dealerId;
    private Nccasino plugin;
    private String returnName;
    private Mob dealer;
    public static final Map<UUID, DragonDescentMenu> dragonInventories = new HashMap<>();
    // Track which setting is being edited
    public static final Map<UUID, String> editDragonSetting = new HashMap<>();

    public DragonDescentMenu(UUID dealerId, Player player, String title, Consumer<Player> ret, Nccasino plugin, String returnName) {
        super(player, plugin, dealerId, title, 9, returnName, ret);
        this.dealerId = dealerId;
        this.plugin = plugin;
        this.returnName = returnName;
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
        
        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.RETURN, 0);
        slotMapping.put(SlotOption.EDIT_COLUMNS, 1);
        slotMapping.put(SlotOption.EDIT_VINES, 2);
        slotMapping.put(SlotOption.EDIT_FLOORS, 3);
        dragonInventories.put(this.ownerId, this);
        initializeMenu();
    }


    @Override
    public void cleanup() {
        // 1) Unregister all event handlers for this instance
        HandlerList.unregisterAll(this);

        // 2) Remove from adminInventories
        dragonInventories.remove(ownerId);
        AdminMenu.dragonEditMode.remove(ownerId);
        // 3) Remove player references from the specialized maps
        editDragonSetting.remove(ownerId);
        this.delete();
    }

    @Override
    protected void initializeMenu() {
        String internalName = Dealer.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        
        int defaultColumns = config.getInt("dealers." + internalName + ".default-columns", 7);
        int defaultVines = config.getInt("dealers." + internalName + ".default-vines", 5);
        int defaultFloors = config.getInt("dealers." + internalName + ".default-floors", 4);
        addItemAndLore(Material.WHITE_STAINED_GLASS_PANE, defaultColumns, "Edit Default # of Columns", 
                       slotMapping.get(SlotOption.EDIT_COLUMNS), "Current: §a" + defaultColumns);
        addItemAndLore(Material.VINE, defaultVines, "Edit Default # of Vines", 
                       slotMapping.get(SlotOption.EDIT_VINES), "Current: §a" + defaultVines);
        addItemAndLore(Material.BLACK_STAINED_GLASS_PANE, defaultFloors, "Edit Default # of Floors", 
                       slotMapping.get(SlotOption.EDIT_FLOORS), "Current: §a" + defaultFloors);
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        if (!(event.getInventory().getHolder() instanceof DragonDescentMenu)) {
            return; // If not, do nothing
        }

        // If this player has this inventory open
        if (dragonInventories.containsKey(playerId)) {
            // If they're in the middle of editing, we shouldn't cleanup
            if (editDragonSetting.containsKey(playerId)) {
                return;
            }
            cleanup();
        }
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();
        if (!dragonInventories.containsKey(playerId)) return;
        
        switch (option) {
            case EDIT_COLUMNS:
                handleEditSetting(player, "default-columns", "columns", 2, 9);
                playDefaultSound(player);
                break;
            case EDIT_VINES:
                handleEditSetting(player, "default-vines", "vines", 1, 8);
                playDefaultSound(player);
                break;
            case EDIT_FLOORS:
                handleEditSetting(player, "default-floors", "floors", 1, 100);
                playDefaultSound(player);
                break;
            default:
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f); 
                
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage("§cInvalid option selected.");
                        break;
                    case VERBOSE:
                        player.sendMessage("§cInvalid Dragon Descent settings option selected.");
                        break;
                    case NONE:
                        break;
                }
                break;
        }
    }

    private void handleEditSetting(Player player, String configKey, String settingName, int min, int max) {
        UUID playerId = player.getUniqueId();
        AdminMenu.localMob.put(player.getUniqueId(), dealer);
        AdminMenu.dragonEditMode.put(playerId, dealer);
        
        editDragonSetting.put(playerId, configKey);
        player.closeInventory();
        
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD:
                player.sendMessage("§aType new default # of " + settingName + " in chat.");
                break;
            case VERBOSE:
                player.sendMessage("§aType new default # of " + settingName + " between " + min + " and " + max + " in chat.");
                break;
            case NONE:
                player.sendMessage("§aType new value.");
                break;
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!dragonInventories.containsKey(playerId)) {
            cleanup();
            return;
        }

        if (editDragonSetting.containsKey(playerId)) {
            event.setCancelled(true);
            String configKey = editDragonSetting.get(playerId);
            
            int min = 1;
            int max = 100;
            
            if (configKey.equals("default-columns")) {
                min = 2;
                max = 9;
            } else if (configKey.equals("default-vines")) {
                min = 1;
                max = 8;
            } else if (configKey.equals("default-floors")) {
                min = 1;
                max = 100;
            }
            
            handleNumericInput(player, event.getMessage().trim(), configKey, min, max, 
                              "Dragon Descent " + configKey.replace("default-", "") + " updated");
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
        
        // Special validation for vines - must be less than columns
        if (configPath.equals("default-vines")) {
            String internalName = Dealer.getInternalName(dealer);
            int columns = plugin.getConfig().getInt("dealers." + internalName + ".default-columns", 7);
            
            if (value >= columns) {
                denyAction(player, "Vines must be less than the number of columns (" + columns + ").");
                return;
            }
        }

        // Get dealer's internal name
        String internalName = Dealer.getInternalName(dealer);
           
        // Update config
        plugin.getConfig().set("dealers." + internalName + "." + configPath, value);
        plugin.saveConfig();
        
        // Clear edit mode
        editDragonSetting.remove(player.getUniqueId());
        AdminMenu.dragonEditMode.remove(player.getUniqueId());
        // Play success sound
        if (SoundHelper.getSoundSafely("entity.experience_orb.pickup", player) != null)
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1.0f, 1.0f);
        
        // Show success message
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD:
                player.sendMessage("§a" + standardMessage + ".");
                break;
            case VERBOSE:
                player.sendMessage("§a" + standardMessage + " to " + value + ".");
                break;
            case NONE:
                break;
        }
        AdminMenu.localMob.remove(player.getUniqueId());

            cleanup();
        
    }
    
    @Override
    protected void denyAction(Player player, String message) {
        if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        
        player.sendMessage("§c" + message);
    }
} 