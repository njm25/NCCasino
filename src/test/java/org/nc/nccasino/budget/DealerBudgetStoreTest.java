package org.nc.nccasino.budget;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.payout.BankedCurrency;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The persistent side of the dealer budget: money that must survive a restart,
 * must never be promised twice, and must never be paid twice.
 *
 * <p>The invariant every test here ultimately checks is one line:
 * {@code reserved <= liveBalance}, and both non-negative. No double payment, no
 * negative dealer and no reservation leak are separate properties -- they are
 * all consequences of that invariant surviving every operation, including the
 * ones that fail partway.
 */
class DealerBudgetStoreTest {

    private static final String DEALER = "highroller";
    private static final BankedCurrency EMERALDS =
        new BankedCurrency(CurrencyMode.STANDARD, "EMERALD", "Casino Token");

    @TempDir
    Path tempDir;

    private Nccasino plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(Nccasino.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DealerBudgetStoreTest"));
    }

    private DealerBudgetStore store() {
        return new DealerBudgetStore(plugin);
    }

    private static BigDecimal money(String value) {
        return Money.of(new BigDecimal(value));
    }

    private static Reservation reservation(String key, String amount) {
        return new Reservation(
            Reservation.forCommitment(DEALER, UUID.nameUUIDFromBytes("p".getBytes()), key),
            DEALER,
            UUID.nameUUIDFromBytes("p".getBytes()),
            "Slots",
            EMERALDS,
            money(amount),
            1_700_000_000L);
    }

    private static void assertInvariant(DealerBudgetStore store) {
        DealerBudgetState state = store.state(DEALER);
        assertTrue(state.isConsistent(),
            "reserved must never exceed the live balance: " + state);
        assertFalse(Money.isNegative(state.liveBalance()), "a dealer must never go negative");
    }

    // ---- persistence -----------------------------------------------------

    @Test
    void aBalanceAndItsReservationsSurviveAReload() {
        DealerBudgetStore store = store();
        assertTrue(store.deposit(DEALER, money("1000")));
        assertNotNull(store.creditAndReserve(reservation("spin-1", "250"), money("10")));

        DealerBudgetStore reloaded = store();
        assertEquals(0, reloaded.liveBalance(DEALER).compareTo(money("1010")));
        assertEquals(0, reloaded.reservedTotal(DEALER).compareTo(money("250")));
        assertEquals(0, reloaded.available(DEALER).compareTo(money("760")));
        assertTrue(new File(tempDir.toFile(), "data/dealer-budgets.yml").isFile());
    }

    @Test
    void anAwardedSettlementIntentSurvivesReloadAndCompletesExactlyOnce() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        Reservation open = store.creditAndReserve(reservation("spin-1", "600"), money("100"));
        // Model the durable midpoint between settle's intent write and its
        // final debit write, then force that state through persistence.
        store.state(DEALER).recordSettlementIntent(open.id(), money("600"));
        assertTrue(store.deposit(DEALER, money("1")));

