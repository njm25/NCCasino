package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.currency.ChipSlots;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the "Currently Selected" wager
 * subtitle: the localized {@code blackjack.currently-selected} lore line
 * that accompanies the canonical selected-wager glint (see
 * {@link BlackjackWagerSelectionLifecycleIntegrationTest} for the
 * selection-persistence lifecycle itself, and
 * {@link BlackjackGuidanceCadenceIntegrationTest} for the flash/glow
 * animations this presentation must coexist with). This suite only proves
 * the lore itself is applied, moved, and never duplicated -- not the
 * selection lifecycle those other suites already cover.
 */
class BlackjackSelectedWagerLoreIntegrationTest {

    private static final String SELECTED_LORE = "blackjack.currently-selected";

    private static BlackjackControllerTestSupport.Harness newTable() {
        return BlackjackControllerTestSupport.newHarness();
    }

    private static Player seatAndOpen(BlackjackControllerTestSupport.Harness h, UUID id, String name, int seatSlot) {
        Player player = h.seatOnlinePlayer(id, name);
        h.click(player, seatSlot);
        return player;
    }

    private static boolean isGlowing(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && (Boolean.TRUE.equals(meta.getEnchantmentGlintOverride()) || meta.hasEnchants());
    }

    private static boolean hasSelectedLore(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta == null ? null : meta.getLore();
        return lore != null && lore.contains(SELECTED_LORE);
    }

    private static int countSelectedLoreLines(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta == null ? null : meta.getLore();
        if (lore == null) {
            return 0;
        }
        int count = 0;
        for (String line : lore) {
            if (SELECTED_LORE.equals(line)) {
                count++;
            }
        }
        return count;
    }

