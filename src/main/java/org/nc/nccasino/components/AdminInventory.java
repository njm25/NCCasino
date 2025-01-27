package org.nc.nccasino.components;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.helpers.SoundHelper;

public class AdminInventory extends DealerInventory {

    /**
     * We store a reference to the "owner" Player's UUID so we know
     * which player maps we must remove references from in cleanup().
     */
    private final UUID ownerId;

    private UUID dealerId;
    private Villager dealer;
    private final Nccasino plugin;

    // Track click state per player
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>();

    // Static maps referencing AdminInventory or the player's editing states
    private static final Map<UUID, Villager> moveMode = new HashMap<>();
    private static final Map<UUID, Villager> nameEditMode = new HashMap<>();
    private static final Map<UUID, Villager> timerEditMode = new HashMap<>();
    private static final Map<UUID, Villager> amsgEditMode = new HashMap<>();

    // All active AdminInventories by player ID
    public static final Map<UUID, AdminInventory> adminInventories = new HashMap<>();

    // Tracks which dealer is being edited by which player
    public final Map<UUID, Villager> localVillager = new HashMap<>();

    // Slot options for the admin menu
    private enum SlotOption {
        EDIT_DISPLAY_NAME,
        EDIT_GAME_TYPE,
        MOVE_DEALER,
        DELETE_DEALER,
        EDIT_CURRENCY,
        USE_VAULT,
        EDIT_TIMER,
        EDIT_ANIMATION_MESSAGE
    }

    // The slot positions of each option in the inventory
    private final Map<SlotOption, Integer> slotMapping = new HashMap<>() {{
        put(SlotOption.EDIT_DISPLAY_NAME, 10);
        put(SlotOption.EDIT_GAME_TYPE, 12);
        put(SlotOption.EDIT_TIMER, 14);
        put(SlotOption.EDIT_ANIMATION_MESSAGE, 16);

        // put(SlotOption.USE_VAULT, 28);
        // put(SlotOption.EDIT_CURRENCY, 30);

        put(SlotOption.MOVE_DEALER, 30);
        put(SlotOption.DELETE_DEALER, 32);
    }};

    /**
     * Constructor: creates an AdminInventory for a specific dealer, owned by a specific player.
     */
    public AdminInventory(UUID dealerId, Player player, Nccasino plugin) {
        super(player.getUniqueId(), 54, 
        
            DealerVillager.getInternalName((Villager) player.getWorld()
                    .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                    .filter(entity -> entity instanceof Villager)
                    .map(entity -> (Villager) entity)
                    .filter(v -> DealerVillager.isDealerVillager(v)
                                && DealerVillager.getUniqueId(v).equals(dealerId))
                    .findFirst().orElse(null))

                    + "'s Admin Menu"
        ); 
        
        // ^ We are using the player's UUID as "dealerId" in the DealerInventory parent. That's your existing logic.

        this.ownerId = player.getUniqueId();  // Store the player's ID
        this.dealerId = dealerId;
        this.plugin = plugin;

        // Find the actual Villager instance (the "dealer")
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
        adminInventories.put(this.ownerId, this);
        initializeAdminMenu();
    }

    /**
     * Registers this inventory as an event listener with the plugin.
     */
    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Build out the Admin Menu items.
     */
    private void initializeAdminMenu() {
        addItem(createCustomItem(Material.NAME_TAG, "Edit Display Name"),
                slotMapping.get(SlotOption.EDIT_DISPLAY_NAME));
        addItem(createCustomItem(Material.PAPER, "Edit Game Type"),
                slotMapping.get(SlotOption.EDIT_GAME_TYPE));
        addItem(createCustomItem(Material.CLOCK, "Edit Timer"),
                slotMapping.get(SlotOption.EDIT_TIMER));
        addItem(createCustomItem(Material.RED_STAINED_GLASS_PANE, "Edit Animation Message"),
                slotMapping.get(SlotOption.EDIT_ANIMATION_MESSAGE));

        // addItem(createCustomItem(Material.CHEST, "Use Vault"), slotMapping.get(SlotOption.USE_VAULT));
        // addItem(createCustomItem(Material.GOLD_INGOT, "Edit Currency"), slotMapping.get(SlotOption.EDIT_CURRENCY));

        addItem(createCustomItem(Material.COMPASS, "Move"),
                slotMapping.get(SlotOption.MOVE_DEALER));
        addItem(createCustomItem(Material.BARRIER, "Delete"),
                slotMapping.get(SlotOption.DELETE_DEALER));
    }

