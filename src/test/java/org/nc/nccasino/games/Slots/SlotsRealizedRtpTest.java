package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit finding 8: {@link SlotsPaytable} normalizes its multipliers against
 * the theoretical, un-floored return, but {@link SlotsMath#totalPayout(SlotsOutcome,
 * int, long, SlotsPaytable)}'s original deterministic {@code Math.floor}
 * structurally biases the realized return downward -- worst at denomination
 * 1, where the floored unit is a large fraction of the average payout. The
 * fix is the {@link SlotsMath#totalPayout(SlotsOutcome, int, long,
 * SlotsPaytable, SlotsRandomSource)} overload, which rounds up with
 * probability equal to the fractional remainder instead of always down.
 *
 * <p>These tests avoid an absolute RTP tolerance tight enough to be flaky for
 * wide/high-variance configurations, whose rare large jackpots dominate
 * variance at any practical sample size. Instead: (1) a fast, statistically
 * solid check that the rounding decision itself lands on the ceiling at
 * roughly the right rate, isolated from gameplay variance entirely, and (2)
 * a full-gameplay comparison, from the identical sequence of spin outcomes,
 * showing the fix measurably closes the bias relative to the old
 * deterministic floor -- which remains valid regardless of jackpot variance
 * because both sides of the comparison share the exact same "luck".
 */
class SlotsRealizedRtpTest {

    private static final double HOUSE_EDGE = 0.03;
    private static final double TARGET_RTP = 1.0 - HOUSE_EDGE;

    private static SlotsRandomSource seeded(long seed) {
        Random random = new Random(seed);
        return random::nextInt;
    }

    /** Every row the same symbol, so any active payline's row selection is irrelevant. */
    private static SlotsOutcome uniform(SlotsSymbol symbol, int columns) {
        SlotsSymbol[][] grid = new SlotsSymbol[SlotsGeometry.ROWS][columns];
        for (int row = 0; row < SlotsGeometry.ROWS; row++) {
            for (int col = 0; col < columns; col++) {
                grid[row][col] = symbol;
            }
        }
        return new SlotsOutcome(grid);
    }

    @Test
    void probabilisticRoundingHitsTheCeilingAtRoughlyTheFractionalRate() {
        // A fixed outcome/paytable/wager combination -- isolates the
        // rounding decision itself from gameplay variance entirely, since
        // the outcome never changes across trials, only the rounding draw.
        SlotsPaytable paytable = SlotsPaytable.forConfig(3, HOUSE_EDGE, SlotsVariance.BALANCED);
        SlotsOutcome outcome = uniform(SlotsSymbol.CHERRY, 3);
        double multiplier = paytable.multiplier(SlotsSymbol.CHERRY, 3);
        assertTrue(multiplier > 0.0, "CHERRY-3 must be a real winning combination for this test to mean anything");

        double raw = multiplier; // wager = 1
        long floorValue = (long) Math.floor(raw);
        double fractional = raw - floorValue;
        assertTrue(fractional > 0.02 && fractional < 0.98,
            "expected a non-trivial fractional payout to test rounding against, got " + fractional);

        int trials = 200_000;
        int roundedUp = 0;
        SlotsRandomSource rng = seeded(42L);
        for (int i = 0; i < trials; i++) {
            long paid = SlotsMath.totalPayout(outcome, 1, 1L, paytable, rng);
            if (paid > floorValue) {
                roundedUp++;
            }
        }
        double observedRate = (double) roundedUp / trials;
        // Standard error at 200,000 Bernoulli trials is on the order of
        // 0.001-0.0013 for any p in [0.02, 0.98]; a 0.02 margin is a
        // generous, non-flaky multiple of that.
        assertTrue(Math.abs(observedRate - fractional) < 0.02,
            "expected to round up close to " + fractional + " of the time, observed " + observedRate);
    }

