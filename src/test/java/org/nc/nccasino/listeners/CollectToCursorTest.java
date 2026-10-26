package org.nc.nccasino.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A double-click in the player's own inventory collects every matching stack
 * across the whole view, the casino window included, so plain items in a menu
 * (the mob pickers' spawn eggs) could be pulled out and duplicated.
 */
class CollectToCursorTest {

    @Test
    void aDoubleClickCollectIsCancelledInEveryCasinoWindow() {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Player player = mock(Player.class);
        Inventory top = mock(Inventory.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(top);
        when(top.getHolder()).thenReturn(mock(DealerInventory.class));
        when(event.getAction()).thenReturn(InventoryAction.COLLECT_TO_CURSOR);

        new DealerInteractListener(mock(Nccasino.class)).onInventoryClick(event);

        verify(event).setCancelled(true);
    }
}
