package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller-level regression coverage for the insurance-default and
 * odd-physical-wager redesign: the insurance offer is now generated and
 * stored exactly once, when the offer opens ({@code beginInsurancePhase} ->
 * {@code computeAndStoreInsuranceOffer}), and every later read (display,
 * acceptance/debit, payout, timeout, abort, teardown) looks that stored
 * value up rather than ever recomputing it. For a whole-unit ("physical")
 * currency an odd wager's exact half lands on a half-unit, resolved via a
 * single, deterministic-in-tests 50/50 coin flip
 * ({@link BlackjackInventory#setInsuranceRoundingCoinFlipForTest}) consulted
 * exactly once per offer -- never per read.
 *
 * <p>Most scenarios seat a second, silent "bystander" player alongside the
 * one under test -- with only one eligible player, that single player's own
 * Yes/No answer immediately satisfies {@code checkInsuranceAllDecided} and
 * resolves (and clears) the <em>entire</em> insurance phase synchronously,
 * inside that very click, leaving nothing observable afterward. A bystander
 * who never answers keeps the phase genuinely open so the just-debited
 * stake/offer can actually be inspected before resolution -- exactly the
 * pattern the existing {@code BlackjackTeardownEconomicsIntegrationTest
 * #insuranceStakeIsRefundedOnTeardown} already relies on.
 */
class BlackjackInsuranceOfferIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    /** One seated player, ace up-card (insurance offered). Only safe to use when that one player's own decision is expected to resolve the whole phase immediately. */
    private static List<Card> oneSeatedInsuranceDeck(Rank playerRank, Rank dealerHoleRank) {
        List<Card> stack = new ArrayList<>();
        stack.add(new Card(Suit.SPADES, playerRank));   // player card 1
        stack.add(new Card(Suit.HEARTS, Rank.ACE));     // dealer up-card
        stack.add(new Card(Suit.CLUBS, playerRank));    // player card 2
        stack.add(new Card(Suit.HEARTS, dealerHoleRank)); // dealer hole card
        stack.addAll(flatStack(Rank.SEVEN, 40));
        return stack;
    }

    /** Two seated players, ace up-card -- a silent bystander (player 2) keeps the insurance phase genuinely open after player 1 answers. */
    private static List<Card> twoSeatedInsuranceDeck(Rank playerRank, Rank dealerHoleRank) {
        List<Card> stack = new ArrayList<>();
        stack.add(new Card(Suit.SPADES, playerRank)); // player 1 card 1
        stack.add(new Card(Suit.CLUBS, playerRank));  // player 2 card 1
        stack.add(new Card(Suit.HEARTS, Rank.ACE));   // dealer up-card
        stack.add(new Card(Suit.SPADES, playerRank)); // player 1 card 2
        stack.add(new Card(Suit.CLUBS, playerRank));  // player 2 card 2
        stack.add(new Card(Suit.HEARTS, dealerHoleRank)); // dealer hole card
        stack.addAll(flatStack(Rank.SEVEN, 40));
        return stack;
    }

    private static void driveToInsurancePhase(BlackjackControllerTestSupport.Harness h) {
        for (int i = 0; i < 40 && h.inventory.capturePhaseForTest() != BlackjackFrame.Phase.INSURANCE; i++) {
            h.scheduler.advance(20);
        }
        assertEquals(BlackjackFrame.Phase.INSURANCE, h.inventory.capturePhaseForTest(), "test setup must actually reach the insurance phase");
    }

    private static BooleanSupplier fixed(boolean value) {
        return () -> value;
    }

    // ==================================================================
    // Default duration
    // ==================================================================

    @Test
    void defaultInsuranceTimeoutIsTwentyFiveSeconds() {
        assertEquals(25, BlackjackTiming.INSURANCE_TIMEOUT_DEFAULT_SECONDS);
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            assertEquals(25, h.inventory.insuranceTimeoutSecondsForTest(), "an unconfigured table must default to 25 seconds");
        }
    }

    @Test
    void insuranceTimeoutSecondsConfigKeyStaysCompatible() {
        // Same dotted config key as before (insurance.timeout-seconds) --
        // only the default value changed, never the key an operator's
        // existing config.yml already uses.
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(java.util.Map.of("insurance.timeout-seconds", 15))) {
            assertEquals(15, h.inventory.insuranceTimeoutSecondsForTest(), "an explicit config override must still take effect under the same key");
        }
    }

    // ==================================================================
    // Physical rounding
    // ==================================================================

    @Test
    void evenPhysicalWagerNeedsNoCoinFlipAndOffersExactHalf() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            h.currencyProvider.setBalance(1000);
            h.inventory.setInsuranceRoundingCoinFlipForTest(() -> {
                throw new AssertionError("the coin flip must never be consulted for an even physical wager");
            });
            h.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 6.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            assertEquals(3.0, h.inventory.insuranceOfferedCostForTest(id), 0.0001);
        }
    }

    @Test
    void oddPhysicalWagerCanBeForcedToRoundDown() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            h.currencyProvider.setBalance(1000);
            h.inventory.setInsuranceRoundingCoinFlipForTest(fixed(false));
            h.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0); // odd -- half lands on 2.5
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            assertEquals(2.0, h.inventory.insuranceOfferedCostForTest(id), 0.0001, "wager 5 forced down must offer 2");
        }
    }

    @Test
    void oddPhysicalWagerCanBeForcedToRoundUp() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            h.currencyProvider.setBalance(1000);
            h.inventory.setInsuranceRoundingCoinFlipForTest(fixed(true));
            h.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            assertEquals(3.0, h.inventory.insuranceOfferedCostForTest(id), 0.0001, "wager 5 forced up must offer 3");
        }
    }

    @Test
    void wagerSevenRoundsBetweenThreeAndFourDependingOnTheCoinFlip() {
        try (BlackjackControllerTestSupport.Harness down = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            down.currencyProvider.setBalance(1000);
            down.inventory.setInsuranceRoundingCoinFlipForTest(fixed(false));
            down.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));
            UUID id = UUID.randomUUID();
            Player alice = down.seatOnlinePlayer(id, "Alice");
            down.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            down.inventory.commitWagerForTest(alice, 7.0);
            down.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(down);
            assertEquals(3.0, down.inventory.insuranceOfferedCostForTest(id), 0.0001, "wager 7 forced down must offer 3");
        }
        try (BlackjackControllerTestSupport.Harness up = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            up.currencyProvider.setBalance(1000);
            up.inventory.setInsuranceRoundingCoinFlipForTest(fixed(true));
            up.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));
            UUID id = UUID.randomUUID();
            Player alice = up.seatOnlinePlayer(id, "Alice");
            up.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            up.inventory.commitWagerForTest(alice, 7.0);
            up.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(up);
            assertEquals(4.0, up.inventory.insuranceOfferedCostForTest(id), 0.0001, "wager 7 forced up must offer 4");
        }
    }

    @Test
    void theCoinFlipDecidesTheOddWagerOfferExactlyOncePerOffer() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            h.currencyProvider.setBalance(1000);
            AtomicInteger calls = new AtomicInteger();
            h.inventory.setInsuranceRoundingCoinFlipForTest(() -> {
                calls.incrementAndGet();
                return false;
            });
            h.inventory.stackDeckForTest(twoSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0); // odd -- the only wager needing a coin flip
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 6.0); // even -- must never consult the coin flip

            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            assertEquals(1, calls.get(), "the offer must be decided by exactly one coin flip, regardless of how many players are eligible");
            Double firstRead = h.inventory.insuranceOfferedCostForTest(aliceId);
            Double secondRead = h.inventory.insuranceOfferedCostForTest(aliceId);
            assertEquals(2.0, firstRead, 0.0001);
            assertEquals(firstRead, secondRead, "repeated reads before any decision must return the identical stored value");
            assertEquals(1, calls.get(), "merely reading the offer again must never re-flip");

            // Bob (the bystander) never answers, so accepting keeps the
            // phase open -- the debited stake becomes observable, still
            // without a second flip.
            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);
            assertEquals(1, calls.get(), "accepting the offer must never trigger a second coin flip");
            assertEquals(2.0, h.inventory.insuranceStakeForTest(aliceId), 0.0001, "the debited stake must match the once-decided offer");
        }
    }

    // ==================================================================
    // Display, acceptance, decline, timeout
    // ==================================================================

    @Test
    void theDisplayedCostIsTheExactStoredOffer() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            when(h.preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.STANDARD);
            h.currencyProvider.setBalance(1000);
            h.inventory.setInsuranceRoundingCoinFlipForTest(fixed(true)); // wager 5 -> offer 3
            h.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            double offer = h.inventory.insuranceOfferedCostForTest(id);
            assertEquals(3.0, offer, 0.0001);

            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);

            // formatWagerDisplay is the one call that turns a raw cost into
            // what the player actually sees -- it must have been invoked
            // with the exact stored offer, never any independently
            // recomputed value.
            verify(h.plugin, atLeastOnce()).formatWagerDisplay(any(CurrencyMode.class), anyString(), eq(offer));
        }
    }

    @Test
    void acceptanceDebitsExactlyTheStoredOfferedPrice() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) { // Vault -- exact fractional cost
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(twoSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 25.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 25.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);
            assertEquals(12.5, h.inventory.insuranceOfferedCostForTest(aliceId), 0.0001);

            int withdrawsBefore = h.currencyProvider.withdrawAttempts.size();
            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);

            assertEquals(12.5, h.inventory.insuranceStakeForTest(aliceId), 0.0001);
            assertEquals(withdrawsBefore + 1, h.currencyProvider.withdrawAttempts.size());
            assertEquals(0, BigDecimal.valueOf(12.5).compareTo(h.currencyProvider.withdrawAttempts.get(withdrawsBefore)));
        }
    }

    @Test
    void aFailedAcceptanceDebitRecordsNoPurchasedStake() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(twoSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 20.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 20.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);
            double offer = h.inventory.insuranceOfferedCostForTest(aliceId);
            assertEquals(10.0, offer, 0.0001);

            h.currencyProvider.setNextWithdrawSucceeds(false);
            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);

            assertEquals(0.0, h.inventory.insuranceStakeForTest(aliceId), 0.0001, "a failed debit must never record a purchased stake");
            assertEquals(offer, h.inventory.insuranceOfferedCostForTest(aliceId), 0.0001, "the offer itself must survive a failed debit attempt unchanged");

            // The decision must still be genuinely open (not consumed) --
            // a retry with funds now available must succeed.
            h.currencyProvider.setNextWithdrawSucceeds(true);
            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);
            assertEquals(10.0, h.inventory.insuranceStakeForTest(aliceId), 0.0001);
        }
    }

    @Test
    void decliningChargesNothing() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 20.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            int withdrawsBefore = h.currencyProvider.withdrawAttempts.size();
            h.click(alice, BlackjackSlotLayout.INSURANCE_NO_SLOT);

            assertEquals(0.0, h.inventory.insuranceStakeForTest(id), 0.0001);
            assertEquals(withdrawsBefore, h.currencyProvider.withdrawAttempts.size(), "declining must never attempt a debit");
        }
    }

    @Test
    void timingOutChargesNothing() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(java.util.Map.of("insurance.timeout-seconds", 2))) {
            h.currencyProvider.setBalance(1000);
            h.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 20.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            int withdrawsBefore = h.currencyProvider.withdrawAttempts.size();
            // Let the full configured timeout elapse without ever answering.
            h.scheduler.advance((h.inventory.insuranceTimeoutSecondsForTest() + 2) * 20L);

            assertEquals(0.0, h.inventory.insuranceStakeForTest(id), 0.0001);
            assertEquals(withdrawsBefore, h.currencyProvider.withdrawAttempts.size(), "a timeout must never attempt a debit");
            assertNotEquals(BlackjackFrame.Phase.INSURANCE, h.inventory.capturePhaseForTest(), "the phase must have resolved past insurance");
        }
    }

    // ==================================================================
    // Payout, using the actual purchased price
    // ==================================================================

    @Test
    void payoutUsesTheActualPurchasedPriceWhenTheOfferWasRoundedDown() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(java.util.Map.of("insurance.timeout-seconds", 2), CurrencyMode.STANDARD)) {
            h.currencyProvider.setBalance(1000);
            h.inventory.setInsuranceRoundingCoinFlipForTest(fixed(false)); // wager 5 -> offer 2
            h.inventory.stackDeckForTest(twoSeatedInsuranceDeck(Rank.SEVEN, Rank.TEN)); // dealer hole TEN -> dealer blackjack

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 6.0); // even wager, bystander never answers
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);
            assertEquals(2.0, h.inventory.insuranceOfferedCostForTest(aliceId), 0.0001);

            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);
            assertEquals(2.0, h.inventory.insuranceStakeForTest(aliceId), 0.0001, "phase must still be open (bob hasn't answered) so the debited stake is observable");

            // Bob times out to No; the dealer peek then resolves and pays insurance winners.
            h.scheduler.advance((h.inventory.insuranceTimeoutSecondsForTest() + 2) * 20L);

            // 2:1 payout (2x stake profit) plus the stake back == 3x the actual 2 stake = 6.
            assertTrue(h.currencyProvider.depositAttempts.stream().anyMatch(a -> BigDecimal.valueOf(6.0).compareTo(a) == 0),
                "payout must be 3x the actual purchased 2 stake, never the unrounded 2.5 half: " + h.currencyProvider.depositAttempts);
        }
    }

    @Test
    void payoutUsesTheActualPurchasedPriceWhenTheOfferWasRoundedUp() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(java.util.Map.of("insurance.timeout-seconds", 2), CurrencyMode.STANDARD)) {
            h.currencyProvider.setBalance(1000);
            h.inventory.setInsuranceRoundingCoinFlipForTest(fixed(true)); // wager 5 -> offer 3
            h.inventory.stackDeckForTest(twoSeatedInsuranceDeck(Rank.SEVEN, Rank.TEN));

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 6.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);
            assertEquals(3.0, h.inventory.insuranceOfferedCostForTest(aliceId), 0.0001);

            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);
            assertEquals(3.0, h.inventory.insuranceStakeForTest(aliceId), 0.0001);

            h.scheduler.advance((h.inventory.insuranceTimeoutSecondsForTest() + 2) * 20L);

            // 3x the actual purchased 3 stake = 9.
            assertTrue(h.currencyProvider.depositAttempts.stream().anyMatch(a -> BigDecimal.valueOf(9.0).compareTo(a) == 0),
                "payout must be 3x the actual purchased 3 stake: " + h.currencyProvider.depositAttempts);
        }
    }

    // ==================================================================
    // Abort refund, teardown refund
    // ==================================================================

    @Test
    void abortRefundUsesTheExactDebitedPriceOnce() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            h.currencyProvider.setBalance(1000);
            h.inventory.setInsuranceRoundingCoinFlipForTest(fixed(true)); // wager 5 -> offer 3
            h.inventory.stackDeckForTest(twoSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 6.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);
            assertEquals(3.0, h.inventory.insuranceStakeForTest(aliceId), 0.0001, "test setup must have genuinely debited the stake (bob's own decision is still outstanding)");

            h.inventory.abortRoundAndRefundForTest("blackjack.shoe-exhausted-refunded");

            // Main wager (5) + the actually-debited insurance stake (3) = 8, refunded once.
            assertTrue(h.currencyProvider.depositAttempts.stream().anyMatch(a -> BigDecimal.valueOf(8.0).compareTo(a) == 0),
                "abort refund must return alice's exact debited price (3) plus her main wager (5) == 8, never the unrounded half (2.5): " + h.currencyProvider.depositAttempts);
        }
    }

    @Test
    void teardownRefundUsesTheExactDebitedPriceOnce() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            h.currencyProvider.setBalance(1000);
            h.inventory.setInsuranceRoundingCoinFlipForTest(fixed(false)); // wager 7 -> offer 3
            h.inventory.stackDeckForTest(twoSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 7.0);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 6.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);
            assertEquals(3.0, h.inventory.insuranceStakeForTest(aliceId), 0.0001, "test setup must have genuinely debited the stake");

            h.inventory.delete();

            assertTrue(h.currencyProvider.depositAttempts.stream().anyMatch(a -> BigDecimal.valueOf(10.0).compareTo(a) == 0),
                "teardown refund must return the exact debited price (3) plus alice's main wager (7) == 10, once: " + h.currencyProvider.depositAttempts);

            int depositsAfterFirstTeardown = h.currencyProvider.depositAttempts.size();
            h.inventory.delete();
            assertEquals(depositsAfterFirstTeardown, h.currencyProvider.depositAttempts.size(), "a duplicate teardown must never double-refund");
        }
    }

    // ==================================================================
    // Vault
    // ==================================================================

    @Test
    void vaultFractionalCostRemainsExactAndNeverConsultsTheCoinFlip() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) { // default harness == VAULT
            h.currencyProvider.setBalance(1000);
            h.inventory.setInsuranceRoundingCoinFlipForTest(() -> {
                throw new AssertionError("Vault must never consult the physical-rounding coin flip");
            });
            h.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            assertEquals(2.5, h.inventory.insuranceOfferedCostForTest(id), 0.0001, "Vault must keep the exact fractional half, never physically rounded");
        }
    }

    // ==================================================================
    // Multi-player isolation
    // ==================================================================

    @Test
    void multiplePlayersReceiveDifferentIndependentlyStoredOffers() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            h.currencyProvider.setBalance(1000);
            // Alice's odd wager rounds up, Bob's rounds down -- a single
            // coin-flip decider that alternates by call order proves each
            // player's own offer is decided (and stored) independently.
            AtomicInteger calls = new AtomicInteger();
            h.inventory.setInsuranceRoundingCoinFlipForTest(() -> calls.getAndIncrement() % 2 == 0);

            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.SEVEN));  // alice card 1
            stack.add(new Card(Suit.CLUBS, Rank.SEVEN));   // bob card 1
            stack.add(new Card(Suit.HEARTS, Rank.ACE));    // dealer up-card
            stack.add(new Card(Suit.SPADES, Rank.SEVEN));  // alice card 2
            stack.add(new Card(Suit.CLUBS, Rank.SEVEN));   // bob card 2
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer hole card
            stack.addAll(flatStack(Rank.SEVEN, 40));
            h.inventory.stackDeckForTest(stack);

            UUID aliceId = UUID.randomUUID();
            UUID bobId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0); // odd -- 1st coin flip (call index 0, even -> true -> round up)
            Player bob = h.seatOnlinePlayer(bobId, "Bob");
            h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(bob, 9.0); // odd -- 2nd coin flip (call index 1, odd -> false -> round down)

            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            Double aliceOffer = h.inventory.insuranceOfferedCostForTest(aliceId);
            Double bobOffer = h.inventory.insuranceOfferedCostForTest(bobId);
            assertNotNull(aliceOffer);
            assertNotNull(bobOffer);
            assertEquals(3.0, aliceOffer, 0.0001, "alice's own wager-5 offer, decided by the first coin flip (round up)");
            assertEquals(4.0, bobOffer, 0.0001, "bob's own wager-9 offer, decided by the second coin flip (round down)");

            // Accepting for one player must never touch the other's offer or stake.
            h.click(alice, BlackjackSlotLayout.INSURANCE_YES_SLOT);
            assertEquals(3.0, h.inventory.insuranceStakeForTest(aliceId), 0.0001);
            assertEquals(0.0, h.inventory.insuranceStakeForTest(bobId), 0.0001, "bob has not decided yet -- alice's acceptance must not affect him");
            assertEquals(4.0, h.inventory.insuranceOfferedCostForTest(bobId), 0.0001, "bob's own stored offer must be completely unaffected by alice's decision");
        }
    }

    // ==================================================================
    // Reset boundary
    // ==================================================================

    @Test
    void aNewRoundCannotReuseAStaleOfferFromThePreviousRound() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness(new java.util.HashMap<>(), CurrencyMode.STANDARD)) {
            h.currencyProvider.setBalance(1000);
            h.inventory.setInsuranceRoundingCoinFlipForTest(fixed(false)); // round 1: wager 5 -> offer 2
            h.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));

            UUID id = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(id, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 5.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);
            assertEquals(2.0, h.inventory.insuranceOfferedCostForTest(id), 0.0001, "round 1's own offer");

            // Decline (single eligible player -- resolves the whole phase
            // synchronously), then abort/reset back to a fresh lobby state.
            h.click(alice, BlackjackSlotLayout.INSURANCE_NO_SLOT);
            assertNull(h.inventory.insuranceOfferedCostForTest(id), "the round-1 offer must be gone once insurance resolves for that round");

            h.inventory.abortRoundAndRefundForTest("blackjack.shoe-exhausted-refunded");
            assertNull(h.inventory.insuranceOfferedCostForTest(id), "a round reset must never leave a stale offer behind");

            // Round 2: same wager, opposite coin-flip direction -- if a
            // stale round-1 offer (2) were ever reused instead of a fresh
            // computation, this would silently observe the wrong (stale)
            // value instead of the freshly decided one (3).
            h.inventory.setInsuranceRoundingCoinFlipForTest(fixed(true));
            h.inventory.stackDeckForTest(oneSeatedInsuranceDeck(Rank.SEVEN, Rank.SEVEN));
            h.inventory.commitWagerForTest(alice, 5.0);
            h.inventory.beginStartTransitionForTest();
            driveToInsurancePhase(h);

            assertEquals(3.0, h.inventory.insuranceOfferedCostForTest(id), 0.0001, "round 2 must compute its own fresh offer, never reuse round 1's stale value");
        }
    }
}
