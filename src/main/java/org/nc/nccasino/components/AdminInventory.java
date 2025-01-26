package org.nc.nccasino.components;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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
import org.bukkit.event.Listener;
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

public class AdminInventory extends DealerInventory implements Listener {
    private UUID dealerId;
    private Villager dealer;
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player
    private Nccasino plugin;
    private static final Map<UUID, Boolean> moveMode = new HashMap<>(); // Track players in move mode
    private static final Map<UUID, Boolean> nameEditMode = new HashMap<>(); // Track players renaming dealer
    private static final Map<UUID, Boolean> timerEditMode = new HashMap<>(); // Track players renaming dealer
    private static final Map<UUID, Boolean> amsgEditMode = new HashMap<>(); // Track players renaming dealer
    public static final Map<UUID, AdminInventory> adminInventories = new HashMap<>();

    public static final Map<UUID, Villager> villagerMap = new HashMap<>();


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

    private final Map<SlotOption, Integer> slotMapping = new HashMap<>() {{
        put(SlotOption.EDIT_DISPLAY_NAME, 10);
        put(SlotOption.EDIT_GAME_TYPE, 12);
        put(SlotOption.EDIT_TIMER, 14);
        put(SlotOption.EDIT_ANIMATION_MESSAGE, 16);

        //put(SlotOption.USE_VAULT, 28);
        //put(SlotOption.EDIT_CURRENCY, 30);
        put(SlotOption.MOVE_DEALER, 30);
        put(SlotOption.DELETE_DEALER, 32);
    }};

    public AdminInventory(UUID dealerId, Player player, Nccasino plugin) {
        super(player.getUniqueId(), 54, "Admin Menu");
        this.dealerId = dealerId;
        this.dealer = DealerVillager.getVillagerFromId(dealerId);
        this.dealer =(Villager) player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
            .filter(entity -> entity instanceof Villager)
            .map(entity -> (Villager) entity)
            .filter(v -> DealerVillager.isDealerVillager(v) && DealerVillager.getUniqueId(v).equals(this.dealerId))
            .findFirst().orElse(null);
        this.plugin = plugin;
        registerListener();
        adminInventories.put(player.getUniqueId(), this);
        initializeAdminMenu();
    }

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    
    private void initializeAdminMenu() {
        addItem(createCustomItem(Material.NAME_TAG, "Edit Display Name"), slotMapping.get(SlotOption.EDIT_DISPLAY_NAME));
        addItem(createCustomItem(Material.PAPER, "Edit Game Type"), slotMapping.get(SlotOption.EDIT_GAME_TYPE));
        addItem(createCustomItem(Material.CLOCK, "Edit Timer"), slotMapping.get(SlotOption.EDIT_TIMER));
        addItem(createCustomItem(Material.RED_STAINED_GLASS_PANE, "Edit Animation Message"), slotMapping.get(SlotOption.EDIT_ANIMATION_MESSAGE));

        //addItem(createCustomItem(Material.CHEST, "Use Vault"), slotMapping.get(SlotOption.USE_VAULT));
        //addItem(createCustomItem(Material.GOLD_INGOT, "Edit Currency"), slotMapping.get(SlotOption.EDIT_CURRENCY));
        addItem(createCustomItem(Material.COMPASS, "Move"), slotMapping.get(SlotOption.MOVE_DEALER));
        addItem(createCustomItem(Material.BARRIER, "Delete"), slotMapping.get(SlotOption.DELETE_DEALER));

    }
    
