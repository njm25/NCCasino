package org.nc.nccasino.games;

import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.Nccasino;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DragonTable implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final UUID playerId;
    private final UUID dealerId;
    private final Villager dealer;
    private final Nccasino plugin;
    private final String internalName;
    private final DragonInventory dragonInventory;
    private final Map<String, Double> chipValues;

    public DragonTable(Player player, Villager dealer, Nccasino plugin, String internalName, DragonInventory dragonInventory) {
        this.playerId = player.getUniqueId();
        this.dealerId = DealerVillager.getUniqueId(dealer);
        this.dealer = dealer;
        this.plugin = plugin;
        this.internalName = internalName;
        this.dragonInventory = dragonInventory;
        this.inventory = Bukkit.createInventory(this, 54, "Mines");
        this.chipValues = new HashMap<>();
        loadChipValuesFromConfig();

        initializeTable();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadChipValuesFromConfig() {
        Nccasino nccasino = (Nccasino) plugin;
        for (int i = 1; i <= 5; i++) {
            String chipName = nccasino.getChipName(internalName, i);
            double chipValue = nccasino.getChipValue(internalName, i);
            this.chipValues.put(chipName, chipValue);
        }
    }

    private void initializeTable() {
        // Use the inherited method to create and add a custom item
        dragonInventory.addItem(dragonInventory.createCustomItem(Material.DRAGON_HEAD, "YAYY TESTING"), 0);
    }

    @EventHandler
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        Player player = (Player) event.getWhoClicked();
        event.setCancelled(true);

        int slot = event.getRawSlot();
        // Handle click logic
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