        DealerBudgetStore reloaded = store();
        assertEquals(0, reloaded.settlementIntents(DEALER).get(open.id()).compareTo(money("600")));
        assertEquals(Settlement.Status.SETTLED,
            reloaded.settle(DEALER, open.id(), money("600")).status());
        assertTrue(reloaded.settlementIntents(DEALER).isEmpty());
        assertEquals(Settlement.Status.ALREADY_SETTLED,
            reloaded.settle(DEALER, open.id(), money("600")).status());
        assertEquals(0, reloaded.liveBalance(DEALER).compareTo(money("501")));
        assertInvariant(reloaded);
    }

    @Test
    void anExactFractionalBalanceRoundTripsWithoutBinaryDrift() {
        DealerBudgetStore store = store();
        // Values a double cannot hold exactly. Ten deposits of 0.1 must be
        // exactly 1, not 0.9999999999999999.
        for (int i = 0; i < 10; i++) {
            assertTrue(store.deposit(DEALER, money("0.1")));
        }
        assertEquals(0, store().liveBalance(DEALER).compareTo(money("1")));
    }

    @Test
    void aStoredValueIsPlainTextRatherThanAnExponentOrAFloat() throws Exception {
        DealerBudgetStore store = store();
        assertTrue(store.deposit(DEALER, money("12345678.5")));
        String contents = Files.readString(tempDir.resolve("data/dealer-budgets.yml"));
        assertTrue(contents.contains("12345678.5"), "expected a plain decimal string: " + contents);
        assertFalse(contents.contains("E+"), "an exponent form would not round-trip: " + contents);
    }

    // ---- reserve is idempotent on the commitment id ----------------------

    @Test
    void reservingTheSameCommitmentTwiceCreditsTheStakeOnlyOnce() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));

        Reservation first = store.creditAndReserve(reservation("hand-7", "300"), money("100"));
        Reservation replay = store.creditAndReserve(reservation("hand-7", "300"), money("100"));

        assertNotNull(first);
        assertNotNull(replay);
        assertEquals(first.id(), replay.id());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1100")),
            "the stake must be credited exactly once");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("300")),
            "the payout must be promised exactly once");
        assertInvariant(store);
    }

    @Test
    void reusingACommitmentIdWithADifferentStakeIsRejectedRatherThanSilentlyAccepted() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));

        Reservation first = store.creditAndReserve(reservation("hand-7", "300"), money("100"));
        assertNotNull(first);
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1100")));

        Reservation mismatched = store.creditAndReserve(reservation("hand-7", "300"), money("250"));

        assertNull(mismatched, "a reused id with a different stake must be refused, not silently accepted");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1100")),
            "the mismatched replay must not move any money");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("300")));
        assertInvariant(store);
    }

    @Test
    void aReservationBeyondTheAvailableFundsIsRefusedAndChangesNothing() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("100"));

        assertNull(store.creditAndReserve(reservation("spin-1", "5000"), money("10")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("100")),
            "a refused reservation must not credit its stake");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(Money.ZERO));
        assertInvariant(store);
    }

    @Test
    void theStakeItselfCountsTowardCoveringItsOwnCommitment() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("100"));
        // Stake 50, gross payout 150: the house risks only 100, which it has.
        assertNotNull(store.creditAndReserve(reservation("spin-1", "150"), money("50")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("150")));
        assertInvariant(store);
    }

    @Test
    void competingReservationsCannotBothClaimTheSameFunds() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("500"));

        assertNotNull(store.creditAndReserve(reservation("a", "400"), Money.ZERO));
        assertNull(store.creditAndReserve(reservation("b", "400"), Money.ZERO),
            "the second commitment must not be promised money already promised");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("400")));
        assertInvariant(store);
    }

    // ---- settlement is idempotent ---------------------------------------

    @Test
    void settlingTheSameCommitmentTwiceDebitsThePayoutOnlyOnce() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        store.creditAndReserve(reservation("spin-1", "300"), money("100"));

        Settlement first = store.settle(DEALER, reservation("spin-1", "300").id(), money("300"));
        Settlement replay = store.settle(DEALER, reservation("spin-1", "300").id(), money("300"));

        assertEquals(Settlement.Status.SETTLED, first.status());
        assertEquals(Settlement.Status.ALREADY_SETTLED, replay.status());
        assertTrue(replay.isResolved(), "a replay is a success, not a failure");
        assertEquals(0, replay.paid().compareTo(Money.ZERO), "a replay must move no money");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("800")));
        assertInvariant(store);
    }

    @Test
    void aPlayerLossRetainsTheStakeAndReleasesTheReservation() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        store.creditAndReserve(reservation("spin-1", "300"), money("100"));

        Settlement result = store.release(DEALER, reservation("spin-1", "300").id());

        assertEquals(Settlement.Status.SETTLED, result.status());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1100")),
            "the dealer keeps the losing stake");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(Money.ZERO),
            "the unused reservation must be released, not leaked");
        assertInvariant(store);
    }

    @Test
    void aPushReturnsTheStakeAndLeavesTheDealerWhereItStarted() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        store.creditAndReserve(reservation("hand-1", "200"), money("100"));

        Settlement result = store.refund(DEALER, reservation("hand-1", "200").id(), money("100"));

        assertEquals(Settlement.Status.SETTLED, result.status());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1000")));
        assertEquals(0, store.reservedTotal(DEALER).compareTo(Money.ZERO));
        assertInvariant(store);
    }

    @Test
    void aPayoutLargerThanItsReservationIsPaidInFullAndFlaggedAsAnExposureViolation() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        store.creditAndReserve(reservation("spin-1", "300"), Money.ZERO);

        Settlement result = store.settle(DEALER, reservation("spin-1", "300").id(), money("999"));

        assertEquals(Settlement.Status.SETTLED, result.status());
        assertTrue(result.exposureViolation(), "an over-large payout signals an exposure-calculation bug");
        assertFalse(result.insolvent(), "the dealer's full balance can still cover this payout");
        assertEquals(0, result.paid().compareTo(money("999")),
            "the player's full awarded result must never be reduced");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1")),
            "the ledger debits the full payout, not just the reservation");
        assertInvariant(store);
    }

    @Test
    void aPayoutExceedingEvenTheFullBalanceFloorsAtZeroRatherThanGoingNegative() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("500"));
        store.creditAndReserve(reservation("spin-1", "300"), Money.ZERO);

        Settlement result = store.settle(DEALER, reservation("spin-1", "300").id(), money("999"));

        assertEquals(Settlement.Status.SETTLED, result.status());
        assertTrue(result.exposureViolation());
        assertTrue(result.insolvent(), "even the dealer's full balance could not cover this payout");
        assertEquals(0, result.paid().compareTo(money("999")),
            "the player's full awarded result must never be reduced, even when the dealer is insolvent");
        assertEquals(0, store.liveBalance(DEALER).compareTo(Money.ZERO),
            "the balance floors at zero rather than going negative");
        assertFalse(Money.isNegative(store.liveBalance(DEALER)));
    }

    @Test
    void anOversizedSettlementNeverConsumesAnotherActiveReservationsBacking() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("500"));
        store.creditAndReserve(reservation("spin-A", "300"), Money.ZERO);
        store.creditAndReserve(reservation("spin-B", "200"), Money.ZERO);

        Settlement result = store.settle(DEALER, reservation("spin-A", "300").id(), money("999"));

        assertEquals(Settlement.Status.SETTLED, result.status());
        assertTrue(result.exposureViolation());
        assertTrue(result.insolvent(), "the dealer's full balance could not cover this payout");
        assertEquals(0, result.paid().compareTo(money("999")),
            "the player's full awarded result must never be reduced");
        // Only the 300 not already promised to spin-B could be paid from the
        // dealer's economy without eating into spin-B's backing.
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("200")),
            "spin-B's 200 must remain fully backed");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("200")),
            "spin-B's reservation must be untouched");
        assertInvariant(store);

        List<DealerBudgetState.Shortfall> shortfalls = store.shortfalls(DEALER);
        assertEquals(1, shortfalls.size());
        assertEquals(0, shortfalls.get(0).amount().compareTo(money("699")),
            "the exact unbacked remainder must be durably recorded for reconciliation");
    }

    @Test
    void aShortfallSurvivesAReloadExactly() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("500"));
        store.creditAndReserve(reservation("spin-A", "300"), Money.ZERO);
        store.creditAndReserve(reservation("spin-B", "200"), Money.ZERO);
        store.settle(DEALER, reservation("spin-A", "300").id(), money("999"));

        DealerBudgetStore reloaded = store();
        List<DealerBudgetState.Shortfall> shortfalls = reloaded.shortfalls(DEALER);
        assertEquals(1, shortfalls.size(), "the shortfall record must survive a restart exactly");
        assertEquals(reservation("spin-A", "300").id(), shortfalls.get(0).reservationId());
        assertEquals(0, shortfalls.get(0).amount().compareTo(money("699")));
        assertEquals(0, reloaded.liveBalance(DEALER).compareTo(money("200")));
        assertEquals(0, reloaded.reservedTotal(DEALER).compareTo(money("200")));
        assertInvariant(reloaded);
    }

    @Test
    void resolvingAShortfallClearsItExactlyOnce() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("500"));
        store.creditAndReserve(reservation("spin-A", "300"), Money.ZERO);
        store.creditAndReserve(reservation("spin-B", "200"), Money.ZERO);
        store.settle(DEALER, reservation("spin-A", "300").id(), money("999"));
        String shortfallId = reservation("spin-A", "300").id();

        assertTrue(store.resolveShortfall(DEALER, shortfallId),
            "an outstanding shortfall must actually be resolved");
        assertTrue(store.shortfalls(DEALER).isEmpty());

        assertFalse(store.resolveShortfall(DEALER, shortfallId),
            "resolving an already-resolved shortfall must be a harmless no-op, not an error");
        assertFalse(store.resolveShortfall(DEALER, "never-existed"),
            "resolving an id that never had a shortfall must be a harmless no-op");

        DealerBudgetStore reloaded = store();
        assertTrue(reloaded.shortfalls(DEALER).isEmpty(),
            "the resolution must be durably persisted, not just in-memory");
    }

    @Test
    void distinctReservationsBothInShortfallAreEachRecordedExactly() {
        // Two independent settlements can each go insolvent without one
        // clobbering or duplicating the other's record.
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("300"));
        store.creditAndReserve(reservation("spin-A", "300"), Money.ZERO);
        store.settle(DEALER, reservation("spin-A", "300").id(), money("999"));
        assertEquals(0, store.liveBalance(DEALER).compareTo(Money.ZERO));

        store.deposit(DEALER, money("50"));
        store.creditAndReserve(reservation("spin-B", "50"), Money.ZERO);
        store.settle(DEALER, reservation("spin-B", "50").id(), money("999"));

        List<DealerBudgetState.Shortfall> shortfalls = store.shortfalls(DEALER);
        assertEquals(2, shortfalls.size(), "each insolvent settlement must produce its own record");
        assertTrue(shortfalls.stream().anyMatch(s -> s.reservationId().equals(reservation("spin-A", "300").id())));
        assertTrue(shortfalls.stream().anyMatch(s -> s.reservationId().equals(reservation("spin-B", "50").id())));
    }

    @Test
    void settlingAnUnknownCommitmentMovesNoMoney() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));

        Settlement result = store.settle(DEALER, "never-existed", money("500"));

        assertEquals(Settlement.Status.ALREADY_SETTLED, result.status());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1000")));
    }

    @Test
    void aSettledCommitmentCannotBeRecreatedByReplayingCreditAndReserve() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        Reservation original = store.creditAndReserve(reservation("spin-1", "300"), money("50"));
        assertNotNull(original);
        store.settle(DEALER, original.id(), money("100"));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("950")));

        Reservation recreated = store.creditAndReserve(reservation("spin-1", "300"), money("50"));

        assertNull(recreated, "a settled commitment id must never be recreated as a fresh reservation");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("950")),
            "no stake may be credited for a recreation attempt");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(Money.ZERO));
        assertInvariant(store);
    }

    @Test
    void reusingACommitmentIdWithADifferentPayloadIsRefused() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        Reservation original = store.creditAndReserve(reservation("spin-1", "300"), money("50"));
        assertNotNull(original);

        Reservation differentGame = new Reservation(
            original.id(), DEALER, original.playerId(), "Roulette",
            EMERALDS, money("300"), original.createdAtEpochSeconds());
        Reservation conflict = store.creditAndReserve(differentGame, money("50"));

        assertNull(conflict, "a reused id with a different game/player/currency/exposure must be refused");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("300")),
            "the original reservation must be untouched");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1050")),
            "no second stake may be credited for the rejected payload");
        assertInvariant(store);
    }

    // ---- adjusting an open commitment ------------------------------------

    @Test
    void aPortfolioReservationCanGrowAtomicallyWithItsAddedStake() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        Reservation open = store.creditAndReserve(reservation("table-1", "200"), money("50"));
        assertNotNull(open);

        Reservation grown = store.adjustReservation(DEALER, open.id(), money("500"), money("50"));

        assertNotNull(grown);
        assertEquals(0, grown.amount().compareTo(money("500")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1100")));
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("500")));
        assertInvariant(store);
    }

    @Test
    void anIncreaseTheDealerCannotCoverIsRefusedBeforeTheStakeIsTaken() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("300"));
        Reservation open = store.creditAndReserve(reservation("table-1", "200"), money("50"));
        assertNotNull(open);

        assertNull(store.adjustReservation(DEALER, open.id(), money("99999"), money("50")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("350")),
            "a refused increase must not take the additional stake");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("200")));
        assertInvariant(store);
    }

    @Test
    void replayingTheSameIncreaseDoesNotCreditTheAdditionalStakeTwice() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        Reservation open = store.creditAndReserve(reservation("table-1", "200"), money("50"));
        assertNotNull(open);

        Reservation first = store.adjustReservation(DEALER, open.id(), money("500"), money("50"));
        Reservation replay = store.adjustReservation(DEALER, open.id(), money("500"), money("50"));

        assertNotNull(first);
        assertNotNull(replay);
        assertEquals(0, replay.amount().compareTo(money("500")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1100")),
            "a replayed increase for one real deposit must not credit the stake twice");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("500")));
        assertInvariant(store);
    }

    @Test
    void adjustingAnUnknownReservationChangesNothing() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        assertNull(store.adjustReservation(DEALER, "nope", money("10"), money("10")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1000")));
    }

    @Test
    void reducingAReservationRemovesOnlyTheReturnedStakeAndKeepsItsIdentity() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        Reservation open = store.creditAndReserve(reservation("table-1", "200"), money("50"));
        Reservation grown = store.adjustReservation(
            DEALER, open.id(), "bet-2", money("500"), money("50"));
        assertNotNull(grown);

        ReservationAdjustment reduced = store.reduceReservation(
            DEALER, open.id(), "undo-2", money("200"), money("50"));

        assertTrue(reduced.success());
        assertNotNull(reduced.reservation());
        assertEquals(open.id(), reduced.reservation().id(),
            "an undo must preserve the original commitment identity");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1050")));
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("200")));
        assertInvariant(store);

        DealerBudgetStore reloaded = store();
        assertEquals(0, reloaded.liveBalance(DEALER).compareTo(money("1050")));
        assertEquals(0, reloaded.reservedTotal(DEALER).compareTo(money("200")));
    }

    @Test
    void replayingTheSameReductionDoesNotRemoveTheStakeTwice() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        Reservation open = store.creditAndReserve(reservation("table-1", "500"), money("100"));

        ReservationAdjustment first = store.reduceReservation(
            DEALER, open.id(), "undo-1", money("200"), money("50"));
        ReservationAdjustment replay = store.reduceReservation(
            DEALER, open.id(), "undo-1", money("200"), money("50"));

        assertTrue(first.success());
        assertTrue(replay.success());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1050")));
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("200")));
        assertInvariant(store);
    }

    @Test
    void anOlderAdjustmentReplayStaysHarmlessAfterANewerOperationAndReload() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        Reservation open = store.creditAndReserve(reservation("table-1", "200"), money("50"));

        assertNotNull(store.adjustReservation(
            DEALER, open.id(), "bet-A", money("300"), money("25")));
        assertNotNull(store.adjustReservation(
            DEALER, open.id(), "bet-B", money("400"), money("25")));

        DealerBudgetStore reloaded = store();
        Reservation replay = reloaded.adjustReservation(
            DEALER, open.id(), "bet-A", money("300"), money("25"));

        assertNotNull(replay);
        assertEquals(0, replay.amount().compareTo(money("400")),
            "replaying an older operation must not roll exposure backward");
        assertEquals(0, reloaded.liveBalance(DEALER).compareTo(money("1100")),
            "the older operation's stake must not be credited twice");
        assertInvariant(reloaded);
    }

    @Test
    void reducingTheLastStakeClosesAndTombstonesTheCommitmentAtomically() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        Reservation open = store.creditAndReserve(reservation("table-1", "200"), money("50"));

        ReservationAdjustment closed = store.reduceReservation(
            DEALER, open.id(), "undo-last", Money.ZERO, money("50"));

        assertTrue(closed.success());
        assertNull(closed.reservation());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1000")));
        assertEquals(0, store.reservedTotal(DEALER).compareTo(Money.ZERO));
        assertNull(store.creditAndReserve(reservation("table-1", "200"), money("50")),
            "the closed commitment id must not be recreated");
        assertInvariant(store);
    }

    // ---- withdrawal never touches promised money -------------------------

    @Test
    void reservedFundsCannotBeWithdrawn() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        store.creditAndReserve(reservation("spin-1", "800"), Money.ZERO);

        assertFalse(store.withdrawUnreserved(DEALER, money("500")),
            "only unreserved funds may be withdrawn");
        assertTrue(store.withdrawUnreserved(DEALER, money("200")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("800")));
        assertInvariant(store);
    }

    @Test
    void anAdministrativeBalanceSetNeverDropsBelowActiveReservations() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        store.creditAndReserve(reservation("spin-1", "600"), Money.ZERO);

        assertTrue(store.setBalance(DEALER, money("10")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("600")),
            "a promise outranks an administrative adjustment");
        assertInvariant(store);
    }

    // ---- one-time LIMITED funding seed ------------------------------------

    @Test
    void aFreshDealerIsSeededToTheBaselineExactlyOnce() {
        DealerBudgetStore store = store();

        assertTrue(store.ensureInitialFunding(DEALER, money("5000"), 1_700_000_000L));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("5000")));

        boolean seededAgain = store.ensureInitialFunding(DEALER, money("5000"), 1_700_100_000L);

        assertFalse(seededAgain, "a dealer that has ever been touched before must not be re-seeded");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("5000")),
            "a second seed attempt must not mint additional money");
    }

    @Test
    void theSeedSurvivesAReloadAndIsNeverRepeated() {
        DealerBudgetStore store = store();
        assertTrue(store.ensureInitialFunding(DEALER, money("5000"), 1_700_000_000L));

        DealerBudgetStore reloaded = store();
        assertFalse(reloaded.ensureInitialFunding(DEALER, money("5000"), 1_700_200_000L),
            "the persisted baseline-initialized marker must prevent re-seeding after a restart");
        assertEquals(0, reloaded.liveBalance(DEALER).compareTo(money("5000")));
    }

    // ---- migration: baseline-initialized is a marker of its own, not refill-boundary ----

    private void writeLegacyFile(String liveBalance, long refillBoundary, boolean withBaselineMarker) throws Exception {
        Path data = tempDir.resolve("data");
        Files.createDirectories(data);
        StringBuilder yaml = new StringBuilder();
        yaml.append("version: 2\n");
        yaml.append("dealers:\n");
        yaml.append("  ").append(DEALER).append(":\n");
        yaml.append("    live-balance: \"").append(liveBalance).append("\"\n");
        yaml.append("    refill-boundary: ").append(refillBoundary).append("\n");
        if (withBaselineMarker) {
            yaml.append("    baseline-initialized: ").append(refillBoundary).append("\n");
        }
        yaml.append("    reservations: []\n");
        Files.writeString(data.resolve("dealer-budgets.yml"), yaml.toString());
    }

    @Test
    void aFreshLimitedDealerWithNoFileAtAllIsEligibleForSeeding() {
        // No file on disk yet -- the plain "brand new dealer" case.
        DealerBudgetStore store = store();
        assertTrue(store.ensureInitialFunding(DEALER, money("5000"), 1_700_000_000L));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("5000")));
    }

    @Test
    void anExistingNoneDealerWithBoundaryZeroIsStillEligibleForSeeding() throws Exception {
        // refill-mode NONE never touches refill-boundary, so a dealer that
        // has only ever received wagers (never admitted through the seed
        // path) legitimately has boundary 0 and no baseline-initialized key.
        writeLegacyFile("300", 0L, false);
        DealerBudgetStore store = store();

        assertTrue(store.ensureInitialFunding(DEALER, money("5000"), 1_700_000_000L));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("5000")),
            "an unseeded dealer must still receive its one-time floor regardless of prior balance");
    }

    @Test
    void anExistingAddOrResetDealerWithAnAlreadyRunningRefillClockIsMigratedSafely() throws Exception {
        // This is the exact migration hazard: a file from before the
        // baseline-initialized marker existed, where refill-boundary is
        // already positive purely because ADD/RESET refills have been
        // running -- NOT because the baseline was ever seeded. The old
        // (buggy) gate on refill-boundary > 0 would have falsely treated
        // this dealer as already-seeded and permanently denied it the floor.
        long longRunningBoundary = 1_650_000_000L;
        writeLegacyFile("8000", longRunningBoundary, false);
        DealerBudgetStore store = store();

        assertTrue(store.ensureInitialFunding(DEALER, money("5000"), 1_700_000_000L),
            "an old file with no baseline-initialized marker must still be seeded once,"
                + " even though its refill boundary is already positive");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("8000")),
            "the dealer was already above baseline, so the floor is a no-op, not a top-up");
        assertEquals(longRunningBoundary, store.state(DEALER).refillBoundaryEpochSeconds(),
            "an already-running refill clock must never be reset back to \"now\" by migration seeding");

        boolean reseeded = store.ensureInitialFunding(DEALER, money("5000"), 1_700_500_000L);
        assertFalse(reseeded, "the marker set during migration must prevent a second seed");
    }

    @Test
    void anExistingManuallyFundedBalanceBelowBaselineIsToppedUpExactlyOnce() throws Exception {
        writeLegacyFile("1000", 0L, false);
        DealerBudgetStore store = store();

        assertTrue(store.ensureInitialFunding(DEALER, money("5000"), 1_700_000_000L));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("5000")),
            "a manually-funded balance below the baseline still receives the one-time floor");

        DealerBudgetStore reloaded = store();
        assertFalse(reloaded.ensureInitialFunding(DEALER, money("5000"), 1_700_600_000L),
            "the marker must survive a restart and block a second seed");
        assertEquals(0, reloaded.liveBalance(DEALER).compareTo(money("5000")));
    }

    @Test
    void aDealerAlreadyCarryingTheMarkerIsNeverReseededByMigration() throws Exception {
        writeLegacyFile("5000", 1_700_000_000L, true);
        DealerBudgetStore store = store();

        assertFalse(store.ensureInitialFunding(DEALER, money("50000"), 1_700_900_000L),
            "a dealer that explicitly carries the marker must never be seeded again,"
                + " even against a much larger later baseline");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("5000")));
    }

    @Test
    void aLaterBaselineChangeNeverMovesAlreadySeededMoney() {
        DealerBudgetStore store = store();
        assertTrue(store.ensureInitialFunding(DEALER, money("5000"), 1_700_000_000L));

        // An administrator raises the configured baseline afterward; this
        // must never be re-applied to a dealer that was already seeded.
        boolean reseeded = store.ensureInitialFunding(DEALER, money("50000"), 1_700_300_000L);

        assertFalse(reseeded);
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("5000")),
            "live money must not move because the configured baseline changed");
    }

    @Test
    void aDealerWithNoBaselineIsNeverSeeded() {
        DealerBudgetStore store = store();
        assertFalse(store.ensureInitialFunding(DEALER, Money.ZERO, 1_700_000_000L));
        assertEquals(0, store.liveBalance(DEALER).compareTo(Money.ZERO));
    }

    // ---- recovery --------------------------------------------------------

    @Test
    void aFileWithMoreReservedThanHeldHonorsThePromisesRatherThanDeletingThem() throws Exception {
        Path data = tempDir.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("dealer-budgets.yml"), """
            version: 1
            dealers:
              highroller:
                live-balance: "100.000000"
                refill-boundary: 0
                reservations:
                - id: "orphan-1"
                  player: "00000000-0000-0000-0000-000000000001"
                  game: "Slots"
                  amount: "900.000000"
                  created: 1700000000
                  currency-mode: STANDARD
                  currency-material: EMERALD
                  currency-name: "Casino Token"
            """);

        DealerBudgetStore store = store();
        assertEquals(1, store.state(DEALER).reservations().size(),
            "an economically meaningful reservation must never be silently deleted");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("900")),
            "the balance is raised to honor the promise");
        assertInvariant(store);
    }

    @Test
    void aMalformedReservationIsSkippedWithoutDiscardingTheRestOfTheFile() throws Exception {
        Path data = tempDir.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("dealer-budgets.yml"), """
            version: 1
            dealers:
              highroller:
                live-balance: "500.000000"
                refill-boundary: 0
                reservations:
                - id: "good-1"
                  player: "00000000-0000-0000-0000-000000000001"
                  game: "Slots"
                  amount: "100.000000"
                  created: 1700000000
                  currency-mode: STANDARD
                  currency-material: EMERALD
                  currency-name: "Casino Token"
                - id: "bad-1"
                  player: "not-a-uuid"
                  amount: "banana"
            """);

        DealerBudgetStore store = store();
        assertEquals(1, store.state(DEALER).reservations().size());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("500")));
        assertInvariant(store);
    }

    @Test
    void staleReservationsAreReportedRatherThanQuietlyRemoved() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        store.creditAndReserve(reservation("spin-1", "100"), Money.ZERO);

        long now = 1_700_000_000L + TimeUnit.DAYS.toSeconds(3);
        List<Reservation> stale = store.staleReservations(now, TimeUnit.HOURS.toSeconds(1));

        assertEquals(1, stale.size());
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("100")),
            "reporting a stale reservation must not release it -- that is an operator decision");
    }

    // ---- concurrency -----------------------------------------------------

    @Test
    void concurrentReservationsNeverPromiseMoreThanTheDealerHolds() throws Exception {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));

        int threads = 8;
        int perThread = 25;
        // 200 attempts at 100 each against 1000 available: at most 10 can win.
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger granted = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int thread = t;
            tasks.add(() -> {
                for (int i = 0; i < perThread; i++) {
                    if (store.creditAndReserve(
                        reservation("c-" + thread + "-" + i, "100"), Money.ZERO) != null) {
                        granted.incrementAndGet();
                    }
                }
                return null;
            });
        }
        for (Future<Void> future : pool.invokeAll(tasks)) {
            future.get();
        }
        pool.shutdownNow();

        assertEquals(10, granted.get(), "exactly the affordable number of commitments may be granted");
        assertEquals(0, store.reservedTotal(DEALER).compareTo(money("1000")));
        assertInvariant(store);
    }

    @Test
    void concurrentSettlementsOfOneCommitmentPayItExactlyOnce() throws Exception {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        Reservation open = store.creditAndReserve(reservation("spin-1", "400"), Money.ZERO);
        assertNotNull(open);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger settled = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            tasks.add(() -> {
                Settlement result = store.settle(DEALER, open.id(), money("400"));
                if (result.status() == Settlement.Status.SETTLED) {
                    settled.incrementAndGet();
                }
                return null;
            });
        }
        for (Future<Void> future : pool.invokeAll(tasks)) {
            future.get();
        }
        pool.shutdownNow();

        assertEquals(1, settled.get(), "only one of the racing settlements may move money");
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("600")));
        assertInvariant(store);
    }
}
