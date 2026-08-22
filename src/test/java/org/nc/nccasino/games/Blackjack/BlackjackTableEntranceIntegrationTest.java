package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Controller-level regression coverage for BlackjackInventory#startTableEntrance's
 * own wiring -- the pure choreography (paths, timing, collisions) is covered by
 * BlackjackTableEntrancePlanTest; this covers the runtime lifecycle (generation
 * staleness, close/reopen races) that only exists once a real controller schedules it.
 */
class BlackjackTableEntranceIntegrationTest {

    @Test
    void occupiedPregameSeatIsDealtAsAPlayerHeadInsteadOfSkippingTheEntrance() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, seatSlot);

            UUID spectatorId = UUID.randomUUID();
            Player spectator = h.registerOnlinePlayer(spectatorId, "Spectator");
            h.inventory.getOrCreateView(spectator);
            h.inventory.onViewOpened(spectator);

            assertTrue(h.inventory.isTableEntranceActiveForTest(spectatorId),
                "an occupied lobby table must still play the entrance for a fresh viewer");
            assertEquals(Material.GREEN_STAINED_GLASS_PANE,
                h.inventory.getOrCreateView(spectator).getItem(seatSlot).getType(),
                "the occupied target must begin green before its head is dealt in");

            long landingTick = BlackjackTableEntrancePlan.build(
                    BlackjackTiming.TABLE_ENTRANCE_HOP_TICKS,
                    BlackjackTiming.TABLE_ENTRANCE_LAUNCH_STAGGER_TICKS
                ).stream()
                .filter(piece -> piece.getTargetSlot() == seatSlot)
                .findFirst().orElseThrow()
                .landingTick(BlackjackTiming.TABLE_ENTRANCE_HOP_TICKS);
            h.scheduler.advance(landingTick);

            ItemStack dealtHead = h.inventory.getOrCreateView(spectator).getItem(seatSlot);
            assertEquals(Material.PLAYER_HEAD, dealtHead.getType(),
                "the moving chair piece must be Alice's actual head when it lands");
        }
    }

    @Test
    void seatedPlayerReopeningDuringCountdownGetsEntranceButStartTransitionDoesNot() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            h.inventory.onViewClosed(alice, h.inventory.viewForTest(aliceId));
            h.inventory.getOrCreateView(alice);
            h.inventory.onViewOpened(alice);
            assertTrue(h.inventory.isTableEntranceActiveForTest(aliceId),
                "a seated player reopening during COUNTDOWN must see the full build");

            h.inventory.onViewClosed(alice, h.inventory.viewForTest(aliceId));
            h.inventory.beginStartTransitionForTest();
            h.inventory.getOrCreateView(alice);
            h.inventory.onViewOpened(alice);
            assertFalse(h.inventory.isTableEntranceActiveForTest(aliceId),
                "once the shared start transition begins, reopen must show live state immediately");
        }
    }

    @Test
    void closingAndReopeningFastDoesNotLetTheStaleFirstEntranceSkipTheSecondOnesAnimation() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            UUID id = UUID.randomUUID();
            Player alice = h.registerOnlinePlayer(id, "Alice");
            h.inventory.getOrCreateView(alice);
            h.inventory.onViewOpened(alice);

            // A couple of ticks in -- the first entrance is genuinely
            // mid-flight, with plenty of its own per-tick frame callbacks
            // still scheduled (they're never actually cancelled at the
            // Bukkit-scheduler level, only self-checked for staleness --
            // see startTableEntrance's own doc).
            h.scheduler.advance(2);
            assertTrue(h.inventory.isTableEntranceActiveForTest(id), "the first entrance must have started");

            // Close and immediately reopen -- exactly what a fast
            // double-click (or Esc + reopen) does. This starts a brand-new
            // entrance under a freshly-bumped animation generation; the
            // FIRST entrance's own still-pending per-tick callbacks
            // (scheduled before the close) haven't fired yet.
            h.inventory.onViewClosed(alice, h.inventory.viewForTest(id));
            h.inventory.getOrCreateView(alice);
            h.inventory.onViewOpened(alice);
            assertTrue(h.inventory.isTableEntranceActiveForTest(id), "reopening on a still-empty table must start a second entrance");

            // Advance well past several of the ORIGINAL entrance's own
            // callback ticks (now stale -- superseded by the second
            // entrance's fresh generation) but nowhere near either
            // entrance's own full duration. If a stale callback from the
            // first entrance incorrectly finished/aborted the second one
            // (the bug this test guards against), tableEntranceActive would
            // already be false here, well before the second entrance's
            // pieces have actually finished landing.
            h.scheduler.advance(4);
            assertTrue(h.inventory.isTableEntranceActiveForTest(id),
                "a stale callback from the superseded first entrance must never finish/abort the second, still-in-flight one");
        }
    }
}