    @Test
    void selectingAFixedDenominationAddsTheLoreAndGlintOnlyToThatChip() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            Player alice = seatAndOpen(h, UUID.randomUUID(), "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);

            h.click(alice, ChipSlots.FIRST_SLOT + 2); // 10.0

            for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(slot);
                if (slot == ChipSlots.FIRST_SLOT + 2) {
                    assertTrue(isGlowing(item), "the selected chip must keep its canonical glint");
                    assertTrue(hasSelectedLore(item), "the selected chip must carry the localized subtitle");
                } else {
                    assertFalse(isGlowing(item), "an unselected chip must never glow");
                    assertFalse(hasSelectedLore(item), "an unselected chip must never carry the subtitle");
                }
            }
            ItemStack allIn = h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.ALL_IN_SLOT);
            assertFalse(hasSelectedLore(allIn), "All In must not carry the subtitle while a fixed chip is selected");
        }
    }

    @Test
    void selectingAnotherDenominationMovesTheLoreAndGlintToTheNewChip() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            Player alice = seatAndOpen(h, UUID.randomUUID(), "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);

            h.click(alice, ChipSlots.FIRST_SLOT);
            h.click(alice, ChipSlots.FIRST_SLOT + 3); // 25.0

            ItemStack former = h.inventory.getOrCreateView(alice).getItem(ChipSlots.FIRST_SLOT);
            ItemStack current = h.inventory.getOrCreateView(alice).getItem(ChipSlots.FIRST_SLOT + 3);
            assertFalse(isGlowing(former), "the former selection must lose its glint");
            assertFalse(hasSelectedLore(former), "the former selection must lose its subtitle");
            assertTrue(isGlowing(current), "the new selection must carry the glint");
            assertTrue(hasSelectedLore(current), "the new selection must carry the subtitle");
        }
    }

    @Test
    void selectingAllInBehavesIdenticallyToAFixedChip() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(500);
            Player alice = seatAndOpen(h, UUID.randomUUID(), "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);

            h.click(alice, ChipSlots.FIRST_SLOT); // select a fixed chip first
            h.click(alice, BlackjackSlotLayout.ALL_IN_SLOT); // then All In

            ItemStack formerChip = h.inventory.getOrCreateView(alice).getItem(ChipSlots.FIRST_SLOT);
            ItemStack allIn = h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.ALL_IN_SLOT);
            assertFalse(hasSelectedLore(formerChip), "the former fixed chip must lose the subtitle to All In");
            assertTrue(isGlowing(allIn), "All In must carry the canonical glint once selected");
            assertTrue(hasSelectedLore(allIn), "All In must carry the localized subtitle once selected");
        }
    }

    @Test
    void switchingFromAllInBackToAFixedChipMovesThePresentationBack() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(500);
            Player alice = seatAndOpen(h, UUID.randomUUID(), "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);

            h.click(alice, BlackjackSlotLayout.ALL_IN_SLOT);
            h.click(alice, ChipSlots.FIRST_SLOT + 1); // back to a fixed 5.0 chip

            ItemStack allIn = h.inventory.getOrCreateView(alice).getItem(BlackjackSlotLayout.ALL_IN_SLOT);
            ItemStack chip = h.inventory.getOrCreateView(alice).getItem(ChipSlots.FIRST_SLOT + 1);
            assertFalse(isGlowing(allIn), "All In must lose its glint once a fixed chip is chosen instead");
            assertFalse(hasSelectedLore(allIn), "All In must lose its subtitle once a fixed chip is chosen instead");
            assertTrue(isGlowing(chip));
            assertTrue(hasSelectedLore(chip));
        }
    }

    @Test
    void repeatedBootstrapRepaintDoesNotDuplicateTheLore() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            Player alice = seatAndOpen(h, id, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.click(alice, ChipSlots.FIRST_SLOT);

            // Force a genuine close/reopen -- the exact bootstrapView path a
            // late or returning viewer takes, distinct from the incremental
            // refreshWagerControlsForPlayer path a click already exercised.
            BlackjackView view = h.inventory.viewForTest(id);
            h.inventory.onViewClosed(alice, view);
            h.inventory.getOrCreateView(alice);

            ItemStack afterReopen = h.inventory.getOrCreateView(alice).getItem(ChipSlots.FIRST_SLOT);
            assertEquals(1, countSelectedLoreLines(afterReopen), "reopening the inventory must not duplicate the subtitle");

            // A second full-table repaint (initializeGameMenu via resetGame) on top of that.
            h.inventory.resetGameForTest();
            ItemStack afterReset = h.inventory.getOrCreateView(alice).getItem(ChipSlots.FIRST_SLOT);
            assertEquals(1, countSelectedLoreLines(afterReset), "a bootstrap-style full repaint must not duplicate the subtitle");
        }
    }

    @Test
    void presentationSurvivesCommitFailureRejectionUndoAndReset() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            h.currencyProvider.setBalance(1000);
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = seatAndOpen(h, UUID.randomUUID(), "Alice", seatSlot);
            int betSpot = BlackjackSlotLayout.betSlipSlot(seatSlot);

            h.click(alice, ChipSlots.FIRST_SLOT + 1); // 5.0
            assertPresentSelectedChip(h, alice); // baseline

            h.click(alice, betSpot); // successful commit
            assertPresentSelectedChip(h, alice);

            h.currencyProvider.setNextWithdrawSucceeds(false);
            h.click(alice, betSpot); // transaction failure
            assertPresentSelectedChip(h, alice);
            h.currencyProvider.setNextWithdrawSucceeds(true);

            h.click(alice, BlackjackSlotLayout.UNDO_LAST_SLOT); // undo last
            assertPresentSelectedChip(h, alice);

            h.click(alice, betSpot);
            h.click(alice, BlackjackSlotLayout.UNDO_ALL_SLOT); // undo all
            assertPresentSelectedChip(h, alice);

            h.inventory.beginStartTransitionForTest();
            h.inventory.resetGameForTest(); // betting-to-deal transition + normal round reset
            assertPresentSelectedChip(h, alice);
        }
    }

    private static void assertPresentSelectedChip(BlackjackControllerTestSupport.Harness h, Player alice) {
        ItemStack chip = h.inventory.getOrCreateView(alice).getItem(ChipSlots.FIRST_SLOT + 1);
        assertTrue(isGlowing(chip), "the selected chip's glint must survive");
        assertTrue(hasSelectedLore(chip), "the selected chip's subtitle must survive");
    }

    @Test
    void leavingTheChairClearsThePresentationAndReseatingStartsWithNone() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = seatAndOpen(h, id, "Alice", seatSlot);
            h.click(alice, ChipSlots.FIRST_SLOT);

            h.click(alice, seatSlot); // leave

            for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(slot);
                assertFalse(hasSelectedLore(item), "no chip may keep the subtitle after leaving the chair");
                assertFalse(isGlowing(item), "no chip may keep the glint after leaving the chair");
            }

            h.click(alice, seatSlot); // reseat

            for (int slot = ChipSlots.FIRST_SLOT; slot <= ChipSlots.LAST_SLOT; slot++) {
                ItemStack item = h.inventory.getOrCreateView(alice).getItem(slot);
                assertFalse(hasSelectedLore(item), "a freshly reseated player must start with no selected subtitle");
                assertFalse(isGlowing(item), "a freshly reseated player must start with no selected glint");
            }
        }
    }

    @Test
    void aStaleWagerGuidancePlainCallbackCannotRemoveTheSelectedLore() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID id = UUID.randomUUID();
            Player alice = seatAndOpen(h, id, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.scheduler.advance(BlackjackWagerRevealPlan.revealDurationTicks(BlackjackTiming.WAGER_REVEAL_STEP_TICKS));

            h.click(alice, ChipSlots.FIRST_SLOT); // selects, bumps the generation, cancels the guidance chain

            // Advance well past when the already-queued plain-phase guidance callback would have fired.
            h.scheduler.advance(BlackjackTiming.WAGER_GUIDANCE_STEP_TICKS * 3L);

            ItemStack selectedChip = h.inventory.getOrCreateView(alice).getItem(ChipSlots.FIRST_SLOT);
            assertTrue(isGlowing(selectedChip), "the canonical selected glint must survive a stale queued plain frame");
            assertTrue(hasSelectedLore(selectedChip), "the canonical selected subtitle must survive a stale queued plain frame");
        }
    }

    @Test
    void otherViewersNeverSeeAnotherPlayersSelectedPresentation() {
        try (BlackjackControllerTestSupport.Harness h = newTable()) {
            UUID aliceId = UUID.randomUUID();
            UUID bobId = UUID.randomUUID();
            Player alice = seatAndOpen(h, aliceId, "Alice", BlackjackSlotLayout.SEAT_SLOTS[0]);
            Player bob = seatAndOpen(h, bobId, "Bob", BlackjackSlotLayout.SEAT_SLOTS[1]);

            h.click(alice, ChipSlots.FIRST_SLOT + 2); // Alice selects 10.0

            ItemStack aliceOwnView = h.inventory.getOrCreateView(alice).getItem(ChipSlots.FIRST_SLOT + 2);
            assertTrue(hasSelectedLore(aliceOwnView), "Alice must see her own selected subtitle");

            ItemStack bobsCopyOfAliceSlot = h.inventory.getOrCreateView(bob).getItem(ChipSlots.FIRST_SLOT + 2);
            assertFalse(hasSelectedLore(bobsCopyOfAliceSlot), "Bob must never see Alice's private selected subtitle");
            assertFalse(isGlowing(bobsCopyOfAliceSlot), "Bob must never see Alice's private selected glint");
        }
    }
}
