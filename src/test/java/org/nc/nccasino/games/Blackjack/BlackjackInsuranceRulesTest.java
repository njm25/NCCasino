package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

/**
 * Covers the pure insurance decision/payout math extracted per the table
 * redesign plan's "Pure round-logic classes" section. The Bukkit-glue side
 * (timeout scheduling, UI rendering, click routing in BlackjackInventory)
 * follows the established pattern of not being directly unit-tested.
 */
class BlackjackInsuranceRulesTest {

    private static final Card ACE_SPADES = new Card(Suit.SPADES, Rank.ACE);
    private static final Card KING_HEARTS = new Card(Suit.HEARTS, Rank.KING);
    private static final Card TEN_CLUBS = new Card(Suit.CLUBS, Rank.TEN);
    private static final Card NINE_DIAMONDS = new Card(Suit.DIAMONDS, Rank.NINE);
    private static final Card SEVEN_SPADES = new Card(Suit.SPADES, Rank.SEVEN);
    private static final Card FIVE_HEARTS = new Card(Suit.HEARTS, Rank.FIVE);

    // --- cost/payout math ---

    @Test
    void costIsExactlyHalfTheOriginalPreSplitWager() {
        assertEquals(50.0, BlackjackInsuranceRules.cost(100.0));
        assertEquals(25.0, BlackjackInsuranceRules.cost(50.0));
        assertEquals(0.0, BlackjackInsuranceRules.cost(0.0));
    }

    @Test
    void payoutTotalIsStakePlusTwoToOneProfit() {
        // 2:1 profit (2x stake) plus the stake itself returned == 3x stake.
        assertEquals(300.0, BlackjackInsuranceRules.payoutTotal(100.0));
        assertEquals(150.0, BlackjackInsuranceRules.payoutTotal(50.0));
    }

    // --- eligibility, including the natural-blackjack-holder correction ---

    @Test
    void anySeatedWageredPlayerIsEligible() {
        assertTrue(BlackjackInsuranceRules.isEligible(10.0));
        assertFalse(BlackjackInsuranceRules.isEligible(0.0));
    }

    @Test
    void eligibilityNeverExcludesANaturalBlackjackHolder() {
        // The plan's explicit correction: a player already holding a
        // natural blackjack must still be offered the even-money decision.
        // isEligible deliberately takes no hand parameter at all -- it
        // cannot special-case a natural even if it wanted to, which is
        // itself the regression guard.
        List<Card> naturalBlackjackHand = List.of(ACE_SPADES, TEN_CLUBS);
        assertTrue(BlackjackRules.isNaturalBlackjack(naturalBlackjackHand)); // sanity: this really is a natural
        assertTrue(BlackjackInsuranceRules.isEligible(20.0), "a natural-blackjack holder with a committed wager must still be eligible");
    }

    // --- settle(): main-hand outcome is independent of insurance ---

    @Test
    void settleReturnsZeroInsurancePayoutWhenNoStakeWasTaken() {
        List<Card> dealerBlackjack = List.of(ACE_SPADES, KING_HEARTS);
        List<Card> playerHand = List.of(NINE_DIAMONDS, SEVEN_SPADES);

        BlackjackInsuranceRules.Settlement settlement = BlackjackInsuranceRules.settle(playerHand, dealerBlackjack, 0.0);

        assertEquals(BlackjackOutcome.LOSS, settlement.getMainHandOutcome());
        assertEquals(0.0, settlement.getInsurancePayout());
    }

    @Test
    void settlePaysInsuranceWhenDealerHasBlackjackAndThePlayerTookIt() {
        List<Card> dealerBlackjack = List.of(ACE_SPADES, KING_HEARTS);
        List<Card> playerHand = List.of(NINE_DIAMONDS, SEVEN_SPADES);

        BlackjackInsuranceRules.Settlement settlement = BlackjackInsuranceRules.settle(playerHand, dealerBlackjack, 50.0);

        assertEquals(BlackjackOutcome.LOSS, settlement.getMainHandOutcome(), "losing the main hand to dealer blackjack is unaffected by taking insurance");
        assertEquals(150.0, settlement.getInsurancePayout());
    }

