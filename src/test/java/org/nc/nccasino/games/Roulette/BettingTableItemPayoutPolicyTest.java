package org.nc.nccasino.games.Roulette;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Pair;

/**
 * Covers BettingTable's pre-acceptance exposure gate for STANDARD/item-mode
 * currency -- refundWagerToInventory itself needs a live Bukkit inventory to
 * exercise directly, but the "would this bet push the worst-case payout too
 * high" decision it exists to make unreachable is pure and testable on its
 * own, reusing the same RoulettePayoutMath a real spin resolves with.
 */
class BettingTableItemPayoutPolicyTest {

    @Test
    void ordinaryBetsStayWellUnderTheCeiling() {
        assertFalse(BettingTable.wouldExceedItemModePayoutCeiling(List.of(), "Red - 1:1", 100));
        assertFalse(BettingTable.wouldExceedItemModePayoutCeiling(List.of(), "17 - 35:1", 100));
    }

    @Test
    void aSingleStraightUpBetIsRejectedOnceItsOwnPayoutWouldExceedTheCeiling() {
        // A straight-up bet pays 36x -- MAX_ITEM_MODE_PAYOUT / 36 rounded
        // down is the largest single wager still safe on its own.
        long maxSafeWager = BettingTable.MAX_ITEM_MODE_PAYOUT / 36;
        assertFalse(BettingTable.wouldExceedItemModePayoutCeiling(List.of(), "17 - 35:1", (int) maxSafeWager));
        assertTrue(BettingTable.wouldExceedItemModePayoutCeiling(List.of(), "17 - 35:1", (int) maxSafeWager + 1));
    }

    @Test
    void alreadyStakedBetsOnTheSameNumberCountTowardTheCeiling() {
        // Two bets that individually look safe can still combine into an
        // unsafe payout for the number they share -- the check must look
        // at the FULL hypothetical stack, not just the new bet in isolation.
        // Derived from the live ceiling so this keeps testing the combining
        // rule rather than one hardcoded limit.
        int maxSafeWager = (int) (BettingTable.MAX_ITEM_MODE_PAYOUT / 36);
        List<Pair<String, Integer>> alreadyStaked = List.of(new Pair<>("17 - 35:1", maxSafeWager - 100));

        // Adding 200 pushes the combined stake on number 17 past what the
        // ceiling allows for that one result.
        assertTrue(BettingTable.wouldExceedItemModePayoutCeiling(alreadyStaked, "17 - 35:1", 200));
        // A small enough addition keeps the combined total under the ceiling.
        assertFalse(BettingTable.wouldExceedItemModePayoutCeiling(alreadyStaked, "17 - 35:1", 1));
    }

    @Test
    void betsOnDifferentNumbersDoNotCombineAgainstEachOther() {
        // A bet on number 5 can never win alongside a bet on number 17 in
        // the same spin, so staking near the ceiling on one must not block
        // staking near the ceiling on an unrelated number.
        long maxSafeWager = BettingTable.MAX_ITEM_MODE_PAYOUT / 36;
        List<Pair<String, Integer>> alreadyStaked = List.of(new Pair<>("5 - 35:1", (int) maxSafeWager));
        assertFalse(BettingTable.wouldExceedItemModePayoutCeiling(alreadyStaked, "17 - 35:1", (int) maxSafeWager));
    }

    @Test
    void overlappingCategoriesOnTheSameNumberCombineTowardTheCeiling() {
        // Red-1:1 and a straight-up bet on a red number both pay out on the
        // same spin result (1 is red), so their payouts stack against that
        // one result even though neither bet alone exceeds the ceiling.
        // Red pays 1:1 (a 2x total return), so this sits just under the
        // ceiling on its own, with room for roughly 3,600 more.
        int redStake = (int) (BettingTable.MAX_ITEM_MODE_PAYOUT / 2 - 3_600);
        List<Pair<String, Integer>> alreadyStaked = List.of(new Pair<>("Red - 1:1", redStake));
        assertFalse(BettingTable.wouldExceedItemModePayoutCeiling(alreadyStaked, "5 - 35:1", 1)); // 5 is red but low
        // 300 on number 1 pays 10,800 more, which overshoots that remaining
        // room once result 1 (red AND number 1) is considered.
        assertTrue(BettingTable.wouldExceedItemModePayoutCeiling(alreadyStaked, "1 - 35:1", 300));
    }

    @Test
    @DisplayName("placement accepts a payout above the removed 1,000,000 settlement clamp")
    void placementAcceptsPayoutsAboveTheOldSettlementClamp() {
        // The audit found placement and settlement contradicting each other:
        // bets were accepted far above the 1,000,000 the settlement path would
        // actually pay, and the excess was silently discarded. Settlement now
        // pays whatever placement accepts, so this must be admitted.
        int wager = 100_000; // a straight-up bet returns 36x -> 3,600,000
        assertTrue(36L * wager > 1_000_000L, "fixture must exceed the removed clamp");
        assertFalse(BettingTable.wouldExceedItemModePayoutCeiling(List.of(), "17 - 35:1", wager));
    }

    @Test
    @DisplayName("the pre-wager ceiling is one the settlement path can pay completely")
    void theCeilingIsPayableInFull() {
        // Every accepted payout must be representable by the durable retry
        // record a failed delivery falls back to (a double-typed amount),
        // whose exact-integer range is 2^53.
        assertTrue(BettingTable.MAX_ITEM_MODE_PAYOUT > 1_000_000L);
        assertTrue(BettingTable.MAX_ITEM_MODE_PAYOUT <= (1L << 53));
    }
}
