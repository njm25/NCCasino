package org.nc.nccasino.components;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;

public class ConfirmInventory extends DealerInventory implements Listener {
    private final Consumer<UUID> confirm;
    private final Consumer<UUID> cancel;
    private UUID dealerId;
    private final Map<UUID, Boolean> clickAllowed = new HashMap<>(); // Track click state per player
    private Nccasino plugin;

    public ConfirmInventory(UUID dealerId, String title, Consumer<UUID> confirm, Consumer<UUID> cancel, Nccasino plugin) {
        super(dealerId, 9, title);
        this.confirm = confirm;
        this.cancel = cancel;
        this.dealerId = dealerId;
        this.plugin = plugin;

        initalizeMenu();
    }

    private void initalizeMenu(){
        
        addItem(createCustomItem(Material.GREEN_STAINED_GLASS_PANE, "Yes"), 0);
        addItem(createCustomItem(Material.RED_STAINED_GLASS_PANE, "No"), 8);
    }

    public void executeConfirm() {
        confirm.accept(dealerId);
    }

    public void executeCancel() {
        cancel.accept(dealerId);
    }


    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true); // Prevent unintended item movement

        UUID playerId = player.getUniqueId();

        if (clickAllowed.getOrDefault(playerId, true)) {
            clickAllowed.put(playerId, false); // Prevent rapid clicking
            Bukkit.getScheduler().runTaskLater(plugin, () -> clickAllowed.put(playerId, true), 5L);


            switch (slot) {
                case 0:
                    executeConfirm();
                    break;
                case 8:
                    executeCancel();
                    break;
                default:
                    player.sendMessage("§cInvalid option selected.");
                    break;
            }
        } else {
            player.sendMessage("§cPlease wait before clicking again!");
        }
    }

}
