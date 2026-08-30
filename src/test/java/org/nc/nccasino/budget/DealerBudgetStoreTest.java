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
    void aPayoutLargerThanItsReservationIsClampedRatherThanOverdrawingTheDealer() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        store.creditAndReserve(reservation("spin-1", "300"), Money.ZERO);

        Settlement result = store.settle(DEALER, reservation("spin-1", "300").id(), money("999"));

        assertEquals(Settlement.Status.SETTLED, result.status());
        assertTrue(result.clamped(), "an over-large payout signals an exposure-calculation bug");
        assertEquals(0, result.paid().compareTo(money("300")));
        assertInvariant(store);
    }

    @Test
    void settlingAnUnknownCommitmentMovesNoMoney() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));

        Settlement result = store.settle(DEALER, "never-existed", money("500"));

        assertEquals(Settlement.Status.ALREADY_SETTLED, result.status());
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1000")));
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
    void adjustingAnUnknownReservationChangesNothing() {
        DealerBudgetStore store = store();
        store.deposit(DEALER, money("1000"));
        assertNull(store.adjustReservation(DEALER, "nope", money("10"), money("10")));
        assertEquals(0, store.liveBalance(DEALER).compareTo(money("1000")));
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
