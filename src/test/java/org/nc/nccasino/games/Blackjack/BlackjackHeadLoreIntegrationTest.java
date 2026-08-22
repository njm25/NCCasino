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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
            // Await the state this test actually needs instead of pinning it
            // to the old pre-shuffle transition duration. The 300-tick cap
            // is only deadlock protection; this assertion is about the
            // frame at activation, not how quickly activation occurs.
            for (int i = 0; i < 300 && !h.inventory.isGameActiveForTest(); i++) {
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

    /**
     * Regression test for a real bug: Phase 2 (dealing C into the active
     * hand) calls {@code updatePlayerHead} right after mutating the hand,
     * but Phase 3 (dealing D into the sibling hand) didn't -- the sibling
     * hand's own lore line stayed stale (missing D) until something
     * unrelated happened to repaint it later (e.g. standing on the other
     * hand). Overrides the harness's normal placeholder-stripping
     * localization stub, just for this test, so the actual "value" text
     * (which embeds the real card list) is observable.
     */
    @Test
    void siblingHandLoreReflectsDTheInstantItLandsNotOnlyAfterSomethingElseRepaints() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            // Mockito unrolls the target method's own Object... varargs, so
            // getArguments() here is [viewer, key, ...individual placeholder
            // elements], not [viewer, key, Object[]] -- and the other
            // (Player, String) overload (used elsewhere, e.g. BlackjackView's
            // own constructor) can also match this same stub, so this stays
            // defensive about argument count too.
            when(h.plugin.getLocalization().text(any(org.bukkit.entity.Player.class), anyString(), any(Object[].class)))
                .thenAnswer(inv -> {
                    Object[] args = inv.getArguments();
                    if (args.length > 2) {
                        return java.util.Arrays.toString(java.util.Arrays.copyOfRange(args, 2, args.length));
                    }
                    return args.length > 1 ? String.valueOf(args[1]) : "";
                });

            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.EIGHT)); // A
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN)); // dealer up
            stack.add(new Card(Suit.CLUBS, Rank.EIGHT));  // B
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN)); // dealer hole
            stack.add(new Card(Suit.DIAMONDS, Rank.TWO)); // C (original hand's replacement)
            stack.add(new Card(Suit.DIAMONDS, Rank.NINE)); // D (sibling hand's replacement) -- rank chosen to be unambiguous in the lore text
            stack.addAll(flatStack(Rank.TWO, 40));
            h.inventory.stackDeckForTest(stack);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 15.0);
            h.inventory.beginStartTransitionForTest();
            h.advanceToActionableTurn(1, 800);

            h.click(alice, BlackjackSlotLayout.ACTION_SPLIT_SLOT);

            // Advance one tick at a time until D has actually landed
            // (rendered) at its own slot -- the instant Phase 3 fires --
            // and check the lore right then, before anything else (like
            // standing) would have a chance to incidentally repaint it.
            int slotTempD = BlackjackSlotLayout.playerCardSlot(BlackjackSlotLayout.SEAT_SLOTS[0], 4);
            for (int i = 0; i < 200 && h.inventory.getOrCreateView(alice).getItem(slotTempD).getType() != org.bukkit.Material.RED_STAINED_GLASS_PANE; i++) {
                h.scheduler.advance(1);
            }
            assertEquals(org.bukkit.Material.RED_STAINED_GLASS_PANE, h.inventory.getOrCreateView(alice).getItem(slotTempD).getType(), "test setup: D must have actually landed");

            List<String> lore = headLore(h, alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            assertEquals(2, lore.size(), "one line per hand, was: " + lore);
            String siblingLine = lore.get(1);
            assertTrue(siblingLine.contains("9"), "the sibling hand's own lore line must already reflect D (a 9) the instant it lands, was: " + siblingLine);
        }
    }
}