    private boolean isPlayerOccupied(UUID playerId) {
        return nameEditMode.getOrDefault(playerId, false) ||
               timerEditMode.getOrDefault(playerId, false) ||
               amsgEditMode.getOrDefault(playerId, false) ||
               moveMode.getOrDefault(playerId, false);
    }
    

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true); // Prevent unintended item movement

        UUID playerId = player.getUniqueId();

        if (clickAllowed.getOrDefault(playerId, true)) {
            clickAllowed.put(playerId, false); // Prevent rapid clicking
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
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        handleEditTimer(player);
                        break;
                    case EDIT_ANIMATION_MESSAGE:
                        if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
                        handleAnimationMessage(player);
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

    private void handleEditTimer(Player player){
        UUID playerId = player.getUniqueId();
    

        // Temporarily mark player as occupied before checking

    // Run the check in the main thread
        if (isPlayerOccupied(playerId)) {
            if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            player.sendMessage("§cYou are already editing something. ");
            return;
        }
        villagerMap.put(playerId, dealer);  // Store dealer reference
        timerEditMode.put(playerId, true);
        player.closeInventory();
        player.sendMessage("§aType the new timer in chat.");
    }

    private void handleAnimationMessage(Player player){
        UUID playerId = player.getUniqueId();
    
        if (isPlayerOccupied(playerId)) {
            if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            player.sendMessage("§cYou are already editing something. ");
            return;
        }

       villagerMap.put(playerId, dealer);  // Store dealer reference
        amsgEditMode.put(playerId, true);
        player.closeInventory();
 
        player.sendMessage("§aType the new animation message in chat.");
    }

    private void handleEditDealerName(Player player) {
        UUID playerId = player.getUniqueId();
    
        if (isPlayerOccupied(playerId)) {
            if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            player.sendMessage("§cYou are already editing something. ");
            return;
        }
        nameEditMode.put(playerId, true);
        villagerMap.put(playerId, dealer);  // Store dealer reference
        player.closeInventory();

        player.sendMessage("§aType the new dealer name in chat.");
    }
    

    private void handleSelectGameType(Player player) {
        
        // Open the Game Options Inventory
        GameOptionsInventory inventory = new GameOptionsInventory((Nccasino)plugin, dealer);
        player.openInventory(inventory.getInventory());
      
    }

    private void handleMoveDealer(Player player) {
        UUID playerId = player.getUniqueId();
    
        if (isPlayerOccupied(playerId)) {
            if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
            player.sendMessage("§cYou are already editing something. ");
            return;
        }

        
        moveMode.put(playerId, true);
        villagerMap.put(playerId, dealer);  // Store dealer reference
        player.closeInventory();
        player.sendMessage("§aClick a block to move the dealer.");
    }

    private void handleDeleteDealer(Player player) {
        int slot = slotMapping.get(SlotOption.DELETE_DEALER);
        Inventory playerInventory = getInventory();
        ItemStack deleteItem = playerInventory.getItem(slot);
    
        if (deleteItem != null && deleteItem.getType() == Material.BARRIER) {
            // Open confirmation inventory directly
            ConfirmInventory confirmInventory = new ConfirmInventory(
                dealerId,
                "Are you sure?",
                (uuid) -> {
                    // Confirm action: close inventory, delete dealer, and execute command
                    player.closeInventory();
                    UUID playerId = player.getUniqueId();
                    
                    nameEditMode.remove(playerId); // Remove from edit mode
                    amsgEditMode.remove(playerId);
                    timerEditMode.remove(playerId);
                    moveMode.remove(playerId);
                    villagerMap.remove(playerId);
                    adminInventories.remove(playerId);
                    delete();
                            
                    Bukkit.dispatchCommand(player, "ncc delete " + DealerVillager.getInternalName(dealer));
                },
                (uuid) -> {
                    AdminInventory adminInventory = new AdminInventory(dealerId, player, (Nccasino) JavaPlugin.getProvidingPlugin(DealerVillager.class));
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

    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null; // Return null if the value is not found
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Villager dealer = villagerMap.get(playerId);

        if (nameEditMode.getOrDefault(playerId, false)) {
            event.setCancelled(true); // Prevent message from being broadcast

            String newName = event.getMessage().trim();
            if (newName.isEmpty()) {
                if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 

                player.sendMessage("§cInvalid name. Try again.");
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

            nameEditMode.remove(playerId); // Remove from edit mode
            villagerMap.remove(playerId);
            adminInventories.remove(playerId);
        }
        else if (timerEditMode.getOrDefault(playerId, false)) {
            event.setCancelled(true); // Prevent message from being broadcast

            String newTimer = event.getMessage().trim();
            if (newTimer.isEmpty() || !newTimer.matches("\\d+") || Integer.parseInt(newTimer) <= 0) {
                if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 

                player.sendMessage("§cInvalid input. Please enter a positive number.");
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

            timerEditMode.remove(playerId); // Remove from edit mode
            villagerMap.remove(playerId);
            adminInventories.remove(playerId);

        }
        else if (amsgEditMode.getOrDefault(playerId, false)) {
            event.setCancelled(true); // Prevent message from being broadcast

            String newAmsg = event.getMessage().trim();
            if (newAmsg.isEmpty()) {
                if(SoundHelper.getSoundSafely("entity.villager.no")!=null)player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,SoundCategory.MASTER, 1.0f, 1.0f); 
                player.sendMessage("§cInvalid input.");
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

            villagerMap.remove(playerId);
            amsgEditMode.remove(playerId); // Remove from edit mode
            adminInventories.remove(playerId);

        }
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        Villager dealer = villagerMap.get(playerId);
        if (moveMode.containsKey(playerId) && moveMode.get(playerId)) {
            if (event.getClickedBlock() != null) {
                Location newLocation = event.getClickedBlock().getLocation().add(0.5, 1, 0.5); // Center on block

                if (dealer != null) {
                    dealer.teleport(newLocation);   // Save dealer location and chunk data
                    File dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");

                    // Ensure the parent directory exists
                    if (!dealersFile.getParentFile().exists()) {
                        dealersFile.getParentFile().mkdirs();
                    }

                    // Load the custom configuration
                    FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);

                    // Set the dealer's data
                    var chunk = dealer.getLocation().getChunk();
                    String path = "dealers." + DealerVillager.getInternalName(dealer);
                    dealersConfig.set(path + ".chunkX", chunk.getX());
                    dealersConfig.set(path + ".chunkZ", chunk.getZ());
                            // Save the configuration to file
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

                moveMode.remove(playerId); 
                villagerMap.remove(playerId);
                adminInventories.remove(playerId);

            }
            event.setCancelled(true); 
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (moveMode.containsKey(player.getUniqueId()) && moveMode.get(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot break blocks while moving the dealer.");
        }
    }


}
