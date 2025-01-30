package org.nc.nccasino.components;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.DealerVillager;
import org.nc.nccasino.helpers.SoundHelper;

public class LinkedInventory extends DealerInventory {
    private final UUID ownerId;
    private final Consumer<UUID> ret;
    private UUID dealerId;
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player
    private Nccasino plugin;
    private String returnName;
    private Villager dealer;
    public static final Map<UUID, Villager> localLIVillager = new HashMap<>();
    public static final Map<UUID, LinkedInventory> LIInventories = new HashMap<>();
    private enum SlotOption {
       RETURN
    }
    private final Map<SlotOption, Integer> slotMapping = new HashMap<>() {{
        put(SlotOption.RETURN, 0);
    }};
    public LinkedInventory(UUID dealerId,Player player, String title, Consumer<UUID> ret, Nccasino plugin,String returnName,int size) {
        super(dealerId, size, title);
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
        this.ownerId = player.getUniqueId();  // Store the player's ID
        LIInventories.put(this.ownerId, this);

        initalizeMenu();
    }

    private void initalizeMenu(){
        addItem(createCustomItem(Material.MAGENTA_GLAZED_TERRACOTTA, "Return To "+returnName), slotMapping.get(SlotOption.RETURN));
    }


    public void executeReturn() {
        ret.accept(dealerId);
    }


    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
    }
    
    public void delete() {
        super.delete(); 
        // super.delete() removes from DealerInventory.inventories & clears the Inventory
    }

    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null; 
    }


}