    @Test
    void settleNeverPaysInsuranceWhenDealerDoesNotHaveBlackjackEvenIfStaked() {
        List<Card> dealerNonBlackjack = List.of(TEN_CLUBS, FIVE_HEARTS, NINE_DIAMONDS); // 24 -- irrelevant, just not a natural
        List<Card> playerHand = List.of(NINE_DIAMONDS, SEVEN_SPADES);

        BlackjackInsuranceRules.Settlement settlement = BlackjackInsuranceRules.settle(playerHand, dealerNonBlackjack, 50.0);

        assertEquals(0.0, settlement.getInsurancePayout(), "insurance stakes are forfeited whenever the dealer doesn't have blackjack");
    }

    @Test
    void naturalBlackjackHolderGetsAPushedMainHandPlusTheirInsurancePayout() {
        // The even-money case: this player has their own natural, took
        // insurance, and the dealer also has blackjack. Standard rules: a
        // natural vs. a natural pushes the main hand (neither beats the
        // other) -- the insurance payout is the independent side pot that
        // makes this "even money" overall, not a double payout on the main
        // hand too.
        List<Card> dealerBlackjack = List.of(ACE_SPADES, KING_HEARTS);
        List<Card> playerNaturalBlackjack = List.of(new Card(Suit.CLUBS, Rank.ACE), new Card(Suit.DIAMONDS, Rank.QUEEN));

        BlackjackInsuranceRules.Settlement settlement = BlackjackInsuranceRules.settle(playerNaturalBlackjack, dealerBlackjack, 25.0);

        assertEquals(BlackjackOutcome.PUSH, settlement.getMainHandOutcome());
        assertEquals(75.0, settlement.getInsurancePayout());
    }

    // --- mixed table: insured, uninsured, and a natural-blackjack holder settle independently ---

    @Test
    void mixedTableOfInsuredAndUninsuredPlayersSettlesEachIndependentlyAgainstDealerBlackjack() {
        List<Card> dealerBlackjack = List.of(ACE_SPADES, KING_HEARTS);

        List<Card> insuredLoser = List.of(NINE_DIAMONDS, SEVEN_SPADES); // 16, no insurance-unrelated blackjack
        List<Card> uninsuredLoser = List.of(TEN_CLUBS, FIVE_HEARTS); // 15
        List<Card> insuredNatural = List.of(new Card(Suit.CLUBS, Rank.ACE), new Card(Suit.DIAMONDS, Rank.JACK));

        BlackjackInsuranceRules.Settlement a = BlackjackInsuranceRules.settle(insuredLoser, dealerBlackjack, 20.0);
        BlackjackInsuranceRules.Settlement b = BlackjackInsuranceRules.settle(uninsuredLoser, dealerBlackjack, 0.0);
        BlackjackInsuranceRules.Settlement c = BlackjackInsuranceRules.settle(insuredNatural, dealerBlackjack, 15.0);

        assertEquals(BlackjackOutcome.LOSS, a.getMainHandOutcome());
        assertEquals(60.0, a.getInsurancePayout());

        assertEquals(BlackjackOutcome.LOSS, b.getMainHandOutcome());
        assertEquals(0.0, b.getInsurancePayout());

        // The insured natural pushes its main hand against the dealer's
        // own natural (see naturalBlackjackHolderGetsAPushedMainHandPlusTheirInsurancePayout) -- its
        // insurance payout still lands independently.
        assertEquals(BlackjackOutcome.PUSH, c.getMainHandOutcome());
        assertEquals(45.0, c.getInsurancePayout());
    }

    // --- Settlement value semantics ---

    @Test
    void settlementsWithIdenticalStateAreEqual() {
        BlackjackInsuranceRules.Settlement a = new BlackjackInsuranceRules.Settlement(BlackjackOutcome.WIN, 30.0);
        BlackjackInsuranceRules.Settlement b = new BlackjackInsuranceRules.Settlement(BlackjackOutcome.WIN, 30.0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
