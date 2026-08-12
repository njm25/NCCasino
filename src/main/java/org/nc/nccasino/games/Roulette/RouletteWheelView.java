package org.nc.nccasino.games.Roulette;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;

/**
 * One player's localized view onto a shared {@link RouletteInventory}
 * round. Owns its own Bukkit inventory and title; forwards every action
 * back to the controller and holds no independent bets, winning number,
 * countdown, or scheduled task of its own. Mirrors the per-player pattern
 * already established by {@link BettingTable}.
 */
public class RouletteWheelView extends DealerInventory {
    private final UUID playerId;
    private final RouletteInventory controller;

    public RouletteWheelView(Player player, RouletteInventory controller, Nccasino plugin) {
        super(player.getUniqueId(), 54, plugin.getLocalization().text(player, "roulette.wheel-title"));
        this.playerId = player.getUniqueId();
        this.controller = controller;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) {
            return;
        }
        controller.handleViewClick(slot, player);
    }

    @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() != this) {
            return;
        }
        controller.onViewOpened((Player) event.getPlayer());
    }

    @EventHandler
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() != this) {
            return;
        }
        controller.onViewClosed((Player) event.getPlayer(), this);
    }

    @EventHandler
    public void handlePlayerQuit(PlayerQuitEvent event) {
        // PlayerQuitEvent is broadcast to every registered listener, so
        // every open view's instance receives it regardless of whose it
        // is -- without this guard, one player quitting would tear down
        // every other player's still-open view too.
        if (!event.getPlayer().getUniqueId().equals(playerId)) {
            return;
        }
        controller.onViewClosed(event.getPlayer(), this);
    }

    void cleanupListener() {
        HandlerList.unregisterAll(this);
    }
}
