package org.nc.nccasino.components;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.JockeyNode;
import org.nc.nccasino.entities.JockeyManager;

import java.util.UUID;
import java.util.function.Consumer;

public class JockeyOptionsMenu extends Menu {
    private final JockeyNode jockey;
    private final Nccasino plugin;
    private final String returnName;

    public JockeyOptionsMenu(Player player, Nccasino plugin, JockeyNode jockey, String returnName, Consumer<Player> returnCallback) {
        super(player, plugin, jockey.getId(), "Jockey Options", 9, returnName, returnCallback);
        this.jockey = jockey;
        this.plugin = plugin;
        this.returnName = returnName;

        slotMapping.put(SlotOption.EXIT, 8);
        slotMapping.put(SlotOption.RETURN, 0);
        slotMapping.put(SlotOption.EDIT_DISPLAY_NAME, 2);
        slotMapping.put(SlotOption.MOB_SELECTION, 4);
        initializeMenu();
    }

    @Override
    protected void initializeMenu() {
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        addItemAndLore(Material.NAME_TAG, 1, "Edit Name", slotMapping.get(SlotOption.EDIT_DISPLAY_NAME), "Current: §a" + jockey.getCustomName());
        addItemAndLore(Material.SPAWNER, 1, "Change Mob Type", slotMapping.get(SlotOption.MOB_SELECTION), "Current: §a" + jockey.getMob().getType().name());
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        switch (option) {
            case EDIT_DISPLAY_NAME:
                handleEditName(player);
                break;
            case MOB_SELECTION:
                handleMobSelection(player);
                break;
        }
    }

    private void handleEditName(Player player) {
        player.closeInventory();
        player.sendMessage("§aType the new name for this jockey in chat.");
        // TODO: Implement chat listener for name editing
    }

    private void handleMobSelection(Player player) {
        // Get the root jockey manager from the dealer
        JockeyManager rootManager = new JockeyManager(jockey.getParent().getParent() == null ? 
            jockey.getParent().getMob() : jockey.getParent().getParent().getMob());
            
        JockeyMobMenu mobMenu = new JockeyMobMenu(
            player,
            plugin,
            rootManager,
            jockey,
            "Jockey Options",
            (p) -> {
                if (returnCallback != null) {
                    returnCallback.accept(player);
                }
            },
            false // Not adding as passenger when changing existing jockey
        );
        player.openInventory(mobMenu.getInventory());
    }

    @Override
    public void cleanup() {
        HandlerList.unregisterAll(this);
        this.delete();
    }
} 