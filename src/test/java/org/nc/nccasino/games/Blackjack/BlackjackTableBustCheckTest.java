package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

/**
 * Whether the dealer's own turn can be skipped must examine every wagered
 * hand across every seated player -- not just each player's single
 * currently-active hand, which after splitting is commonly their last hand
 * only. See the audited defect: a player who stood on 20 on their first
 * split hand and busted on their second must still have that first hand
 * settled against the dealer's play.
 */
class BlackjackTableBustCheckTest {

    private static Card card(Rank rank) {
        return new Card(Suit.SPADES, rank);
    }

    private static BlackjackHand handWith(double wager, Rank... ranks) {
        BlackjackHand hand = new BlackjackHand(wager);
        for (Rank rank : ranks) {
            hand.addCard(card(rank));
        }
        return hand;
    }

    @Test
    void trueWhenEveryHandEverywhereIsBusted() {
        BlackjackHand busted1 = handWith(10, Rank.TEN, Rank.KING, Rank.FIVE); // 25
        BlackjackHand busted2 = handWith(10, Rank.NINE, Rank.NINE, Rank.NINE); // 27
        assertTrue(BlackjackTableBustCheck.allHandsBusted(List.of(List.of(busted1), List.of(busted2))));
    }

    @Test
    void falseWhenAnEarlierSplitHandStoodButTheLastSplitHandBusted() {
        // The exact audited scenario: split hand 1 stands on 20, split hand
        // 2 busts -- the dealer must still play, hand 1 needs settling.
        BlackjackHand stood20 = handWith(10, Rank.TEN, Rank.KING); // 20, not done via bust
        BlackjackHand busted = handWith(10, Rank.TEN, Rank.KING, Rank.FIVE); // 25
        assertFalse(BlackjackTableBustCheck.allHandsBusted(List.of(List.of(stood20, busted))));
    }

    @Test
    void falseWhenAnEarlierSplitHandBustedButTheLastSplitHandStood() {
        BlackjackHand busted = handWith(10, Rank.TEN, Rank.KING, Rank.FIVE); // 25
        BlackjackHand stood19 = handWith(10, Rank.TEN, Rank.NINE); // 19
        assertFalse(BlackjackTableBustCheck.allHandsBusted(List.of(List.of(busted, stood19))));
    }

    @Test
    void trueWhenEveryHandAcrossMultiplePlayersIsBusted() {
        BlackjackHand playerABusted = handWith(10, Rank.TEN, Rank.KING, Rank.FIVE);
        BlackjackHand playerBHand1Busted = handWith(10, Rank.NINE, Rank.NINE, Rank.NINE);
        BlackjackHand playerBHand2Busted = handWith(10, Rank.EIGHT, Rank.EIGHT, Rank.SEVEN);
        assertTrue(BlackjackTableBustCheck.allHandsBusted(List.of(
            List.of(playerABusted),
            List.of(playerBHand1Busted, playerBHand2Busted)
        )));
    }

    @Test
    void naturalBlackjackCountsAsSurviving() {
        BlackjackHand natural = handWith(10, Rank.ACE, Rank.KING);
        assertFalse(BlackjackTableBustCheck.allHandsBusted(List.of(List.of(natural))));
    }

    @Test
    void twentyOneCountsAsSurviving() {
        BlackjackHand twentyOne = handWith(10, Rank.SEVEN, Rank.SEVEN, Rank.SEVEN);
        assertFalse(BlackjackTableBustCheck.allHandsBusted(List.of(List.of(twentyOne))));
    }

    @Test
    void anOrdinaryStoodHandUnder21CountsAsSurviving() {
        BlackjackHand stood = handWith(10, Rank.TEN, Rank.SIX); // 16, stood
        assertFalse(BlackjackTableBustCheck.allHandsBusted(List.of(List.of(stood))));
    }

    @Test
    void unwageredHandsAreIgnored() {
        BlackjackHand neverDealt = new BlackjackHand(0); // wager 0 -- never actually played this round
        assertTrue(BlackjackTableBustCheck.allHandsBusted(List.of(List.of(neverDealt))));
    }

    @Test
    void nullOrEmptyCollectionIsVacuouslyAllBusted() {
        assertTrue(BlackjackTableBustCheck.allHandsBusted(null));
        assertTrue(BlackjackTableBustCheck.allHandsBusted(List.of()));
    }

    @Test
    void nullHandsListForASeatedPlayerIsSkippedSafely() {
        List<BlackjackHand> nullList = null;
        assertTrue(BlackjackTableBustCheck.allHandsBusted(java.util.Arrays.asList(nullList)));
    }

    @Test
    void realisticMultiPlayerMapDrivenScenario() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Map<UUID, List<BlackjackHand>> playerHands = Map.of(
            alice, List.of(handWith(10, Rank.TEN, Rank.KING)), // 20, stood -- survives
            bob, List.of(handWith(10, Rank.TEN, Rank.KING, Rank.FIVE)) // busted
        );
        boolean allBusted = BlackjackTableBustCheck.allHandsBusted(
            List.of(alice, bob).stream().map(playerHands::get).toList()
        );
        assertFalse(allBusted, "Alice's stood hand must keep the dealer's turn alive");
    }
}
