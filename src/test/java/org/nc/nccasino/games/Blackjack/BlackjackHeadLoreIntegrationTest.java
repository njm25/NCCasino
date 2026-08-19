package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level coverage for the head lore's one-line-per-hand shape
 * (before any card lands, once dealt, and after a split). The test
 * harness's localization mock doesn't substitute {@code {value}}
 * placeholders (see {@code BlackjackControllerTestSupport}'s {@code
 * localization.text} stub), so exact "cards-total" text content is instead
 * covered at the pure-function level by {@code BlackjackRulesTest}; this
 * class only pins down how many lore lines actually render.
 */
class BlackjackHeadLoreIntegrationTest {

    private static List<String> headLore(BlackjackControllerTestSupport.Harness h, Player viewer, int seatSlot) {
        ItemStack head = h.inventory.getOrCreateView(viewer).getItem(seatSlot);
        ItemMeta meta = head.getItemMeta();
        return meta == null || meta.getLore() == null ? List.of() : meta.getLore();
    }

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    @Test
    void headLoreShowsNoLineAtAllBeforeAnyCardHasLanded() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.TWO, 40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();

            // The instant activateGame() flips gameActive, before any card
            // has actually landed (dealInitialCards schedules flights with
            // real delay) -- the head must show no card-value line at all,
            // never a placeholder "0".
            for (int i = 0; i < 60 && !h.inventory.isGameActiveForTest(); i++) {
                h.scheduler.advance(1);
            }
            assertTrue(h.inventory.isGameActiveForTest(), "test setup must actually reach gameActive");

            List<String> lore = headLore(h, alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            assertTrue(lore.isEmpty(), "no card-value line must show before any card has actually landed, was: " + lore);
        }
    }

    @Test
    void headLoreShowsCardsAndTotalOnceDealt() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.KING));  // Alice card 0
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN)); // dealer up
            stack.add(new Card(Suit.CLUBS, Rank.ACE));    // Alice card 1
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN)); // dealer hole
            stack.addAll(flatStack(Rank.TWO, 40));
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();
            for (int i = 0; i < 300 && h.inventory.activeHandCardCountForTest(alice.getUniqueId()) < 2; i++) {
                h.scheduler.advance(1);
            }

            List<String> lore = headLore(h, alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            assertEquals(1, lore.size(), "an unsplit hand must render exactly one card-value line, was: " + lore);
        }
    }

    @Test
    void headLoreShowsOneLinePerHandAfterASplit() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.EIGHT)); // A
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN)); // dealer up
            stack.add(new Card(Suit.CLUBS, Rank.EIGHT));  // B
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN)); // dealer hole
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO)); // C (original hand's replacement)
            stack.add(new Card(Suit.DIAMONDS, Rank.EIGHT)); // D (sibling hand's replacement)
            stack.addAll(flatStack(Rank.TWO, 40));
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);

            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);
            h.scheduler.advance(4 * BlackjackTiming.SPLIT_ANIMATION_STEP_TICKS + 5);

            List<String> lore = headLore(h, alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            assertEquals(2, lore.size(), "one line per hand once split, was: " + lore);
        }
    }
}
