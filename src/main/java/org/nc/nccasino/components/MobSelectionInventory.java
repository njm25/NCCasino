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
import java.util.UUID;
import java.util.ArrayList;
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
        PREVIOUS_PAGE,
        NEXT_PAGE
    }

    private final Map<SlotOption, Integer> slotMapping = Map.of(
        SlotOption.EXIT, 49,
        SlotOption.RETURN, 48,
        SlotOption.PREVIOUS_PAGE, 45,
        SlotOption.NEXT_PAGE, 53
    );

    static {
        for (Material material : Material.values()) {
            if (material.name().endsWith("_SPAWN_EGG")) {
                try {
                    String entityName = material.name().replace("_SPAWN_EGG", "");
                    EntityType entityType = EntityType.valueOf(entityName);
                    spawnEggToEntity.put(material, entityType);
                    spawnEggList.add(material);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public MobSelectionInventory(Player player, Nccasino plugin, UUID dealerId, Consumer<Player> returnToAdmin, String returnName) {
        super(player.getUniqueId(), 54, "Select Mob for " +  Dealer.getInternalName((Mob) player.getWorld()
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
        addItemAndLore(Material.BARRIER, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));

        // Pagination Buttons
        if (currentPage > 1) {
            addItemAndLore(Material.ARROW, 1, "Previous Page", slotMapping.get(SlotOption.PREVIOUS_PAGE));
        }
        if (currentPage < (int) Math.ceil(spawnEggList.size() / (double) PAGE_SIZE)) {
            addItemAndLore(Material.ARROW, 1, "Next Page", slotMapping.get(SlotOption.NEXT_PAGE));
        }
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !(event.getInventory().getHolder() instanceof MobSelectionInventory)) {
            return;
        }
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        // Handle page navigation
        if (clickedItem.getType() == Material.ARROW) {
            if (event.getSlot() == slotMapping.get(SlotOption.PREVIOUS_PAGE) && currentPage > 1) {
                currentPage--;
                updateMenu();
            } else if (event.getSlot() == slotMapping.get(SlotOption.NEXT_PAGE) &&
                    currentPage < (int) Math.ceil(spawnEggList.size() / (double) PAGE_SIZE)) {
                currentPage++;
                updateMenu();
            }
            return;
        }

        // Handle Exit
        if (clickedItem.getType() == Material.SPRUCE_DOOR) {
            player.closeInventory();
            return;
        }

        // Handle Return
        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            returnToAdmin.accept(player);
            return;
        }

        // Handle Mob Selection
        if (!spawnEggToEntity.containsKey(clickedItem.getType())) return;

        EntityType selectedType = spawnEggToEntity.get(clickedItem.getType());
        String internalName = Dealer.getInternalName(dealer);



        // Restore dealer settings
        restoreDealerSettings(internalName,selectedType);
        player.sendMessage(ChatColor.GREEN + "Dealer changed to " + ChatColor.YELLOW + formatEntityName(selectedType.name()));
        player.closeInventory();
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
        Bukkit.dispatchCommand(player, "ncc delete " + Dealer.getInternalName(dealer));
        
        Mob newDealer = Dealer.spawnDealer(plugin, loc, name, internalName, gameType, selectedType);
        Dealer.updateGameType(newDealer, gameType, timer, anmsg, name, chipSizes, currencyMaterial, currencyName);
        delete();
    }

    private static String formatEntityName(String entityName) {
        return entityName.replace("_", " ").toLowerCase();
    }



}
