package org.nc.nccasino.games.Blackjack;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blackjack's per-action exposure: what to reserve for an opening hand, a
 * split, a double, and insurance, without ever reserving an action the player
 * has not actually taken.
 */
class BlackjackLiabilityTest {

    @Test
    void openingHandReservesTheBlackjackCeiling() {
        Exposure exposure = BlackjackLiability.openingHand(100.0);
        assertEquals(0, exposure.stake().compareTo(Money.of(100L)));
        assertEquals(0, exposure.maxGrossPayout().compareTo(Money.of(250L)));
        assertEquals(0, exposure.maxHouseLoss().compareTo(Money.of(150L)));
    }

    @Test
    void openingHandNeverReservesASplitOrDoubleNotYetTaken() {
        // Section 19 of the design: a future split/double is not reserved
        // at the opening wager. The opening ceiling is exactly one hand's
        // blackjack payout, nothing more.
        Exposure exposure = BlackjackLiability.openingHand(100.0);
        assertTrue(exposure.maxGrossPayout().compareTo(Money.of(500L)) < 0,
            "must not pre-reserve as if a split had already happened");
    }

    @Test
    void aSplitHandThatCanPayBlackjackReservesTheFullCeiling() {
        Exposure exposure = BlackjackLiability.splitHand(100.0, true);
        assertEquals(0, exposure.maxGrossPayout().compareTo(Money.of(250L)));
    }

    @Test
    void aSplitHandThatCannotPayBlackjackReservesOnlyTheWinCeiling() {
        // Reserving the blackjack ceiling for a hand that can never pay one
        // would tie up funds the dealer can never actually lose.
        Exposure exposure = BlackjackLiability.splitHand(100.0, false);
        assertEquals(0, exposure.maxGrossPayout().compareTo(Money.of(200L)));
    }

    @Test
    void doubleIncreaseIsTwiceOnlyTheAddedStake() {
        // The original half of the wager is already reserved by whatever
        // created the hand -- doubling adds exposure for the increment alone.
        Exposure increase = BlackjackLiability.doubleIncrease(50.0);
        assertEquals(0, increase.stake().compareTo(Money.of(50L)));
        assertEquals(0, increase.maxGrossPayout().compareTo(Money.of(100L)));
    }

    @Test
    void aDoubledHandCanNeverBeANaturalSoItsCeilingIsLowerThanAFreshHandsWouldBe() {
        // Three cards can never be a natural blackjack. Reserving as though
        // it could would over-reserve funds the dealer cannot actually lose.
        Exposure doubled = BlackjackLiability.doubledHand(200.0);
        Exposure freshHandSameStake = BlackjackLiability.openingHand(200.0);
        assertTrue(doubled.maxGrossPayout().compareTo(freshHandSameStake.maxGrossPayout()) < 0);
        assertEquals(0, doubled.maxGrossPayout().compareTo(Money.of(400L)));
    }

    @Test
    void insuranceIsPricedIndependentlyOfTheHandItProtects() {
        // 2:1 profit plus the stake -- 3x total, per BlackjackInsuranceRules.
        Exposure exposure = BlackjackLiability.insurance(25.0);
        assertEquals(0, exposure.stake().compareTo(Money.of(25L)));
        assertEquals(0, exposure.maxGrossPayout().compareTo(Money.of(75L)));
    }

    @Test
    void combiningExposuresAddsBothSidesForASeatHoldingSeveralObligations() {
        // A hand and its insurance can both pay in the same round.
        Exposure hand = BlackjackLiability.openingHand(100.0);
        Exposure insurance = BlackjackLiability.insurance(25.0);
        Exposure combined = BlackjackLiability.combine(hand, insurance);

        assertEquals(0, combined.stake().compareTo(Money.of(125L)));
        assertEquals(0, combined.maxGrossPayout().compareTo(Money.of(325L)));
    }

    @Test
    void combiningWithNullIsIdentity() {
        Exposure hand = BlackjackLiability.openingHand(100.0);
        assertEquals(hand, BlackjackLiability.combine(null, hand));
        assertEquals(hand, BlackjackLiability.combine(hand, null));
        assertEquals(0, BlackjackLiability.combine(null, null).maxGrossPayout().compareTo(Money.ZERO));
    }

    @Test
    void everyExposureIsNumericallySafeForOrdinaryWagers() {
        assertTrue(BlackjackLiability.openingHand(1000.0).isNumericallySafe());
        assertTrue(BlackjackLiability.splitHand(1000.0, true).isNumericallySafe());
        assertTrue(BlackjackLiability.doubleIncrease(1000.0).isNumericallySafe());
        assertTrue(BlackjackLiability.insurance(1000.0).isNumericallySafe());
    }
}
