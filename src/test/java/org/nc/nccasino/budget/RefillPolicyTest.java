package org.nc.nccasino.budget;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lazy refills, including the cases a ticking scheduler would get wrong: a
 * server that was offline for days, a dealer nobody touched for a month, a
 * restart that must not re-apply a period, and a system clock that moved.
 */
class RefillPolicyTest {

    private static final long HOUR = 3600L;
    private static final long T0 = 1_700_000_000L;

    private static BigDecimal money(String value) {
        return Money.of(new BigDecimal(value));
    }

    private static DealerBudgetSettings add(String amount, String cap) {
        return DealerBudgetSettings.parse(
            "LIMITED", "10000", "1", "ADD", amount, "1h", cap, null);
    }

    private static DealerBudgetSettings reset(String target) {
        return DealerBudgetSettings.parse(
            "LIMITED", "10000", "1", "RESET", null, "1h", null, target);
    }

    private static DealerBudgetSettings none() {
        return DealerBudgetSettings.parse(
            "LIMITED", "10000", "1", "NONE", null, null, null, null);
    }

    // ---- NONE ------------------------------------------------------------

    @Test
    void aDealerWithNoRefillNeverChanges() {
        RefillPolicy.Result result = RefillPolicy.apply(
            none(), money("500"), Money.ZERO, T0, T0 + 30 * 24 * HOUR);
        assertFalse(result.applied());
        assertEquals(0, result.liveBalance().compareTo(money("500")));
    }

    // ---- the boundary ----------------------------------------------------

    @Test
    void theFirstEverAccessStartsTheClockWithoutBackdatingARefill() {
        // A dealer created today must not be handed a year of missed periods.
        RefillPolicy.Result result = RefillPolicy.apply(
            add("100", null), money("500"), Money.ZERO, 0L, T0);
        assertTrue(result.applied(), "the new boundary must be persisted");
        assertEquals(T0, result.boundaryEpochSeconds());
        assertEquals(0L, result.periodsElapsed());
        assertEquals(0, result.liveBalance().compareTo(money("500")));
    }

    @Test
    void anAccessInsideThePeriodChangesNothingAtAll() {
        RefillPolicy.Result result = RefillPolicy.apply(
            add("100", null), money("500"), Money.ZERO, T0, T0 + HOUR - 1);
        assertFalse(result.applied());
        assertEquals(T0, result.boundaryEpochSeconds());
    }

    @Test
    void theBoundaryAdvancesByWholePeriodsSoFrequentAccessCannotStarveARefill() {
        // Touched at 59 minutes past, repeatedly. If the boundary advanced to
        // "now" each time, the dealer would never complete an hour and would
        // never refill at all.
        long boundary = T0;
        BigDecimal balance = money("0");
        for (int i = 1; i <= 5; i++) {
            RefillPolicy.Result result = RefillPolicy.apply(
                add("100", null), balance, Money.ZERO, boundary, T0 + i * HOUR - 60);
            balance = result.liveBalance();
            boundary = result.boundaryEpochSeconds();
        }
        // Four whole hours elapsed across those five probes.
        assertEquals(0, balance.compareTo(money("400")), "balance after five probes");
        assertEquals(T0 + 4 * HOUR, boundary);
    }

    @Test
    void aRepeatedEvaluationAtTheSameInstantAppliesNothingASecondTime() {
        RefillPolicy.Result first = RefillPolicy.apply(
            add("100", null), money("0"), Money.ZERO, T0, T0 + HOUR);
        assertEquals(0, first.liveBalance().compareTo(money("100")));

        RefillPolicy.Result second = RefillPolicy.apply(
            add("100", null), first.liveBalance(), Money.ZERO,
            first.boundaryEpochSeconds(), T0 + HOUR);
        assertFalse(second.applied(), "a restart must not re-apply an already-applied period");
        assertEquals(0, second.liveBalance().compareTo(money("100")));
    }

    @Test
    void aBackwardsSystemClockReAnchorsInsteadOfGrantingOrStalling() {
        RefillPolicy.Result result = RefillPolicy.apply(
            add("100", null), money("500"), Money.ZERO, T0, T0 - 10 * HOUR);
        assertTrue(result.applied());
        assertEquals(T0 - 10 * HOUR, result.boundaryEpochSeconds());
        assertEquals(0, result.liveBalance().compareTo(money("500")), "no refill for negative elapsed time");
    }

    // ---- ADD -------------------------------------------------------------

    @Test
    void addGrantsOneAmountPerElapsedPeriod() {
        RefillPolicy.Result result = RefillPolicy.apply(
            add("100", null), money("0"), Money.ZERO, T0, T0 + HOUR);
        assertEquals(0, result.liveBalance().compareTo(money("100")));
        assertEquals(1L, result.periodsElapsed());
    }

    @Test
    void manyMissedPeriodsAreAppliedDeterministicallyInOneStep() {
        // Server offline for three days: the result must equal what hourly
        // ticking would have produced.
        RefillPolicy.Result result = RefillPolicy.apply(
            add("100", null), money("0"), Money.ZERO, T0, T0 + 72 * HOUR);
        assertEquals(72L, result.periodsElapsed());
        assertEquals(0, result.liveBalance().compareTo(money("7200")));
        assertEquals(T0 + 72 * HOUR, result.boundaryEpochSeconds());
    }

