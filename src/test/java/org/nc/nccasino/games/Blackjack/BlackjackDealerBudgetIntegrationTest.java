package org.nc.nccasino.games.Blackjack;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.budget.DealerBudgetService;
import org.nc.nccasino.budget.DealerBudgetStore;
import org.nc.nccasino.budget.Money;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The dealer-budget wiring exercised through the real production path --
 * {@link BlackjackInventory#commitWagerForTest} -- rather than by calling the
 * private helpers directly. This is the property the design actually cares
 * about: a wager the dealer cannot cover never becomes a hand and never
 * touches the player's balance, checked before any card is dealt.
 */
class BlackjackDealerBudgetIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * @param dealerFunding the dealer's own live balance, deposited before
     *     any player action -- separate from the player's own stake, which
     *     is credited automatically the instant a wager is reserved
     */
    private DealerBudgetService attachLimitedBudget(
        BlackjackControllerTestSupport.Harness h, String baseline, int guaranteedRounds, long dealerFunding) {

        when(h.plugin.getDataFolder()).thenReturn(tempDir.toFile());
        DealerBudgetStore store = new DealerBudgetStore(h.plugin);
        DealerBudgetService service = new DealerBudgetService(h.plugin, store);
        when(h.plugin.getDealerBudgetService()).thenReturn(service);

        h.plugin.getConfig().set("dealers." + h.internalName + ".budget.mode", "LIMITED");
        h.plugin.getConfig().set("dealers." + h.internalName + ".budget.underwriting-baseline", baseline);
        h.plugin.getConfig().set(
            "dealers." + h.internalName + ".budget.guaranteed-worst-case-rounds", String.valueOf(guaranteedRounds));
        // This fixture controls dealerFunding explicitly; one-time baseline
        // seeding is covered by the budget-store/service tests.
        store.ensureInitialFunding(h.internalName, Money.of(1L), 1L);
        store.setBalance(h.internalName, Money.ZERO);
        if (dealerFunding > 0) {
            store.deposit(h.internalName, Money.of(dealerFunding));
        }
        return service;
    }

    @Test
    void aWagerBeyondTheDealersRiskTierIsRefusedBeforeAnyCardIsDealt() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            // Ample funding (10,000), but a tiny risk tier (50 / 1 round) --
            // this isolates a permanent tier refusal from a funding shortage.
            attachLimitedBudget(h, "50", 1, 10_000L);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);

            int withdrawAttemptsBefore = h.currencyProvider.withdrawAttempts.size();
            // 100 staked could pay a 250 blackjack -- far past the 50 tier.
            h.inventory.commitWagerForTest(alice, 100.0);

            assertEquals(withdrawAttemptsBefore, h.currencyProvider.withdrawAttempts.size(),
                "a denied wager must never even attempt the debit");
            assertEquals(1000, h.currencyProvider.getBalance(alice, h.internalName),
                "the player's balance must be completely untouched");
        }
    }

    @Test
    void aWagerWithinTheDealersTierIsAcceptedAndReservesTheBlackjackCeiling() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            DealerBudgetService service = attachLimitedBudget(h, "1000", 1, 100L);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);

            h.inventory.commitWagerForTest(alice, 10.0);

            // 10 staked, up to 2.5x (25) reserved as the blackjack ceiling --
            // only possible if the commit actually went through.
            assertEquals(0, service.store().reservedTotal(h.internalName).compareTo(Money.of(25L)));
        }
    }

    @Test
    void forfeitingAfterAWagerReleasesTheReservationWithNothingPaid() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            DealerBudgetService service = attachLimitedBudget(h, "1000", 1, 100L);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            assertTrue(service.store().reservedTotal(h.internalName).compareTo(Money.ZERO) > 0,
                "the opening wager must reserve something before the leave");

            // Undoing the bet before the deal releases it -- exactly the
            // pregame refund path, not a hand-based one.
            h.click(alice, BlackjackSlotLayout.UNDO_ALL_SLOT);

            assertEquals(0, service.store().reservedTotal(h.internalName).compareTo(Money.ZERO),
                "the reservation must be released once the wager is undone");
            // The dealer's starting 100 plus nothing further -- the stake
            // that was briefly credited on reserve went right back out with
            // the refund settlement.
            assertEquals(0, service.store().liveBalance(h.internalName).compareTo(Money.of(100L)),
                "an undone wager must leave the dealer exactly where it started");
        }
    }

    // ---- stable opening-wager action identity ----------------------------

    @Test
    void replayingTheSameOpeningWagerActionIdAppliesExactlyOnce() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            DealerBudgetService service = attachLimitedBudget(h, "1000", 1, 100L);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);

            h.inventory.commitWagerForTest(alice, 10.0, "explicit-open-action-1");
            h.inventory.commitWagerForTest(alice, 10.0, "explicit-open-action-1");

            assertEquals(1, h.currencyProvider.withdrawAttempts.size(),
                "a replayed action id must attempt the debit exactly once");
            assertEquals(990, h.currencyProvider.getBalance(alice, h.internalName),
                "the player must be debited exactly once");
            assertEquals(10.0, h.inventory.totalRoundRefundForPlayerForTest(alice.getUniqueId()),
                "the wager ledger must record exactly one 10-stake increment");
            assertEquals(0, service.store().reservedTotal(h.internalName).compareTo(Money.of(25L)),
                "the reservation must reflect one 10 stake (2.5x ceiling), not two");
            assertEquals(0, service.store().liveBalance(h.internalName).compareTo(Money.of(110L)),
                "the dealer must be credited the 10 stake exactly once");
        }
    }

    @Test
    void twoDistinctOpeningWagerActionIdsOfTheSameDenominationBothApply() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            DealerBudgetService service = attachLimitedBudget(h, "1000", 1, 100L);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);

            h.inventory.commitWagerForTest(alice, 10.0, "explicit-open-action-a");
            h.inventory.commitWagerForTest(alice, 10.0, "explicit-open-action-b");

            assertEquals(2, h.currencyProvider.withdrawAttempts.size(),
                "two distinct real chips of the same denomination must both debit");
            assertEquals(980, h.currencyProvider.getBalance(alice, h.internalName));
            assertEquals(20.0, h.inventory.totalRoundRefundForPlayerForTest(alice.getUniqueId()),
                "both chips must be recorded in the wager ledger");
            assertEquals(0, service.store().reservedTotal(h.internalName).compareTo(Money.of(50L)),
                "the reservation must cover the combined 20 stake (2.5x ceiling)");
        }
    }

    @Test
    void aFailedSecondChipDebitRollsBackOnlyThatGrowthAndPreservesTheFirstWager() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            DealerBudgetService service = attachLimitedBudget(h, "1000", 1, 100L);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);

            h.inventory.commitWagerForTest(alice, 10.0, "explicit-open-action-first");
            assertEquals(0, service.store().reservedTotal(h.internalName).compareTo(Money.of(25L)));

            h.currencyProvider.setNextWithdrawSucceeds(false);
            h.inventory.commitWagerForTest(alice, 5.0, "explicit-open-action-second");

            assertEquals(10.0, h.inventory.totalRoundRefundForPlayerForTest(alice.getUniqueId()),
                "the failed second chip must never join the wager ledger");
            assertEquals(0, service.store().reservedTotal(h.internalName).compareTo(Money.of(25L)),
                "the reservation must be rolled back to exactly the first, legitimate wager");
            assertEquals(0, service.store().liveBalance(h.internalName).compareTo(Money.of(110L)),
                "the dealer must retain only the first wager's real stake");
        }
    }

    @Test
    void undoAfterMultipleChipsReconcilesTheDealerBalanceAndReservation() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            DealerBudgetService service = attachLimitedBudget(h, "1000", 1, 100L);
            h.currencyProvider.setBalance(1000);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);

            h.inventory.commitWagerForTest(alice, 10.0, "explicit-open-action-undo-1");
            h.inventory.commitWagerForTest(alice, 5.0, "explicit-open-action-undo-2");
            assertTrue(service.store().reservedTotal(h.internalName).compareTo(Money.of(25L)) > 0,
                "sanity: the second chip must have grown the reservation beyond the first chip alone");

            h.click(alice, BlackjackSlotLayout.UNDO_ALL_SLOT);

            assertEquals(0, service.store().reservedTotal(h.internalName).compareTo(Money.ZERO),
                "undo-all must release the entire pending opening reservation");
            assertEquals(0, service.store().liveBalance(h.internalName).compareTo(Money.of(100L)),
                "undo-all must leave the dealer exactly where it started");
            assertEquals(1000, h.currencyProvider.getBalance(alice, h.internalName),
                "undo-all must return every committed chip to the player");
        }
    }
}
