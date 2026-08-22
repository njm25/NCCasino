package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controller-level check for what happens once every seated player has
 * clicked the door (a true, live forfeit) mid-round -- as opposed to
 * disconnecting/closing the inventory, which rides to result instead (see
 * {@code BlackjackRideToResultIntegrationTest}) and never empties {@code
 * playerSeats} on its own.
 */
class BlackjackAllPlayersForfeitIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    @Test
    void everyoneClickingTheDoorMidRoundEndsTheRoundWithTheNormalSweep() {
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
            h.advanceToActionableTurn(20, 60);
            assertTrue(h.inventory.isGameActiveForTest(), "test setup must actually reach an active round");
            assertTrue(h.inventory.isSeatedForTest(alice.getUniqueId()));
            assertTrue(h.inventory.isSeatedForTest(bob.getUniqueId()));

            // Alice forfeits first -- the round must still be going, waiting on Bob.
            h.click(alice, BlackjackSlotLayout.ACTIVE_EXIT_SLOT);
            assertFalse(h.inventory.isSeatedForTest(alice.getUniqueId()), "Alice's seat must be freed immediately by a real forfeit");
            assertTrue(h.inventory.isSeatedForTest(bob.getUniqueId()), "the round must still be live for the remaining player");

            // Bob -- the last one left -- forfeits too.
            h.click(bob, BlackjackSlotLayout.ACTIVE_EXIT_SLOT);
            assertFalse(h.inventory.isSeatedForTest(bob.getUniqueId()));

            // The round must end and the board sweep back to lobby, same as
            // any other round-ending path -- not silently hang or leave
            // stale state.
            for (int i = 0; i < 300 && h.inventory.isGameActiveForTest(); i++) {
                h.scheduler.advance(1);
            }
            assertFalse(h.inventory.isGameActiveForTest(), "the round must actually end once every player has forfeited");

            for (int i = 0; i < 300 && h.inventory.sharedAnimationPhaseForTest() == BlackjackFrame.Phase.ACTIVE; i++) {
                h.scheduler.advance(1);
            }
            assertTrue(h.inventory.sharedAnimationPhaseForTest() != BlackjackFrame.Phase.ACTIVE,
                "the board must actually sweep back to the lobby, not stay stuck mid-round");
        }
    }
}
