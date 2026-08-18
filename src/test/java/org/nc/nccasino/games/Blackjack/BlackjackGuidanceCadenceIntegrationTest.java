package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.currency.ChipSlots;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the guidance-animation redesign:
 * chair/wager/action guidance must flash their whole applicable set
 * simultaneously -- glow together for 5 ticks, plain together for 5 ticks,
 * repeat -- never sweep one seat/chip/action at a time. See BlackjackChairGuidancePlan,
 * BlackjackWagerGuidancePlan, BlackjackActionGuidancePlan and their controller
 * call sites in BlackjackInventory (runChairGuidancePhase/runWagerGuidancePhase/
 * runActionGuidancePhase).
 */
class BlackjackGuidanceCadenceIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    /**
     * Whether {@code item}'s meta reflects either of production's two glow
     * mechanisms: {@code applyGlow}'s enchant-glint override (chair/wager/
     * action guidance's own flashing) or {@code createEnchantedItem}'s
     * literal enchant (the canonical "selected" glint, e.g. a chosen chip).
     */
    private static boolean isGlowing(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && (Boolean.TRUE.equals(meta.getEnchantmentGlintOverride()) || meta.hasEnchants());
    }

    // ==================================================================
    // Chair guidance
    // ==================================================================

    @Test
    void allEmptyChairsGlowInTheSameFrameThenGoPlainTogetherThenGlowAgain() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = h.registerOnlinePlayer(UUID.randomUUID(), "Alice");
            h.inventory.getOrCreateView(alice);
            h.inventory.onViewOpened(alice);
            h.scheduler.advance(BlackjackTiming.CHAIR_GUIDANCE_START_DELAY_TICKS - h.scheduler.currentTick());

            // Frame 1 (glow): every one of the 5 empty seats glows together.
            for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(seatSlot);
                assertTrue(isGlowing(item), "seat " + seatSlot + " must glow in the first frame");
                assertEquals("blackjack.chair-guidance-hint", item.getItemMeta().getDisplayName());
            }

            // Frame 2 (plain): the complete set goes plain together exactly 5 ticks later.
            h.scheduler.advance(BlackjackTiming.CHAIR_GUIDANCE_STEP_TICKS);
            for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(seatSlot);
                assertFalse(isGlowing(item), "seat " + seatSlot + " must be plain in the second frame");
                assertEquals("blackjack.click-sit", item.getItemMeta().getDisplayName());
            }

            // Frame 3 (glow again): another 5 ticks later.
            h.scheduler.advance(BlackjackTiming.CHAIR_GUIDANCE_STEP_TICKS);
            for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(seatSlot);
                assertTrue(isGlowing(item), "seat " + seatSlot + " must glow again in the third frame");
            }
        }
    }

    @Test
    void aSeatFilledBetweenPhasesIsExcludedFromTheNextRenderedFrame() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = h.registerOnlinePlayer(UUID.randomUUID(), "Alice");
            h.inventory.getOrCreateView(alice);
            h.inventory.onViewOpened(alice);
            h.scheduler.advance(BlackjackTiming.CHAIR_GUIDANCE_START_DELAY_TICKS - h.scheduler.currentTick());

            int filledSeat = BlackjackSlotLayout.SEAT_SLOTS[2];
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, filledSeat);

            h.scheduler.advance(BlackjackTiming.CHAIR_GUIDANCE_STEP_TICKS); // next phase re-derives the set
            for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
                ItemStack aliceView = h.inventory.getOrCreateView(alice).getItem(seatSlot);
                if (seatSlot == filledSeat) {
                    assertFalse(isGlowing(aliceView), "a now-filled seat must never appear in the guidance set");
                } else {
                    // still cycling (either glow or plain phase is fine here -- only the filled seat's exclusion matters)
                    assertNotNull(aliceView);
                }
            }
        }
    }

    @Test
    void chairGuidanceCompletionStopsFurtherFramesAndCancelsTheStaleChain() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            UUID id = UUID.randomUUID();
            Player alice = h.registerOnlinePlayer(id, "Alice");
            h.inventory.getOrCreateView(alice);
            h.inventory.onViewOpened(alice);
            h.scheduler.advance(BlackjackTiming.CHAIR_GUIDANCE_START_DELAY_TICKS - h.scheduler.currentTick());
            assertTrue(h.inventory.hasPrivateAnimationForTest(id));

            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]); // sits -- completes chair guidance, cancels the chain
            assertTrue(h.inventory.isChairGuidanceCompletedForTest(id));

            // Any already-queued phase callback from before the sit must no-op rather than repaint the (now door/wager-bar) view.
            h.scheduler.advance(BlackjackTiming.CHAIR_GUIDANCE_STEP_TICKS * 4L);
            for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
                if (seatSlot == BlackjackSlotLayout.SEAT_SLOTS[0]) {
                    continue;
                }
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(seatSlot);
                assertFalse(isGlowing(item), "chair guidance must never resume flashing after this viewer sat");
            }
        }
    }

    // ==================================================================
    // Wager guidance
    // ==================================================================

    private static Player seatedViewer(BlackjackControllerTestSupport.Harness h, UUID id, String name, int seatSlot) {
        Player player = h.seatOnlinePlayer(id, name);
        h.click(player, seatSlot);
        return player;
    }

    /**
     * Advances the scheduler exactly to the tick where wager guidance's
     * first glow phase renders -- the seat click triggers the door-reveal
     * animation first (BlackjackWagerRevealPlan.reveal), which must finish
     * before startWagerGuidance is even scheduled.
     */
    private static void advanceToWagerGuidanceGlow(BlackjackControllerTestSupport.Harness h) {
        h.scheduler.advance(BlackjackWagerRevealPlan.revealDurationTicks(BlackjackTiming.WAGER_REVEAL_STEP_TICKS));
    }

    @Test
    void allWagerControlsGlowInTheSameFrameThenGoPlainTogetherThenGlowAgain() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            UUID id = UUID.randomUUID();
            Player alice = seatedViewer(h, id, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);
            advanceToWagerGuidanceGlow(h);

            for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(slot);
                assertTrue(isGlowing(item), "chip slot " + slot + " must glow together with the rest of the set");
            }

            h.scheduler.advance(BlackjackTiming.WAGER_GUIDANCE_STEP_TICKS);
            for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(slot);
                assertFalse(isGlowing(item), "chip slot " + slot + " must go plain together with the rest of the set");
            }

            h.scheduler.advance(BlackjackTiming.WAGER_GUIDANCE_STEP_TICKS);
            for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(slot);
                assertTrue(isGlowing(item), "chip slot " + slot + " must glow again together with the rest of the set");
            }
        }
    }

    @Test
    void selectingAFixedWagerStopsTheLoopImmediately() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            UUID id = UUID.randomUUID();
            Player alice = seatedViewer(h, id, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);
            advanceToWagerGuidanceGlow(h);

            h.click(alice, ChipSlots.FIRST_SLOT);
            assertTrue(h.inventory.isWagerGuidanceCompletedForTest(id));

            h.scheduler.advance(BlackjackTiming.WAGER_GUIDANCE_STEP_TICKS * 4L);
            for (int slot = ChipSlots.FIRST_SLOT + 1; slot <= ChipSlots.LAST_SLOT; slot++) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(slot);
                assertFalse(isGlowing(item), "no un-selected chip may resume flashing once a fixed wager is chosen");
            }
        }
    }

    @Test
    void aQueuedPlainCallbackCannotRemoveTheCanonicalSelectedGlint() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            UUID id = UUID.randomUUID();
            Player alice = seatedViewer(h, id, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);
            advanceToWagerGuidanceGlow(h); // lands exactly at the first glow phase, with the next plain-phase callback already queued

            h.click(alice, ChipSlots.FIRST_SLOT); // selects, bumps the generation, cancels the chain

            // Advance well past when the already-queued plain-phase callback would have fired.
            h.scheduler.advance(BlackjackTiming.WAGER_GUIDANCE_STEP_TICKS * 3L);

            ItemStack selectedChip = h.inventory.getOrCreateView(alice).getItem(ChipSlots.FIRST_SLOT);
            assertTrue(isGlowing(selectedChip), "the canonical selected-wager glint must survive a stale queued plain frame");
        }
    }

    @Test
    void sameRoundLeaveAndReseatDoesNotRestartCompletedWagerGuidance() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = seatedViewer(h, id, "Alice", seatSlot);
            advanceToWagerGuidanceGlow(h);

            h.click(alice, ChipSlots.FIRST_SLOT); // completes wager guidance
            assertTrue(h.inventory.isWagerGuidanceCompletedForTest(id));

            h.click(alice, seatSlot); // leave
            h.click(alice, seatSlot); // reseat
            h.scheduler.advance(200);

            for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(slot);
                assertFalse(isGlowing(item), "wager guidance must not restart from a same-round leave/reseat");
            }
        }
    }

    @Test
    void otherViewersAreUnaffectedByOnePlayersPrivateWagerGuidance() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            UUID aliceId = UUID.randomUUID();
            UUID bobId = UUID.randomUUID();
            Player alice = seatedViewer(h, aliceId, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);
            Player bob = h.seatOnlinePlayer(bobId, "Bob"); // never sits -- still on chair guidance
            advanceToWagerGuidanceGlow(h);

            for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
                assertTrue(isGlowing(h.inventory.getOrCreateView(alice).getItem(slot)), "Alice's own wager guidance must be flashing");
                ItemStack bobsCopyOfAliceSlot = h.inventory.getOrCreateView(bob).getItem(slot);
                assertFalse(isGlowing(bobsCopyOfAliceSlot), "Bob (unseated) must never see Alice's private wager guidance");
            }
        }
    }

    // ==================================================================
    // Action guidance
    // ==================================================================

    @Test
    void allAvailableActionItemsGlowInTheSameFrameThenGoPlainTogether() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            // Fine-grained (1-tick) polling here specifically: this
            // assertion checks the very first rendered frame, which must be
            // glowing (see startActionGuidance's own glowPhase=true first
            // call) -- a coarser step could overshoot past that first frame
            // into a later "plain" phase of the 5-tick glow/plain cycle.
            h.advanceToActionableTurn(1, 800);

            assertNotNull(h.inventory.currentPlayerIdForTest(), "test setup must actually reach an actionable turn");

            boolean anyGlowing = false;
            for (int slot = BlackjackSlotLayout.ACTION_ROW_FIRST_SLOT; slot <= BlackjackSlotLayout.ACTION_ROW_LAST_SLOT; slot++) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(slot);
                if (item != null && isGlowing(item)) {
                    anyGlowing = true;
                }
            }
            assertTrue(anyGlowing, "at least the Hit/Stand action items must be glowing right after the decision opens");

            List<Integer> glowingSlotsFrame1 = glowingActionSlots(h, alice);
            assertFalse(glowingSlotsFrame1.isEmpty());

            h.scheduler.advance(BlackjackTiming.ACTION_GUIDANCE_STEP_TICKS);
            List<Integer> glowingSlotsFrame2 = glowingActionSlots(h, alice);
            assertTrue(glowingSlotsFrame2.isEmpty(), "the whole set must go plain together, not stay partially lit");

            h.scheduler.advance(BlackjackTiming.ACTION_GUIDANCE_STEP_TICKS);
            List<Integer> glowingSlotsFrame3 = glowingActionSlots(h, alice);
            assertEquals(glowingSlotsFrame1, glowingSlotsFrame3, "the same whole set glows again on the next on-phase");
        }
    }

    @Test
    void onlyTheActingPlayerSeesActionGuidance() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 10.0);

            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);

            UUID currentPlayerId = h.inventory.currentPlayerIdForTest();
            assertNotNull(currentPlayerId);
            Player nonCurrent = currentPlayerId.equals(alice.getUniqueId()) ? bob : alice;

            for (int slot = BlackjackSlotLayout.ACTION_ROW_FIRST_SLOT; slot <= BlackjackSlotLayout.ACTION_ROW_LAST_SLOT; slot++) {
                ItemStack item = h.inventory.getOrCreateView(nonCurrent).getItem(slot);
                assertFalse(isGlowing(item), "a non-acting viewer must never see another player's action guidance");
            }
        }
    }

    @Test
    void anInvalidActionClickDoesNotStartAFreshDecisionCycle() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertNotNull(h.inventory.currentPlayerIdForTest());

            int secondsBefore = h.inventory.turnTimerSecondsRemainingForTest();

            h.click(alice, BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT); // not an action slot -- easter egg, never a decision

            assertEquals(secondsBefore, h.inventory.turnTimerSecondsRemainingForTest(), "an invalid/non-action click must never extend or restart the decision deadline");
        }
    }

    private static List<Integer> glowingActionSlots(BlackjackControllerTestSupport.Harness h, Player viewer) {
        List<Integer> glowing = new ArrayList<>();
        for (int slot = BlackjackSlotLayout.ACTION_ROW_FIRST_SLOT; slot <= BlackjackSlotLayout.ACTION_ROW_LAST_SLOT; slot++) {
            ItemStack item = h.inventory.getOrCreateView(viewer).getItem(slot);
            if (item != null && isGlowing(item)) {
                glowing.add(slot);
            }
        }
        return glowing;
    }
}
