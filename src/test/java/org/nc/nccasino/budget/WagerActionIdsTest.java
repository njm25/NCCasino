package org.nc.nccasino.budget;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure tests for {@link WagerActionIds} -- no Bukkit server, only mocked
 * {@link Player}/{@link InventoryClickEvent} shapes.
 */
class WagerActionIdsTest {

    private Player player(UUID id, int ticksLived) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getTicksLived()).thenReturn(ticksLived);
        return player;
    }

    private InventoryClickEvent event(int rawSlot, int slot, ClickType click, InventoryAction action, int hotbar) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getSlot()).thenReturn(slot);
        when(event.getClick()).thenReturn(click);
        when(event.getAction()).thenReturn(action);
        when(event.getHotbarButton()).thenReturn(hotbar);
        return event;
    }

    @Test
    void theSameEventObjectAlwaysReproducesTheSameId() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId, 100);
        InventoryClickEvent event = event(10, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);

        String first = WagerActionIds.inventoryClick("session-1", player, event, "target", BigDecimal.TEN);
        String second = WagerActionIds.inventoryClick("session-1", player, event, "target", BigDecimal.TEN);

        assertEquals(first, second, "replaying the same event with identical characteristics must reproduce the same id");
    }

    @Test
    void aDifferentEventObjectWithIdenticalCharacteristicsInTheSameTickGetsADifferentId() {
        // Bukkit can process more than one packet from the same client in a
        // single tick, so two genuinely separate physical clicks can share
        // every stubbed characteristic (slot, click type, tick, amount) and
        // still must not collide -- this is exactly the "two legitimate
        // wagers of the same denomination" case.
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId, 100);
        InventoryClickEvent firstEvent = event(10, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);
        InventoryClickEvent secondEvent = event(10, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);

        String first = WagerActionIds.inventoryClick("session-1", player, firstEvent, "target", BigDecimal.TEN);
        String second = WagerActionIds.inventoryClick("session-1", player, secondEvent, "target", BigDecimal.TEN);

        assertNotEquals(first, second, "two distinct physical clicks must never collide even with identical characteristics");
    }

    @Test
    void changingTheTableSessionChangesTheId() {
        Player player = player(UUID.randomUUID(), 100);
        InventoryClickEvent event = event(10, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);

        String first = WagerActionIds.inventoryClick("session-1", player, event, "target", BigDecimal.TEN);
        String second = WagerActionIds.inventoryClick("session-2", player, event, "target", BigDecimal.TEN);

        assertNotEquals(first, second);
    }

    @Test
    void changingThePlayerChangesTheId() {
        InventoryClickEvent event = event(10, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);
        Player alice = player(UUID.randomUUID(), 100);
        Player bob = player(UUID.randomUUID(), 100);

        String first = WagerActionIds.inventoryClick("session-1", alice, event, "target", BigDecimal.TEN);
        String second = WagerActionIds.inventoryClick("session-1", bob, event, "target", BigDecimal.TEN);

        assertNotEquals(first, second);
    }

    @Test
    void changingTheTickChangesTheId() {
        UUID playerId = UUID.randomUUID();
        InventoryClickEvent event = event(10, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);

        String first = WagerActionIds.inventoryClick(
            "session-1", player(playerId, 100), event, "target", BigDecimal.TEN);
        String second = WagerActionIds.inventoryClick(
            "session-1", player(playerId, 101), event, "target", BigDecimal.TEN);

        assertNotEquals(first, second);
    }

    @Test
    void changingTheTargetChangesTheId() {
        Player player = player(UUID.randomUUID(), 100);
        InventoryClickEvent event = event(10, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);

        String first = WagerActionIds.inventoryClick("session-1", player, event, "target-a", BigDecimal.TEN);
        String second = WagerActionIds.inventoryClick("session-1", player, event, "target-b", BigDecimal.TEN);

        assertNotEquals(first, second);
    }

    @Test
    void changingTheAmountChangesTheId() {
        Player player = player(UUID.randomUUID(), 100);
        InventoryClickEvent event = event(10, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);

        String first = WagerActionIds.inventoryClick("session-1", player, event, "target", BigDecimal.TEN);
        String second = WagerActionIds.inventoryClick("session-1", player, event, "target", BigDecimal.valueOf(11));

        assertNotEquals(first, second);
    }

    @Test
    void changingClickSemanticsChangesTheId() {
        Player player = player(UUID.randomUUID(), 100);
        InventoryClickEvent left = event(10, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);
        InventoryClickEvent right = event(10, 10, ClickType.RIGHT, InventoryAction.PLACE_ALL, -1);
        InventoryClickEvent differentHotbar = event(10, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, 3);
        InventoryClickEvent differentSlot = event(11, 10, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);

        String base = WagerActionIds.inventoryClick("session-1", player, left, "target", BigDecimal.TEN);
        assertNotEquals(base, WagerActionIds.inventoryClick("session-1", player, right, "target", BigDecimal.TEN));
        assertNotEquals(base, WagerActionIds.inventoryClick("session-1", player, differentHotbar, "target", BigDecimal.TEN));
        assertNotEquals(base, WagerActionIds.inventoryClick("session-1", player, differentSlot, "target", BigDecimal.TEN));
    }
}