    /**
     * Returns whether the player is currently editing something else (rename, timer, etc.).
     */
    public static boolean isPlayerOccupied(UUID playerId) {
        Villager villager = nameEditMode.get(playerId);
        return (villager != null)
            || (timerEditMode.get(playerId) != null)
            || (amsgEditMode.get(playerId) != null)
            || (moveMode.get(playerId) != null);
    }
    
    public static List<String> playerOccupations(UUID playerId) {
        List<String> occupations = new ArrayList<>();
    
        if (nameEditMode.get(playerId) != null) {
            occupations.add("display name");
        }
        if (timerEditMode.get(playerId) != null) {
            occupations.add("timer");
        }
        if (amsgEditMode.get(playerId) != null) {
            occupations.add("animation message");
        }
        if (moveMode.get(playerId) != null) {
            occupations.add("location");
        }
    
        return occupations;
    }
    public static List<Villager> getOccupiedVillagers(UUID playerId) {
        List<Villager> villagers = new ArrayList<>();
    
        Villager villager;
    
        if ((villager = nameEditMode.get(playerId)) != null) {
            villagers.add(villager);
        }
        if ((villager = timerEditMode.get(playerId)) != null) {
            villagers.add(villager);
        }
        if ((villager = amsgEditMode.get(playerId)) != null) {
            villagers.add(villager);
        }
        if ((villager = moveMode.get(playerId)) != null) {
            villagers.add(villager);
        }
    
        return villagers;
    }
    

