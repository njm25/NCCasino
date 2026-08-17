package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for two related fixes to the seat
 * row and its bet spots:
 *
 * <p>1. The brown bet-spot pane is a permanent part of the table's edge --
 * an empty seat's bet spot must never be cleared to the green background,
 * in any phase (this used to happen the moment the round went active, in
 * both {@code transitionBottomBarToActive} and {@code bootstrapView}).
 *
 * <p>2. Seat/bet-spot rendering is now viewer-relative: an already-seated
 * viewer sees "leave your own chair" instead of "click to sit" on every
 * other empty seat, and any viewer who doesn't own a given occupied seat
 * sees "{name}'s betting circle" (with a live wager subtitle) instead of
 * that seat's own click-to-bet/wager-lore item -- see
 * {@code buildEmptySeatChairItem}/{@code buildBetSpotItemForViewer}, the
 * single canonical builders every repaint path (bootstrap, initial table
 * setup, sit, leave, turn change, active-phase transition) now funnels
 * through.
 */
class BlackjackSeatAndBetSpotRenderingIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    private static Inventory viewInv(BlackjackControllerTestSupport.Harness h, Player p) {
        return h.inventory.viewForTest(p.getUniqueId()).getInventory();
    }

    private static String displayName(ItemStack item) {
        return item.getItemMeta().getDisplayName();
    }

    // ==================================================================
    // 1. The brown bet spot never disappears
    // ==================================================================

    @Test
    void emptySeatBetSpotStaysBrownAfterTheRoundGoesActive() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(300);
            assertTrue(h.inventory.isGameActiveForTest(), "setup: the round must actually go active");

            // Seats 1-4 were never occupied -- their bet spots must remain
            // the permanent brown edge, never the green background.
            for (int i = 1; i < BlackjackSlotLayout.SEAT_SLOTS.length; i++) {
                int betSpot = BlackjackSlotLayout.betSlipSlot(BlackjackSlotLayout.SEAT_SLOTS[i]);
                Material type = viewInv(h, alice).getItem(betSpot).getType();
                assertEquals(Material.BROWN_STAINED_GLASS_PANE, type, "empty seat " + i + "'s bet spot must stay brown, not go to background");
            }
        }
    }

    @Test
    void aLateSpectatorBootstrappingDuringActivePlaySeesEmptySeatBetSpotsAsBrown() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(300);
            assertTrue(h.inventory.isGameActiveForTest());

            // A spectator opens the table for the first time only now, mid
            // active play -- exercising bootstrapView's active branch.
            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            Inventory spectatorView = h.inventory.getOrCreateView(spectator);

            int emptyBetSpot = BlackjackSlotLayout.betSlipSlot(BlackjackSlotLayout.SEAT_SLOTS[1]);
            assertEquals(Material.BROWN_STAINED_GLASS_PANE, spectatorView.getItem(emptyBetSpot).getType(),
                "a freshly bootstrapped view during active play must still show brown, not background, for an empty seat");
        }
    }

    // ==================================================================
    // 2. Seat text is viewer-relative
    // ==================================================================

    @Test
    void aSeatedViewerSeesLeaveYourOwnChairOnEveryOtherEmptySeat() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);

            for (int i = 1; i < BlackjackSlotLayout.SEAT_SLOTS.length; i++) {
                ItemStack seatItem = viewInv(h, alice).getItem(BlackjackSlotLayout.SEAT_SLOTS[i]);
                assertEquals(Material.OAK_STAIRS, seatItem.getType());
                assertEquals("blackjack.seat-taken-elsewhere", displayName(seatItem),
                    "an already-seated viewer must see the redirect text on other empty seats, not plain click-to-sit");
            }
        }
    }

    @Test
    void anUnseatedSpectatorStillSeesPlainClickToSit() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            // Someone has to open the table first to paint the shared seat items.
            h.seatOnlinePlayer(UUID.randomUUID(), "Bystander");

            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            Inventory spectatorView = h.inventory.getOrCreateView(spectator);

            ItemStack seatItem = spectatorView.getItem(BlackjackSlotLayout.SEAT_SLOTS[0]);
            assertEquals(Material.OAK_STAIRS, seatItem.getType());
            assertEquals("blackjack.click-sit", displayName(seatItem), "an unseated viewer must see the normal invite, not the redirect");
        }
    }

    @Test
    void leavingRevertsThatViewersOwnSeatTextBackToPlainClickToSit() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, aliceSeat);
            assertEquals("blackjack.seat-taken-elsewhere", displayName(viewInv(h, alice).getItem(BlackjackSlotLayout.SEAT_SLOTS[1])));

            h.click(alice, aliceSeat); // own head -- leave the chair

            assertFalse(h.inventory.isSeatedForTest(alice.getUniqueId()));
            for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
                assertEquals("blackjack.click-sit", displayName(viewInv(h, alice).getItem(seatSlot)),
                    "once alice is unseated again, every seat (including her old one) must show the plain invite to her");
            }
        }
    }

    // ==================================================================
    // 3. Bet spots are viewer-relative once a seat is occupied
    // ==================================================================

    @Test
    void otherViewersSeeTheOccupantsBettingCircleInsteadOfClickToBet() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, aliceSeat);

            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");

            int aliceBetSpot = BlackjackSlotLayout.betSlipSlot(aliceSeat);
            assertEquals("blackjack.other-betting-circle", displayName(viewInv(h, bob).getItem(aliceBetSpot)),
                "bob must see alice's betting circle, not her click-to-bet control");
            assertEquals("blackjack.click-bet", displayName(viewInv(h, alice).getItem(aliceBetSpot)),
                "alice's own view of her own bet spot must be completely unaffected");
        }
    }

    @Test
    void bettingCircleShowsTheOccupantsLiveWagerOnceCommitted() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, aliceSeat);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");

            int aliceBetSpot = BlackjackSlotLayout.betSlipSlot(aliceSeat);
            ItemStack beforeCommit = viewInv(h, bob).getItem(aliceBetSpot);
            List<String> beforeLore = beforeCommit.getItemMeta().getLore();
            assertTrue(beforeLore == null || beforeLore.isEmpty(), "no wager lore before alice has actually committed anything");

            h.inventory.commitWagerForTest(alice, 15.0);

            ItemStack afterCommit = viewInv(h, bob).getItem(aliceBetSpot);
            assertEquals("blackjack.other-betting-circle", displayName(afterCommit), "the name must survive the wager update");
            List<String> afterLore = afterCommit.getItemMeta().getLore();
            assertTrue(afterLore != null && !afterLore.isEmpty() && afterLore.get(0).contains("blackjack.hand-wager-lore"),
                "the betting circle's own subtitle must reflect the live committed wager: " + afterLore);
        }
    }

    @Test
    void leavingClearsTheBettingCircleBackToBlankBrownForOtherViewers() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, aliceSeat);
            h.inventory.commitWagerForTest(alice, 15.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");

            int aliceBetSpot = BlackjackSlotLayout.betSlipSlot(aliceSeat);
            assertEquals("blackjack.other-betting-circle", displayName(viewInv(h, bob).getItem(aliceBetSpot)));

            h.click(alice, aliceSeat); // alice leaves

            ItemStack afterLeave = viewInv(h, bob).getItem(aliceBetSpot);
            assertEquals(Material.BROWN_STAINED_GLASS_PANE, afterLeave.getType());
            assertNotEquals("blackjack.other-betting-circle", displayName(afterLeave), "bob must never keep seeing alice's name after she's left");
            List<String> lore = afterLeave.getItemMeta().getLore();
            assertTrue(lore == null || lore.isEmpty(), "no stale wager lore may survive alice leaving");
        }
    }

    @Test
    void aSecondPlayerTakingTheVacatedSeatShowsTheirOwnNameNotTheFormerOccupants() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, seatSlot);
            h.inventory.commitWagerForTest(alice, 15.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(alice, seatSlot); // alice leaves

            Player carol = h.seatOnlinePlayer(UUID.randomUUID(), "Carol");
            h.click(carol, seatSlot); // carol takes the same now-empty seat

            int betSpot = BlackjackSlotLayout.betSlipSlot(seatSlot);
            ItemStack bobsView = viewInv(h, bob).getItem(betSpot);
            assertEquals("blackjack.other-betting-circle", displayName(bobsView));
            List<String> lore = bobsView.getItemMeta().getLore();
            assertTrue(lore == null || lore.isEmpty(), "carol hasn't wagered anything yet -- no stale wager lore from alice's old commit may survive");
        }
    }

    // ==================================================================
    // 4. Turn glow survives becoming per-viewer
    // ==================================================================

    @Test
    void otherViewersSeeTheCurrentTurnGlowOnTheActingPlayersBettingCircle() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, aliceSeat);
            h.inventory.commitWagerForTest(alice, 10.0);

            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            int bobSeat = BlackjackSlotLayout.SEAT_SLOTS[1];
            h.click(bob, bobSeat);
            h.inventory.commitWagerForTest(bob, 10.0);

            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(20, 40);
            assertEquals(aliceId, h.inventory.currentPlayerIdForTest(), "setup: alice (seat 0) acts first");

            int aliceBetSpot = BlackjackSlotLayout.betSlipSlot(aliceSeat);
            ItemStack fromBobsView = viewInv(h, bob).getItem(aliceBetSpot);
            assertEquals("blackjack.other-betting-circle", displayName(fromBobsView));
            assertTrue(Boolean.TRUE.equals(fromBobsView.getItemMeta().getEnchantmentGlintOverride()),
                "bob must see alice's betting circle glow while it's genuinely her turn");

            int bobBetSpot = BlackjackSlotLayout.betSlipSlot(bobSeat);
            ItemStack bobsOwnSpotFromAlicesView = viewInv(h, alice).getItem(bobBetSpot);
            assertFalse(Boolean.TRUE.equals(bobsOwnSpotFromAlicesView.getItemMeta().getEnchantmentGlintOverride()),
                "alice must not see bob's betting circle glow when it isn't his turn yet");
        }
    }
}
