package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Regression coverage for the race between the dealer's own walk-down
 * animation and the shuffle it hands off to (see
 * BlackjackInventory#startDealerInspection's own doc): the dealer's per-step
 * MOVE callback sets dealerHeadSlot to its final value several ticks before
 * the "safety net" completion callback (which used to be the only place
 * shuffleInProgress got set) ever runs, and the readiness-check poll cadence
 * happened to land exactly on that gap tick -- letting activateGame() fire
 * before the shuffle had even started. A coarse scheduler.advance(200) never
 * catches this; only a tick-by-tick walk through the exact window does.
 */
class BlackjackShuffleSequenceIntegrationTest {

    private static List<Material> materials(Inventory inventory) {
        List<Material> result = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            result.add(inventory.getItem(slot) == null ? Material.AIR : inventory.getItem(slot).getType());
        }
        return result;
    }

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    @Test
    void shufflePlaysAndCutsOffBatWhooshesAcrossTheWholeCardStream() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            // The shared harness suppresses sounds by default. Isolate this
            // test from registry/preferences behavior so it proves the
            // shuffle controller itself schedules the exact audible burst
            // and each matching hard cutoff.
            try (MockedStatic<SoundHelper> sounds = mockStatic(SoundHelper.class)) {
                Sound batTakeoff = mock(Sound.class);
                sounds.when(() -> SoundHelper.getSoundSafely("entity.bat.takeoff", alice))
                    .thenReturn(batTakeoff);

                h.inventory.beginStartTransitionForTest();
                h.scheduler.advance(100);

                List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(
                    BlackjackTiming.SHUFFLE_CARD_COUNT,
                    BlackjackTiming.SHUFFLE_HOP_TICKS,
                    BlackjackTiming.SHUFFLE_CARD_LAUNCH_STAGGER_TICKS
                );
                long totalShuffleTicks =
                    (long) (BlackjackShuffleAnimationPlan.DECK_TO_CENTER_PATH.size() - 1) * BlackjackTiming.SHUFFLE_HOP_TICKS
                    + BlackjackTiming.SHUFFLE_START_PAUSE_TICKS
                    + BlackjackShuffleAnimationPlan.totalDurationTicks(cards, BlackjackTiming.SHUFFLE_HOP_TICKS)
                    + 1L
                    + (long) (BlackjackShuffleAnimationPlan.CENTER_TO_DECK_PATH.size() - 1) * BlackjackTiming.SHUFFLE_HOP_TICKS;
                int expectedBeats = (int) ((totalShuffleTicks - BlackjackTiming.SHUFFLE_WHOOSH_CUTOFF_TICKS)
                    / BlackjackTiming.SHUFFLE_WHOOSH_BEAT_TICKS) + 1;
                verify(alice, times(expectedBeats)).playSound(
                    any(Location.class),
                    eq(batTakeoff),
                    any(SoundCategory.class),
                    eq(BlackjackTiming.SHUFFLE_WHOOSH_VOLUME),
                    anyFloat()
                );
                verify(alice, times(expectedBeats)).stopSound(
                    eq(batTakeoff),
                    any(SoundCategory.class)
                );
            }
        }
    }

    @Test
    void dealingNeverStartsWhileTheShuffleIsStillInFlight() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            h.inventory.beginStartTransitionForTest();

            // Tick-by-tick through the window where the dealer's own
            // walk-down has already landed but the shuffle it hands off to
            // is still going -- this is exactly the gap a coarse advance
            // would step right over.
            for (int i = 0; i < 30; i++) {
                h.scheduler.advance(1);
                if (h.inventory.isGameActiveForTest()) {
                    fail("dealing started at tick " + (i + 1) + ", while the shuffle should still be in flight");
                }
            }
        }
    }

    @Test
    void dealingEventuallyStartsOnceTheShuffleGenuinelyFinishes() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            h.inventory.beginStartTransitionForTest();
            h.scheduler.advance(200);

            assertTrue(h.inventory.isGameActiveForTest(), "dealing must eventually start once the shuffle finishes");
            assertTrue(h.inventory.activeHandCardCountForTest(id) > 0, "the committed player must actually receive a hand");
        }
    }

    @Test
    void temporaryShuffleDeckSpotIsClearedWhenDeckReturnsHome() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            h.inventory.beginStartTransitionForTest();
            for (int i = 0; i < 120 && !h.inventory.isGameActiveForTest(); i++) {
                h.scheduler.advance(1);
            }

            assertTrue(h.inventory.isGameActiveForTest(), "setup: shuffle must finish and dealing must begin");
            assertEquals(BlackjackSlotLayout.DECK_HOME_SLOT, h.inventory.dealerDeckTokenSlotForTest(),
                "the canonical deck token must be back at its ordinary home before dealing");
            assertEquals(Material.GREEN_STAINED_GLASS_PANE,
                h.inventory.getOrCreateView(alice).getItem(BlackjackShuffleAnimationPlan.CENTER_SLOT).getType(),
                "the temporary center shuffle spot must be plain felt, never a stuck duplicate deck");
        }
    }

    @Test
    void reopeningMidShuffleReconstructsTheExactCurrentSharedFrameAndClosedBar() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 40));
            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            int seatSlot = BlackjackSlotLayout.SEAT_SLOTS[0];
            h.click(alice, seatSlot);
            h.inventory.commitWagerForTest(alice, 10.0);
            h.inventory.beginStartTransitionForTest();

            Inventory original = h.inventory.getOrCreateView(alice);
            for (int tick = 0; tick < 100; tick++) {
                h.scheduler.advance(1);
                long hidden = materials(original).stream().filter(type -> type == Material.WHITE_STAINED_GLASS_PANE).count();
                if (h.inventory.dealerDeckTokenSlotForTest() == BlackjackShuffleAnimationPlan.CENTER_SLOT && hidden >= 2) {
                    break;
                }
            }
            assertEquals(BlackjackShuffleAnimationPlan.CENTER_SLOT, h.inventory.dealerDeckTokenSlotForTest(),
                "setup must stop during the card-stream phase");
            List<Material> frameBeforeClose = materials(original);

            h.inventory.onViewClosed(alice, h.inventory.viewForTest(id));
            Inventory reopened = h.inventory.getOrCreateView(alice);

            assertEquals(frameBeforeClose, materials(reopened),
                "a same-tick reopen must reproduce every shared shuffle cell, not wait for a future frame");
            assertEquals(Material.PLAYER_HEAD, reopened.getItem(seatSlot).getType(),
                "the retained wagered seat must remain a player head");
            assertEquals(Material.SPRUCE_DOOR, reopened.getItem(BlackjackSlotLayout.UNSEATED_EXIT_SLOT).getType(),
                "the start-transition wager bar must remain at its closed endpoint");
            assertEquals(Material.GREEN_STAINED_GLASS_PANE, reopened.getItem(BlackjackSlotLayout.ACTION_ROW_FIRST_SLOT).getType(),
                "reopening must not resurrect seated wager controls over the shuffle");
        }
    }
}