    @Test
    void probabilisticRoundingNeverRoundsAwayFromTheRawValue() {
        SlotsPaytable paytable = SlotsPaytable.forConfig(3, HOUSE_EDGE, SlotsVariance.BALANCED);
        SlotsOutcome outcome = uniform(SlotsSymbol.CHERRY, 3);
        double multiplier = paytable.multiplier(SlotsSymbol.CHERRY, 3);
        long floorValue = (long) Math.floor(multiplier);
        SlotsRandomSource rng = seeded(7L);
        for (int i = 0; i < 10_000; i++) {
            long paid = SlotsMath.totalPayout(outcome, 1, 1L, paytable, rng);
            assertTrue(paid == floorValue || paid == floorValue + 1,
                "a probabilistic-rounding result must be either the floor or the floor plus one, got " + paid);
        }
    }

    @Test
    void probabilisticRoundingClosesTheDenominationOneRtpBiasAcrossReelWidths() {
        for (int columns : new int[] {3, 5, 7}) {
            SlotsPaytable paytable = SlotsPaytable.forConfig(columns, HOUSE_EDGE, SlotsVariance.BALANCED);
            int trials = 300_000;
            // Two independent draw streams: one decides which symbols land
            // (identical for both totals below), the other decides only the
            // rounding coin-flip for the probabilistic total -- so the
            // comparison isolates the rounding fix from spin-outcome luck.
            SlotsRandomSource spinRng = seeded(1000L + columns);
            SlotsRandomSource roundingRng = seeded(2000L + columns);

            long totalStaked = 0L;
            long floorPayout = 0L;
            long probabilisticPayout = 0L;
            for (int i = 0; i < trials; i++) {
                SlotsOutcome outcome = SlotsSpinGenerator.generate(columns, spinRng, SlotsVariance.BALANCED);
                totalStaked += SlotsMath.totalBet(1L, 1);
                floorPayout += SlotsMath.totalPayout(outcome, 1, 1L, paytable);
                probabilisticPayout += SlotsMath.totalPayout(outcome, 1, 1L, paytable, roundingRng);
            }

            double floorRtp = (double) floorPayout / (double) totalStaked;
            double probabilisticRtp = (double) probabilisticPayout / (double) totalStaked;

            assertTrue(Math.abs(probabilisticRtp - TARGET_RTP) <= Math.abs(floorRtp - TARGET_RTP),
                "columns=" + columns + ": probabilistic rounding (" + probabilisticRtp
                    + ") must be at least as close to the target RTP (" + TARGET_RTP
                    + ") as deterministic flooring (" + floorRtp + ")");
        }
    }

    @Test
    void threeReelDenominationOneRealizedRtpIsWithinOnePercentagePointOfTarget() {
        // The 3-reel case is the one the audit finding quantified precisely
        // (~0.956 realized vs. 0.97 target for the old deterministic floor)
        // and has low enough per-spin payout variance to also hold to a
        // tight absolute bound at a practical sample size, unlike the wider,
        // higher-variance configurations whose rare jackpots dominate
        // variance at any feasible trial count.
        SlotsPaytable paytable = SlotsPaytable.forConfig(3, HOUSE_EDGE, SlotsVariance.BALANCED);
        SlotsRandomSource spinRng = seeded(9001L);
        SlotsRandomSource roundingRng = seeded(9002L);
        long totalStaked = 0L;
        long probabilisticPayout = 0L;
        int trials = 1_000_000;
        for (int i = 0; i < trials; i++) {
            SlotsOutcome outcome = SlotsSpinGenerator.generate(3, spinRng, SlotsVariance.BALANCED);
            totalStaked += SlotsMath.totalBet(1L, 1);
            probabilisticPayout += SlotsMath.totalPayout(outcome, 1, 1L, paytable, roundingRng);
        }
        double rtp = (double) probabilisticPayout / (double) totalStaked;
        assertTrue(Math.abs(rtp - TARGET_RTP) < 0.01,
            "3-reel denomination-1 realized RTP " + rtp + " should be within 1 percentage point of " + TARGET_RTP);
    }
}
