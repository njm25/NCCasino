package org.nc.nccasino.budget;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.payout.BankedCurrency;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The service as a game actually uses it.
 *
 * <p>The property that matters most here is the boring one: an
 * {@code UNLIMITED} dealer -- which is every dealer on every existing server --
 * must come out the far side of this API having touched no economic state at
 * all. Phase 2 is only safe to ship if opting in is the thing that changes
 * behavior, not installing it.
 */
class DealerBudgetServiceTest {

    private static final String DEALER = "vault";
    private static final UUID PLAYER = UUID.nameUUIDFromBytes("player".getBytes());
    private static final BankedCurrency EMERALDS =
        new BankedCurrency(CurrencyMode.STANDARD, "EMERALD", "Casino Token");

    @TempDir
    Path tempDir;

    private Nccasino plugin;
    private FileConfiguration config;
    private DealerBudgetStore store;
    private DealerBudgetService service;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        config = new YamlConfiguration();
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DealerBudgetServiceTest"));
        when(plugin.getConfig()).thenReturn(config);
        store = new DealerBudgetStore(plugin);
        service = new DealerBudgetService(plugin, store);
    }

    private static BigDecimal money(String value) {
        return Money.of(new BigDecimal(value));
    }

    private void makeLimited(String baseline, int rounds) {
        config.set("dealers." + DEALER + ".budget.mode", "LIMITED");
        config.set("dealers." + DEALER + ".budget.underwriting-baseline", baseline);
        config.set("dealers." + DEALER + ".budget.guaranteed-worst-case-rounds", String.valueOf(rounds));
    }

    private Commitment reserve(String key, Exposure exposure) {
        return service.reserve(DEALER, PLAYER, "Slots", key, EMERALDS, exposure);
    }

    // ---- unlimited backward compatibility --------------------------------

    @Test
    void aDealerWithNoBudgetBlockIsUnlimitedAndRecordsNothing() {
        assertTrue(service.isUnlimited(DEALER));

        Commitment commitment = reserve("spin-1", Exposure.of(money("10"), money("999999")));

        assertTrue(commitment.isAccepted(), "an unlimited dealer accepts what it always accepted");
        assertTrue(commitment.unlimited());
        assertFalse(commitment.requiresSettlement(), "nothing was reserved, so nothing needs settling");
        assertEquals(0, store.liveBalance(DEALER).compareTo(Money.ZERO),
            "an unlimited dealer must not accumulate a balance");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(Money.ZERO));
    }

    @Test
    void settlingAnUnlimitedCommitmentIsAHarmlessNoOp() {
        Commitment commitment = reserve("spin-1", Exposure.of(money("10"), money("500")));
        Settlement settlement = service.settle(DEALER, commitment, money("500"));

        assertTrue(settlement.isResolved(),
            "the settlement path must be identical in both modes");
        assertEquals(0, settlement.paid().compareTo(Money.ZERO));
        assertEquals(0, store.liveBalance(DEALER).compareTo(Money.ZERO));
    }

    @Test
    void increasingAnUnlimitedCommitmentAlwaysSucceeds() {
        Commitment open = reserve("hand-1", Exposure.of(money("10"), money("25")));
        Commitment doubled = service.increase(
            DEALER, open, Exposure.of(money("20"), money("50")), money("10"));

        assertTrue(doubled.isAccepted());
        assertTrue(doubled.unlimited());
        assertEquals(0, store.liveBalance(DEALER).compareTo(Money.ZERO));
    }

    @Test
    void everyDenominationStaysAvailableOnAnUnlimitedDealer() {
        List<BigDecimal> denominations = List.of(money("1"), money("10"), money("1000000"));
        assertEquals(denominations,
            service.affordableDenominations(DEALER, denominations,
                d -> Exposure.of(d, Money.multiply(d, money("1000")))));
    }

    // ---- limited lifecycle -----------------------------------------------

    @Test
    void anAcceptedCommitmentCreditsTheStakeAndPromisesTheWorstCase() {
        makeLimited("10000", 1);
        store.deposit(DEALER, money("1000"));

        Commitment commitment = reserve("spin-1", Exposure.of(money("100"), money("600")));

        assertTrue(commitment.isAccepted());
        assertTrue(commitment.requiresSettlement());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1100")));
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("600")));
    }

    @Test
    void aWinDebitsThePayoutExactlyOnceHoweverManyTimesSettlementIsRetried() {
        makeLimited("10000", 1);
        store.deposit(DEALER, money("1000"));
        Commitment commitment = reserve("spin-1", Exposure.of(money("100"), money("600")));

        assertEquals(Settlement.Status.SETTLED,
            service.settle(DEALER, commitment, money("600")).status());
        for (int i = 0; i < 5; i++) {
            Settlement replay = service.settle(DEALER, commitment, money("600"));
            assertEquals(Settlement.Status.ALREADY_SETTLED, replay.status());
            assertTrue(replay.isResolved());
        }
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("500")),
            "1000 + 100 stake - 600 payout, debited once");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(Money.ZERO));
    }

    @Test
    void claimingABankedPayoutLaterHasNoDealerBudgetEffect() {
        // The design's rule: a payout that moves to the overflow bank leaves
        // the dealer at settlement. Claiming it later is a delivery event, not
        // an economic one -- modelled here as a repeated settlement, which is
        // exactly what a retry looks like to the budget.
        makeLimited("10000", 1);
        store.deposit(DEALER, money("1000"));
        Commitment commitment = reserve("spin-1", Exposure.of(money("100"), money("600")));
        service.settle(DEALER, commitment, money("600"));

        BigDecimal afterAward = store.liveBalance(DEALER);
        service.settle(DEALER, commitment, money("600"));   // claimed from the bank
        service.settle(DEALER, commitment, money("600"));   // delivery retried

        assertEquals(0, store.liveBalance(DEALER).compareTo(afterAward));
    }

    @Test
    void aLossLeavesTheStakeWithTheDealerAndReleasesTheReservation() {
        makeLimited("10000", 1);
        store.deposit(DEALER, money("1000"));
        Commitment commitment = reserve("spin-1", Exposure.of(money("100"), money("600")));

        assertEquals(Settlement.Status.SETTLED,
            service.releaseLoss(DEALER, commitment).status());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1100")));
        assertEquals(0, store.reservedTotal(DEALER).compareTo(Money.ZERO));
    }

    @Test
    void aRefusedCommitmentCarriesItsReasonAndChangesNothing() {
        makeLimited("10000", 1);
        store.deposit(DEALER, money("10"));

        Commitment refused = reserve("spin-1", Exposure.of(money("1"), money("500")));

        assertFalse(refused.isAccepted());
        assertEquals(AdmissionDecision.INSUFFICIENT_FUNDS, refused.decision());
        assertNull(refused.reservation());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("10")),
            "a refused wager must not take the stake");
    }

    @Test
    void aWagerBeyondTheRiskTierIsRefusedAsPermanentEvenWhenFundsAreAmple() {
        makeLimited("1000", 1);
        store.deposit(DEALER, money("500000"));

        Commitment refused = reserve("spin-1", Exposure.of(money("1"), money("50000")));

        assertEquals(AdmissionDecision.EXCEEDS_RISK_TIER, refused.decision());
        assertFalse(refused.decision().isTemporary());
    }

    @Test
    void aReplayedReserveDoesNotCreditTheStakeTwice() {
        makeLimited("10000", 1);
        store.deposit(DEALER, money("1000"));

        Commitment first = reserve("spin-1", Exposure.of(money("100"), money("600")));
        Commitment replay = reserve("spin-1", Exposure.of(money("100"), money("600")));

        assertTrue(first.isAccepted());
        assertTrue(replay.isAccepted());
        assertEquals(first.reservation().id(), replay.reservation().id());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1100")));
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("600")));
    }

    // ---- exposure increases ----------------------------------------------

    @Test
    void anIncreaseIsRefusedBeforeItsStakeIsTakenWhenItCannotBeCovered() {
        makeLimited("10000", 1);
        store.deposit(DEALER, money("700"));
        Commitment open = reserve("hand-1", Exposure.of(money("100"), money("600")));
        assertTrue(open.isAccepted());

        Commitment doubled = service.increase(
            DEALER, open, Exposure.of(money("200"), money("5000")), money("100"));

        assertFalse(doubled.isAccepted());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("800")),
            "the refused double must not have posted its extra wager");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("600")));
    }

    @Test
    void anIncreaseIsJudgedOnTheReplacementExposureNotTheSumOfBoth() {
        // The open reservation is being replaced. Counting it twice would
        // refuse doubles the dealer can plainly afford.
        makeLimited("10000", 1);
        store.deposit(DEALER, money("1000"));
        Commitment open = reserve("hand-1", Exposure.of(money("100"), money("600")));

        Commitment doubled = service.increase(
            DEALER, open, Exposure.of(money("200"), money("1200")), money("100"));

        assertTrue(doubled.isAccepted());
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("1200")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1200")));
    }

    // ---- configuration ---------------------------------------------------

    @Test
    void aLimitedDealerWithABrokenBaselineRefusesEveryWagerWithoutTouchingItsBalance() {
        config.set("dealers." + DEALER + ".budget.mode", "LIMITED");
        config.set("dealers." + DEALER + ".budget.underwriting-baseline", "not-a-number");
        store.deposit(DEALER, money("5000"));

        Commitment refused = reserve("spin-1", Exposure.of(money("1"), money("2")));

        assertEquals(AdmissionDecision.CONFIGURATION_INVALID, refused.decision());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("5000")),
            "a configuration mistake must never damage stored funds");
    }

    @Test
    void anUnreadableModeFallsBackToUnlimitedRatherThanBlockingTheDealer() {
        config.set("dealers." + DEALER + ".budget.mode", "SOMEWHAT_LIMITED");
        assertTrue(service.isUnlimited(DEALER));
        assertTrue(reserve("spin-1", Exposure.of(money("1"), money("99999"))).isAccepted());
    }

    // ---- refills ---------------------------------------------------------

    @Test
    void anElapsedRefillPeriodIsAppliedWhenTheDealerIsNextUsed() {
        makeLimited("10000", 1);
        config.set("dealers." + DEALER + ".budget.refill-mode", "ADD");
        config.set("dealers." + DEALER + ".budget.refill-amount", "500");
        config.set("dealers." + DEALER + ".budget.refill-period", "1h");

        // First touch starts the clock and grants nothing.
        service.admit(DEALER, Exposure.of(money("1"), money("2")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(Money.ZERO));

        // Back-date the boundary by two hours and touch it again.
        DealerBudgetSettings settings = service.settingsFor(DEALER);
        long twoHoursAgo = java.time.Instant.now().getEpochSecond() - 2 * 3600L;
        store.applyRefill(DEALER, settings, twoHoursAgo);      // re-anchors backwards
        service.admit(DEALER, Exposure.of(money("1"), money("2")));

        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1000")),
            "two elapsed hours at 500 each");
    }

    @Test
    void describeReportsTheNumbersAnAdministratorNeeds() {
        makeLimited("10000", 2);
        store.deposit(DEALER, money("1000"));
        reserve("spin-1", Exposure.of(money("100"), money("600")));

        String description = service.describe(DEALER);

        assertTrue(description.contains("LIMITED"), description);
        assertTrue(description.contains("reserved=600"), description);
        assertTrue(description.contains("baseline=10000"), description);
        assertTrue(description.contains("guaranteed-rounds=2"), description);
        assertTrue(description.contains("open-commitments=1"), description);

        assertNotNull(service.describe("never-configured"));
        assertTrue(service.describe("never-configured").contains("UNLIMITED"));
    }
}
