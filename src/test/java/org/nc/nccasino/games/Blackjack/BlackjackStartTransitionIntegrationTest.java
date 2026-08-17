package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level regression coverage for the confirmed start-transition
 * seating deadlock (audit finding 1): a player who sat after
 * {@code beginStartTransition} snapshotted the seated players used to never
 * receive a door-conceal sequence, so {@code isReadyToDeal}'s old
 * live-{@code playerSeats} check could wait on them forever. Fixed with
 * three independent layers -- chair-click rejection during the transition,
 * a snapshot-based readiness check that only ever awaits players present
 * when the transition began, and a bounded readiness poll with a safe
 * refund-and-abort fallback -- each covered by its own test below using the
 * real {@code BlackjackInventory} via {@link BlackjackControllerTestSupport}.
 */
class BlackjackStartTransitionIntegrationTest {

    private static java.util.List<org.nc.nccasino.objects.Card> flatSevenStack(int count) {
        java.util.List<org.nc.nccasino.objects.Card> cards = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new org.nc.nccasino.objects.Card(org.nc.nccasino.objects.Suit.SPADES, org.nc.nccasino.objects.Rank.SEVEN));
        }
        return cards;
    }

    @Test
    void normalTransitionWithOriginalSeatedPlayersReachesActiveGame() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatSevenStack(40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            h.inventory.beginStartTransitionForTest();
            assertTrue(h.inventory.isStartTransitionActiveForTest());

            h.scheduler.advance(300);

            assertTrue(h.inventory.isGameActiveForTest(), "the round must actually deal once every legitimate condition is satisfied");
            assertFalse(h.inventory.isStartTransitionActiveForTest());
            assertEquals(1, h.inventory.playerSeatsSizeForTest());
        }
    }

    @Test
    void lateSeatingDuringStartTransitionIsRejectedAndNeverStallsTheGate() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatSevenStack(40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            h.inventory.beginStartTransitionForTest();

            // A second player attempts to sit mid-transition, before any
            // conceal/inspection animation has had a chance to finish --
            // exactly the reproduction window for the original deadlock.
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);

            assertFalse(h.inventory.isSeatedForTest(bob.getUniqueId()), "handleChairClick must reject seating once startTransitionActive is true");
            assertEquals(1, h.inventory.playerSeatsSizeForTest());

            h.scheduler.advance(300);

            assertTrue(h.inventory.isGameActiveForTest(), "the rejected late seat must never stall the readiness gate");
            assertEquals(1, h.inventory.playerSeatsSizeForTest(), "the rejected late player must still not be seated after the deal");
        }
    }

    @Test
    void originalParticipantLeavingDuringTransitionDoesNotStallTheGate() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatSevenStack(40));
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 10.0);

            h.inventory.beginStartTransitionForTest();

            // Bob was part of the original snapshot (he was seated before
            // the transition began) but leaves before his own door-conceal
            // has necessarily finished -- isReadyToDeal must stop awaiting
            // him rather than wait on a seat that no longer exists.
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]); // clicking one's own head slot leaves the seat

            assertFalse(h.inventory.isSeatedForTest(bob.getUniqueId()));

            h.scheduler.advance(300);

            assertTrue(h.inventory.isGameActiveForTest(), "a snapshotted participant leaving mid-transition must not stall the gate");
            assertEquals(1, h.inventory.playerSeatsSizeForTest());
            assertTrue(h.inventory.isSeatedForTest(alice.getUniqueId()));
        }
    }

    @Test
    void unsatisfiableReadinessSafelyAbortsInsteadOfPollingForever() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 25.0);

            h.inventory.beginStartTransitionForTest();
            // Simulates a hypothetical future bug: a seat present in the
            // readiness snapshot that was never scheduled a door-conceal
            // sequence at all, so it can never legitimately complete.
            // Registered online (rather than left dangling) so the eventual
            // abort-and-reset's own rendering can resolve them like any
            // other seated player.
            UUID stuckPlayerId = UUID.randomUUID();
            h.registerOnlinePlayer(stuckPlayerId, "Stuck");
            h.inventory.forceUnsatisfiableReadinessForTest(stuckPlayerId, BlackjackSlotLayout.SEAT_SLOTS[4]);

            // Comfortably past BlackjackTiming.START_TRANSITION_READINESS_MAX_POLLS
            // (120 polls * 5 ticks = 600 ticks) plus door-conceal/dealer-inspection.
            h.scheduler.advance(2000);

            assertFalse(h.inventory.isGameActiveForTest(), "an unsatisfiable readiness gate must never deal");
            assertFalse(h.inventory.isStartTransitionActiveForTest(), "the round must abort out of START_TRANSITION rather than hang in it forever");
            // resetGame() deliberately does not clear playerSeats (matches
            // its normal end-of-round behavior -- seated players stay
            // seated between rounds); the fix's job is refunding the
            // committed wager and abandoning the stuck round, not evicting anyone.
            assertEquals(2, h.inventory.playerSeatsSizeForTest());
            assertEquals(1, h.currencyProvider.depositAttempts.size(), "the legitimately seated player's wager must be refunded exactly once");
            assertEquals(0, java.math.BigDecimal.valueOf(25.0).compareTo(h.currencyProvider.depositAttempts.get(0)), "the exact wagered amount must be refunded");
        }
    }
}
