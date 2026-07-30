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
    @SuppressWarnings("unused")
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
        this.dealer =  Dealer.findDealer(dealerId, player.getLocation());
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

    /**
     * Tears down this player's open DragonDescentMenu, if any — that also
     * clears editDragonSetting and AdminMenu.dragonEditMode via cleanup().
     * AdminMenu.dragonEditMode is additionally cleared separately and
     * unconditionally by AdminMenu.clearPlayerEditState, but
     * editDragonSetting is owned only here, so if no menu instance exists
     * (e.g. mid chat-wait for a specific setting) it must still be cleared
     * directly.
     */
    public static void clearPlayerState(UUID playerId) {
        DragonDescentMenu menu = dragonInventories.get(playerId);
        if (menu != null) {
            menu.cleanup();
        } else {
            editDragonSetting.remove(playerId);
        }
    }

    @Override
    protected void initializeMenu() {
        String internalName = Dealer.getInternalName(dealer);
        FileConfiguration config = plugin.getConfig();
        
        int defaultColumns = config.getInt("dealers." + internalName + ".default-columns", 7);
        int defaultVines = config.getInt("dealers." + internalName + ".default-vines", 5);
        int defaultFloors = config.getInt("dealers." + internalName + ".default-floors", 4);
        addItemAndLore(Material.WHITE_STAINED_GLASS_PANE, defaultColumns, text("dragon-settings.edit-columns"),
                       slotMapping.get(SlotOption.EDIT_COLUMNS), text("common.current", "value", defaultColumns));
        addItemAndLore(Material.VINE, defaultVines, text("dragon-settings.edit-vines"),
                       slotMapping.get(SlotOption.EDIT_VINES), text("common.current", "value", defaultVines));
        addItemAndLore(Material.BLACK_STAINED_GLASS_PANE, defaultFloors, text("dragon-settings.edit-floors"),
                       slotMapping.get(SlotOption.EDIT_FLOORS), text("common.current", "value", defaultFloors));
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, text("common.return-to", "menu", returnName), slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, text("common.exit"), slotMapping.get(SlotOption.EXIT));
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
                handleEditSetting(player, "default-columns", text("dragon-settings.columns"), 2, 9);
                playDefaultSound(player);
                break;
            case EDIT_VINES:
                handleEditSetting(player, "default-vines", text("dragon-settings.vines"), 1, 8);
                playDefaultSound(player);
                break;
            case EDIT_FLOORS:
                handleEditSetting(player, "default-floors", text("dragon-settings.floors"), 1, 100);
                playDefaultSound(player);
                break;
            default:
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f); 
                
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage(text("dragon-settings.invalid-option"));
                        break;
                    case VERBOSE:
                        player.sendMessage(text("dragon-settings.invalid-settings-option"));
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
                player.sendMessage(text("dragon-settings.prompt-setting", "setting", settingName));
                break;
            case VERBOSE:
                player.sendMessage(text(
                    "dragon-settings.prompt-setting-detailed",
                    "setting",
                    settingName,
                    "min",
                    min,
                    "max",
                    max
                ));
                break;
            case NONE:
                player.sendMessage(text("admin.prompt-new-value"));
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
            
            handleNumericInput(player, event.getMessage().trim(), configKey, min, max);
        }
    }

    private void handleNumericInput(Player player, String input, String configPath, long min, long max) {
        if (input.isEmpty() || !input.matches("\\d+")) {
            denyAction(player, text("blackjack-settings.valid-positive-integer"));
            return;
        }

        long value;
        try {
            value = Long.parseLong(input);
        } catch (NumberFormatException e) {
            denyAction(player, text("blackjack-settings.invalid-number-format"));
            return;
        }

        if (value < min || value > max) {
            denyAction(player, text("blackjack-settings.number-range", "min", min, "max", max));
            return;
        }
        
        // Special validation for vines - must be less than columns
        if (configPath.equals("default-vines")) {
            String internalName = Dealer.getInternalName(dealer);
            int columns = plugin.getConfig().getInt("dealers." + internalName + ".default-columns", 7);
            
            if (value >= columns) {
                denyAction(player, text("dragon-settings.vines-less-columns", "columns", columns));
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
                player.sendMessage(text(settingUpdatedKey(configPath)));
                break;
            case VERBOSE:
                player.sendMessage(text(
                    "dragon-settings.updated-detailed",
                    "setting",
                    text(settingUpdatedKey(configPath)),
                    "value",
                    value
                ));
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

    private String settingUpdatedKey(String configPath) {
        return switch (configPath) {
            case "default-columns" -> "dragon-settings.columns-updated";
            case "default-vines" -> "dragon-settings.vines-updated";
            default -> "dragon-settings.floors-updated";
        };
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }
}