    /**
     * Handle inventory clicks for AdminInventory.
     */
    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true); // Prevent unintended item movement

        UUID playerId = player.getUniqueId();
        if (clickAllowed.getOrDefault(playerId, true)) {
            // Throttle clicking slightly to prevent spam
            clickAllowed.put(playerId, false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L);

            SlotOption option = getKeyByValue(slotMapping, slot);
            if (option != null) {
                switch (option) {
                    case EDIT_DISPLAY_NAME:
                        handleEditDealerName(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case EDIT_GAME_TYPE:
                        handleSelectGameType(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case MOVE_DEALER:
                        handleMoveDealer(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case DELETE_DEALER:
                        handleDeleteDealer(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case EDIT_CURRENCY:
                        handleEditCurrency(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case USE_VAULT:
                        handleUseVault(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case EDIT_TIMER:
                        handleEditTimer(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    case EDIT_ANIMATION_MESSAGE:
                        handleAnimationMessage(player);
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        break;
                    default:
                        player.sendMessage("§cInvalid option selected.");
                        break;
                }
            }
        } else {
            player.sendMessage("§cPlease wait before clicking again!");
        }
    }

    // ----- Option handlers -----
    private void handleEditTimer(Player player) {
        UUID playerId = player.getUniqueId();
        List<String> occupations = AdminInventory.playerOccupations(playerId);

        if (!occupations.isEmpty()) {
            for (String occupation : occupations) {
                denyAction(player, "Please finish editing " + occupation + " for " + DealerVillager.getInternalName(dealer));
            }
            return;
        }
        

        localVillager.put(playerId, dealer);
        timerEditMode.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aType the new timer in chat.");
    }

    private void handleAnimationMessage(Player player) {
        UUID playerId = player.getUniqueId();
        List<String> occupations = AdminInventory.playerOccupations(playerId);

        if (!occupations.isEmpty()) {
            for (String occupation : occupations) {
                denyAction(player, "Please finish editing " + occupation + " for " + DealerVillager.getInternalName(dealer));
            }
            return;
        }
        

        localVillager.put(playerId, dealer);
        amsgEditMode.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aType the new animation message in chat.");
    }

    private void handleEditDealerName(Player player) {
        UUID playerId = player.getUniqueId();
        List<String> occupations = AdminInventory.playerOccupations(playerId);

        if (!occupations.isEmpty()) {
            for (String occupation : occupations) {
                denyAction(player, "Please finish editing " + occupation + " for " + DealerVillager.getInternalName(dealer));
            }
            return;
        }
        

        nameEditMode.put(playerId, dealer);
        localVillager.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aType the new dealer name in chat.");
    }

    private void handleSelectGameType(Player player) {
        // Open the Game Options Inventory
        GameOptionsInventory inventory = new GameOptionsInventory(plugin, dealer);
        player.openInventory(inventory.getInventory());
    }

    private void handleMoveDealer(Player player) {
        UUID playerId = player.getUniqueId();

        List<String> occupations = AdminInventory.playerOccupations(playerId);

        if (!occupations.isEmpty()) {
            for (String occupation : occupations) {
                denyAction(player, "Please finish editing " + occupation + " for " + DealerVillager.getInternalName(dealer));
            }
            return;
        }
        

        moveMode.put(playerId, dealer);
        localVillager.put(playerId, dealer);
        player.closeInventory();
        player.sendMessage("§aClick a block to move the dealer.");
    }

    private void handleDeleteDealer(Player player) {
        int slot = slotMapping.get(SlotOption.DELETE_DEALER);
        Inventory playerInventory = getInventory();
        ItemStack deleteItem = playerInventory.getItem(slot);

        if (deleteItem != null && deleteItem.getType() == Material.BARRIER) {
            // Open confirmation inventory
            ConfirmInventory confirmInventory = new ConfirmInventory(
                    dealerId,
                    "Are you sure?",
                    (uuid) -> {
                        // Confirm action
                        player.closeInventory();
                        UUID pid = player.getUniqueId();

                        // Remove editing references
                        nameEditMode.remove(pid);
                        amsgEditMode.remove(pid);
                        timerEditMode.remove(pid);
                        moveMode.remove(pid);
                        localVillager.remove(pid);
                        adminInventories.remove(pid);

                        // Delete this AdminInventory
                        // Overridden delete() below calls cleanup() 
                        delete();

                        // Execute your "delete" command
                        Bukkit.dispatchCommand(player, "ncc delete " + DealerVillager.getInternalName(dealer));
                    },
                    (uuid) -> {
                        // Cancel action: re-open the AdminInventory
                        AdminInventory adminInventory = new AdminInventory(
                                dealerId, 
                                player,
                                (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class));
                        player.openInventory(adminInventory.getInventory());
                    },
                    plugin
            );

            player.openInventory(confirmInventory.getInventory());
        }
    }

    private void handleEditCurrency(Player player) {
        player.sendMessage("§aEditing currency...");
        // Implement currency editing logic here
    }

    private void handleUseVault(Player player) {
        player.sendMessage("§aUsing vault...");
        // Implement vault usage logic here
    }

    // ----- Event handlers -----

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (adminInventories.get(playerId) == null){
            cleanup();
            return;
        }

        // Editing dealer name
        if (nameEditMode.get(playerId) != null) {
            event.setCancelled(true);
            String newName = event.getMessage().trim();
            if (newName.isEmpty()) {
                denyAction(player, "Invalid name. Try again.");
                return;
            }
            if (dealer != null) {
                DealerVillager.setName(dealer, newName);
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".display-name", newName);
                plugin.saveConfig();
                dealer.setCustomNameVisible(true);
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aDealer name updated to: " + newName);
            } else {
                player.sendMessage("§cCould not find dealer.");
            }

                
            cleanup();

        }
        // Editing dealer timer
        else if (timerEditMode.get(playerId) != null) {
            event.setCancelled(true);
            String newTimer = event.getMessage().trim();

            if (newTimer.isEmpty() || !newTimer.matches("\\d+") || Integer.parseInt(newTimer) <= 0) {
                denyAction(player, "Invalid input. Please enter a positive number.");
                return;
            }
            if (dealer != null) {
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".timer", Integer.parseInt(newTimer));
                plugin.saveConfig();
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aDealer timer updated to: " + newTimer);
            } else {
                player.sendMessage("§cCould not find dealer.");
            }

                
            cleanup();
        }
        // Editing dealer animation message
        else if (amsgEditMode.get(playerId) != null) {
            event.setCancelled(true);
            String newAmsg = event.getMessage().trim();

            if (newAmsg.isEmpty()) {
                denyAction(player, "Invalid input.");
                return;
            }
            if (dealer != null) {
                String internalName = DealerVillager.getInternalName(dealer);
                plugin.getConfig().set("dealers." + internalName + ".animation-message", newAmsg);
                plugin.saveConfig();
                plugin.reloadDealerVillager(dealer);
                if(SoundHelper.getSoundSafely("entity.villager.work_cartographer")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.MASTER,1.0f, 1.0f);
                player.sendMessage("§aDealer animation message updated to: " + newAmsg);
            } else {
                player.sendMessage("§cCould not find dealer.");
            }

                
            cleanup();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (adminInventories.get(playerId) == null){
            cleanup();
            return;
        }

        if (moveMode.get(playerId) != null) {

            if (event.getClickedBlock() != null) {
                Location newLocation = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);

                if (dealer != null) {
                    dealer.teleport(newLocation);

                    // Save new location to your dealers.yaml
                    File dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");
                    if (!dealersFile.getParentFile().exists()) {
                        dealersFile.getParentFile().mkdirs();
                    }
                    FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);
                    var chunk = dealer.getLocation().getChunk();

                    String path = "dealers." + DealerVillager.getInternalName(dealer);
                    dealersConfig.set(path + ".chunkX", chunk.getX());
                    dealersConfig.set(path + ".chunkZ", chunk.getZ());
                    // Optionally store world name if needed
                    // dealersConfig.set(path + ".world", dealer.getWorld().getName());

                    try {
                        dealersConfig.save(dealersFile);
                    } catch (IOException e) {
                        plugin.getLogger().severe("Failed to save dealer location to " + dealersFile.getPath());
                        e.printStackTrace();
                    }
                    plugin.saveConfig();
                    if(SoundHelper.getSoundSafely("item.chorus_fruit.teleport")!=null)player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.MASTER,1.0f, 1.0f); 
                    player.sendMessage("§aDealer moved to new location.");
                } else {
                    player.sendMessage("§cCould not find dealer.");
                }

                cleanup();
            }
            event.setCancelled(true);
                
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID pid = player.getUniqueId();
        if (moveMode.get(pid) != null) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot break blocks while moving the dealer.");
        }
    }

    /**
     * Utility method to retrieve a SlotOption by the mapped slot number.
     */
    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null; 
    }

  
    /**
     * Provide user feedback if an action is disallowed.
     */
    private void denyAction(Player player, String message) {
        if (SoundHelper.getSoundSafely("entity.villager.no") != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        player.sendMessage("§c" + message);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if the player has an active AdminInventory
        if (adminInventories.containsKey(playerId)) {
            // Check if the player is currently editing something
            if (!isPlayerOccupied(playerId)) {
                // Remove the AdminInventory and clean up references
                AdminInventory inventory = adminInventories.remove(playerId);
                
                if (inventory != null) {
                    inventory.unregisterListener();
                    inventory.delete();
                }

                // Unregister this listener if no more AdminInventories exist
                if (adminInventories.isEmpty()) {
                    unregisterListener();
                }
            }
        }
    }

    private void unregisterListener() {
        InventoryCloseEvent.getHandlerList().unregister(this);
    }
    
    // --------------------------------------------------------------------------------
    // GC / Cleanup Logic
    // --------------------------------------------------------------------------------

    /**
     * Override delete() from DealerInventory to ensure we do a complete cleanup
     * of references for this AdminInventory.
     */
    @Override
    public void delete() {
        cleanup();
        super.delete(); 
        // super.delete() removes from DealerInventory.inventories & clears the Inventory
    }

    /**
     * Cleanup references to this AdminInventory and unregister event listeners.
     * Once called, there should be no lingering references preventing GC.
     */
    public void cleanup() {
        // 1) Unregister all event handlers for this instance
        HandlerList.unregisterAll(this);

        // 2) Remove from adminInventories
        adminInventories.remove(ownerId);

        // 3) Remove player references from the specialized maps
        moveMode.remove(ownerId);
        nameEditMode.remove(ownerId);
        timerEditMode.remove(ownerId);
        amsgEditMode.remove(ownerId);
        localVillager.remove(ownerId);
        clickAllowed.remove(ownerId);
    }

    public static void deleteAssociatedAdminInventories(Player player) {
        if (adminInventories.get(player.getUniqueId()) != null){
            adminInventories.remove(player.getUniqueId());
        }
    }

}