    @Test
    void aCapBoundsGrowthButNeverConfiscatesABalanceEarnedAboveIt() {
        RefillPolicy.Result capped = RefillPolicy.apply(
            add("100", "1000"), money("950"), Money.ZERO, T0, T0 + 10 * HOUR);
        assertEquals(0, capped.liveBalance().compareTo(money("1000")));

        // The dealer won big and is above its cap. A refill must not claw back.
        RefillPolicy.Result aboveCap = RefillPolicy.apply(
            add("100", "1000"), money("5000"), Money.ZERO, T0, T0 + 10 * HOUR);
        assertEquals(0, aboveCap.liveBalance().compareTo(money("5000")));
    }

    @Test
    void anEnormousElapsedTimeStaysWithinTheSupportedNumericRange() {
        RefillPolicy.Result result = RefillPolicy.apply(
            add("1000000000", null), money("0"), Money.ZERO, 1L, Long.MAX_VALUE / 2);
        assertTrue(Money.isSafe(result.liveBalance()),
            "an absurd elapsed time must not produce an unusable balance");
        assertTrue(result.liveBalance().compareTo(Money.MAX) <= 0);
    }

    @Test
    void aZeroAddAmountIsANoOpRatherThanAnError() {
        RefillPolicy.Result result = RefillPolicy.apply(
            add("0", null), money("500"), Money.ZERO, T0, T0 + 5 * HOUR);
        assertEquals(0, result.liveBalance().compareTo(money("500")));
    }

    @Test
    void anInvalidAddAmountDisablesRefillWithoutDamagingTheBalance() {
        DealerBudgetSettings broken = add("not-a-number", null);
        assertFalse(broken.problems().isEmpty(), "an actionable diagnostic must be produced");
        RefillPolicy.Result result = RefillPolicy.apply(
            broken, money("777"), Money.ZERO, T0, T0 + 100 * HOUR);
        assertEquals(0, result.liveBalance().compareTo(money("777")));
    }

    // ---- RESET -----------------------------------------------------------

    @Test
    void resetSetsTheBalanceToTheTarget() {
        RefillPolicy.Result up = RefillPolicy.apply(
            reset("1000"), money("120"), Money.ZERO, T0, T0 + HOUR);
        assertEquals(0, up.liveBalance().compareTo(money("1000")));

        // A reset is a fresh allowance, so it also brings a surplus back down.
        RefillPolicy.Result down = RefillPolicy.apply(
            reset("1000"), money("8000"), Money.ZERO, T0, T0 + HOUR);
        assertEquals(0, down.liveBalance().compareTo(money("1000")));
    }

    @Test
    void resetIsIdempotentAcrossHoweverManyPeriodsWereMissed() {
        RefillPolicy.Result once = RefillPolicy.apply(
            reset("1000"), money("0"), Money.ZERO, T0, T0 + HOUR);
        RefillPolicy.Result many = RefillPolicy.apply(
            reset("1000"), money("0"), Money.ZERO, T0, T0 + 5000 * HOUR);
        assertEquals(0, once.liveBalance().compareTo(many.liveBalance()));
    }

    @Test
    void resetNeverDropsBelowActiveReservations() {
        // 900 promised to games in flight, target 1000: the promises survive.
        RefillPolicy.Result result = RefillPolicy.apply(
            reset("1000"), money("5000"), money("900"), T0, T0 + HOUR);
        assertEquals(0, result.liveBalance().compareTo(money("1000")));

        // Reservations above the target: the balance is held up to cover them
        // rather than the reservations being invalidated.
        RefillPolicy.Result overCommitted = RefillPolicy.apply(
            reset("1000"), money("5000"), money("4200"), T0, T0 + HOUR);
        assertEquals(0, overCommitted.liveBalance().compareTo(money("4200")));
    }

    @Test
    void theResetTargetIsTotalLiveBalanceNotFreeBalance() {
        // The documented semantic: 900 reserved with a target of 1000 leaves
        // 100 free, NOT 1000 free. Otherwise a player holding a large open
        // commitment could time a reset to mint the house extra funding.
        RefillPolicy.Result result = RefillPolicy.apply(
            reset("1000"), money("950"), money("900"), T0, T0 + HOUR);
        assertEquals(0, result.liveBalance().compareTo(money("1000")));
        assertEquals(0, Money.subtract(result.liveBalance(), money("900")).compareTo(money("100")),
            "free balance after a reset with 900 reserved");
    }

    @Test
    void anInvalidResetTargetLeavesAFundedDealerAlone() {
        DealerBudgetSettings broken = reset("nonsense");
        assertFalse(broken.problems().isEmpty());
        RefillPolicy.Result result = RefillPolicy.apply(
            broken, money("4321"), Money.ZERO, T0, T0 + 10 * HOUR);
        assertEquals(0, result.liveBalance().compareTo(money("4321")),
            "a misconfigured target must never wipe a dealer's funds");
    }

    // ---- refills and the baseline ----------------------------------------

    @Test
    void noRefillModeEverChangesTheUnderwritingBaseline() {
        for (DealerBudgetSettings settings : new DealerBudgetSettings[] {
            add("100", "50000"), reset("50"), none()}) {

            BigDecimal before = settings.maxHouseLossPerRound();
            RefillPolicy.apply(settings, money("10"), Money.ZERO, T0, T0 + 1000 * HOUR);
            assertEquals(0, settings.maxHouseLossPerRound().compareTo(before),
                "a refill changes availability, never the dealer's wager tier");
        }
    }
}
