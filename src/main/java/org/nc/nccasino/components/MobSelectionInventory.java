package org.nc.nccasino.components;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.DealerInventory;

import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobSelectionInventory extends DealerInventory {
    private final UUID ownerId;
    private final Consumer<Player> returnToAdmin;
    private final Nccasino plugin;
    private final String returnName;
    private final Mob dealer;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 45;
    private static final Map<Material, EntityType> spawnEggToEntity = new HashMap<>();
    private static final List<Material> spawnEggList = new ArrayList<>();
    private UUID dealerId;
    private final Player player;
    private enum SlotOption {
        EXIT,
        RETURN,
        PAGE_TOGGLE
    }

    private final Map<SlotOption, Integer> slotMapping = Map.of(
        SlotOption.EXIT, 53,
        SlotOption.RETURN, 45,
        SlotOption.PAGE_TOGGLE, 49
    );

    static {
        for (Material material : Material.values()) {
            if (material.name().endsWith("_SPAWN_EGG")) {
                try {
                    String entityName = material.name().replace("_SPAWN_EGG", "");
                    EntityType entityType = EntityType.valueOf(entityName);
                    if (entityType != EntityType.ENDER_DRAGON&& entityType != EntityType.WITHER) {
                        spawnEggToEntity.put(material, entityType);
                        spawnEggList.add(material);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    
        // Sort based on natural word order instead of strict ASCII order
        spawnEggList.sort((m1, m2) -> {
            String name1 = formatEntityName(spawnEggToEntity.get(m1).name());
            String name2 = formatEntityName(spawnEggToEntity.get(m2).name());
            return name1.compareToIgnoreCase(name2);
        });
    }
    

    public MobSelectionInventory(Player player, Nccasino plugin, UUID dealerId, Consumer<Player> returnToAdmin, String returnName) {
        super(player.getUniqueId(), 54, "Select Model for " +  Dealer.getInternalName((Mob) player.getWorld()
        .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
        .filter(entity -> entity instanceof Mob)
        .map(entity -> (Mob) entity)
        .filter(v -> Dealer.isDealer(v)
                    && Dealer.getUniqueId(v).equals(dealerId))
        .findFirst().orElse(null)));
        this.dealerId = dealerId;
        this.dealer = (Mob) player.getWorld()
        .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
        .filter(entity -> entity instanceof Mob)
        .map(entity -> (Mob) entity)
        .filter(v -> Dealer.isDealer(v)
                     && Dealer.getUniqueId(v).equals(this.dealerId))
        .findFirst().orElse(null);
        this.ownerId = player.getUniqueId();
        this.plugin = plugin;
        this.player=player;
        this.returnName = returnName;
        this.returnToAdmin = returnToAdmin;
        registerListener();
        updateMenu();
    }

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void updateMenu() {
        inventory.clear();
        int startIndex = (currentPage - 1) * PAGE_SIZE;

        // Populate spawn eggs
        for (int i = 0; i < PAGE_SIZE && startIndex + i < spawnEggList.size(); i++) {
            Material material = spawnEggList.get(startIndex + i);
            EntityType entityType = spawnEggToEntity.get(material);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + formatEntityName(entityType.name()));
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }

        // Navigation & Utility Buttons
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));

        // Single Slot Pagination Button (Switches Between Next & Previous)
        if (currentPage > 1) {
            addItemAndLore(Material.ARROW, 1, "Previous Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        } else if (currentPage < (int) Math.ceil(spawnEggList.size() / (double) PAGE_SIZE)) {
            addItemAndLore(Material.ARROW, 1, "Next Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        }
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !(event.getInventory().getHolder() instanceof MobSelectionInventory)) {
            return;
        }

        event.setCancelled(true);
        
        SlotOption option = getKeyByValue(slotMapping, slot);
        if(option!=null){
            switch (option) {
                case PAGE_TOGGLE:
                    if (currentPage > 1) {
                        currentPage--;
                    } else if (currentPage < (int) Math.ceil(spawnEggList.size() / (double) PAGE_SIZE)) {
                        currentPage++;
                    }
                    updateMenu();
                    playDefaultSound(player);
                    return;
                case EXIT: 
                    player.closeInventory();
                    playDefaultSound(player);
                    return;
                case RETURN: 
                    returnToAdmin.accept(player);
                    playDefaultSound(player);
                    return;
            }
        }

        

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;


        if (!spawnEggToEntity.containsKey(clickedItem.getType())) return;

        EntityType selectedType = spawnEggToEntity.get(clickedItem.getType());
        String internalName = Dealer.getInternalName(dealer);


        playDefaultSound(player);
        // Restore dealer settings
        restoreDealerSettings(internalName,selectedType);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getInventory().getHolder() instanceof MobSelectionInventory) {
                if (event.getPlayer().getUniqueId().equals(ownerId)) {
                    HandlerList.unregisterAll(this);
                    super.delete();
                }
            }
        }, 4L);
    }

    private void restoreDealerSettings(String internalName, EntityType selectedType) {
        Location loc = dealer.getLocation();
        
        String name = plugin.getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
        String gameType = plugin.getConfig().getString("dealers." + internalName + ".game", "Menu");
        int timer = plugin.getConfig().getInt("dealers." + internalName + ".timer", 0);
        String anmsg = plugin.getConfig().getString("dealers." + internalName + ".animation-message");
        List<Integer> chipSizes = new ArrayList<>();

        ConfigurationSection chipSizeSection = plugin.getConfig().getConfigurationSection("dealers." + internalName + ".chip-sizes");
        if (chipSizeSection != null) {
            for (String key : chipSizeSection.getKeys(false)) {
                chipSizes.add(plugin.getConfig().getInt("dealers." + internalName + ".chip-sizes." + key));
            }
        }
        chipSizes.sort(Integer::compareTo);

        String currencyMaterial = plugin.getConfig().getString("dealers." + internalName + ".currency.material");
        String currencyName = plugin.getConfig().getString("dealers." + internalName + ".currency.name");
        //DealerInventory.unregisterAllListeners(dealer);

        //Dealer.removeDealer(dealer);
       // DealerInventory.unregisterAllListeners(dealer);
        // Remove dealer data from YAML
        
        //delete();
        ConfirmInventory confirmInventory = new ConfirmInventory(
            dealerId,
            "Reset config to default?",
            (uuid) -> {
                // Confirm action
                

                Bukkit.dispatchCommand(player, "ncc delete " + Dealer.getInternalName(dealer));
                
                plugin.saveDefaultDealerConfig(internalName);
                Dealer.spawnDealer(plugin, loc, name, internalName, gameType, selectedType);
                player.sendMessage(ChatColor.GREEN + "Dealer changed to " + ChatColor.YELLOW + formatEntityName(selectedType.name()));
                player.closeInventory();
                if (!plugin.getConfig().contains("dealers." + internalName + ".stand-on-17") && gameType.equals("Blackjack")) {
                    plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
                } else {
                    int hasStand17Config = plugin.getConfig().getInt("dealers." + internalName + ".stand-on-17");
                    if (hasStand17Config > 100 || hasStand17Config < 0) {
                        plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
                    }
                }

        
                this.delete();
            },
            (uuid) -> {
                // Dent action
                Bukkit.dispatchCommand(player, "ncc delete " + Dealer.getInternalName(dealer));
                Mob newDealer = Dealer.spawnDealer(plugin, loc, name, internalName, gameType, selectedType);
                Dealer.updateGameType(newDealer, gameType, timer, anmsg, name, chipSizes, currencyMaterial, currencyName);
                player.sendMessage(ChatColor.GREEN + "Dealer changed to " + ChatColor.YELLOW + formatEntityName(selectedType.name()));
                player.closeInventory();
                this.delete();
            },
            plugin
            );
        
        player.openInventory(confirmInventory.getInventory());
    
    }

private static String formatEntityName(String entityName) {
    return Arrays.stream(entityName.toLowerCase().replace("_", " ").split(" "))
                 .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                 .collect(Collectors.joining(" "));
}

}
