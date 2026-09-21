package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the three-speed timing model: SLOW at 150% of Normal duration, NORMAL
 * as the untouched baseline, and FAST at 50%.
 *
 * <p>Two genuinely different scalings have to agree on those three speeds --
 * {@link SlotsSpinSpeed#scaled(long)} for a discrete scheduled delay, and the
 * rational cadence {@link SlotsReelCadence} drives for the reel animation's
 * simulated ticks -- so both are pinned here together.
 */
class SlotsSpinSpeedTest {

    @Test
    void normalNeverScales() {
        assertEquals(0L, SlotsSpinSpeed.NORMAL.scaled(0L));
        assertEquals(1L, SlotsSpinSpeed.NORMAL.scaled(1L));
        assertEquals(40L, SlotsSpinSpeed.NORMAL.scaled(40L));
    }

    @Test
    void fastRoughlyHalves() {
        assertEquals(10L, SlotsSpinSpeed.FAST.scaled(20L));
        assertEquals(20L, SlotsSpinSpeed.FAST.scaled(40L));
    }

    @Test
    void slowStretchesToOneAndAHalfTimesNormal() {
        assertEquals(30L, SlotsSpinSpeed.SLOW.scaled(20L));
        assertEquals(60L, SlotsSpinSpeed.SLOW.scaled(40L));
        assertEquals(15L, SlotsSpinSpeed.SLOW.scaled(10L));
    }

    @Test
    void everySpeedKeepsAPositiveDelayAtLeastOneRealTick() {
        for (SlotsSpinSpeed speed : SlotsSpinSpeed.values()) {
            for (long ticks = 1; ticks <= 200; ticks++) {
                assertTrue(speed.scaled(ticks) >= 1L,
                    speed + "-scaled " + ticks + " must remain at least one tick");
            }
        }
    }

    @Test
    void everySpeedLeavesZeroAtZero() {
        // Scaling "no delay at all" is not meaningful, and inventing one
        // would insert a delay where production deliberately has none.
        for (SlotsSpinSpeed speed : SlotsSpinSpeed.values()) {
            assertEquals(0L, speed.scaled(0L), speed + " must leave a zero delay alone");
        }
    }

    @Test
    void scaledDelaysAreOrderedSlowestToFastest() {
        for (long ticks = 2; ticks <= 200; ticks++) {
            assertTrue(SlotsSpinSpeed.FAST.scaled(ticks) <= SlotsSpinSpeed.NORMAL.scaled(ticks));
            assertTrue(SlotsSpinSpeed.NORMAL.scaled(ticks) <= SlotsSpinSpeed.SLOW.scaled(ticks));
        }
    }

    @Test
    void durationFactorsAreExactlyOneAndAHalfOneAndAHalf() {
        assertEquals(1.5, SlotsSpinSpeed.SLOW.durationFactor(), 1e-9);
        assertEquals(1.0, SlotsSpinSpeed.NORMAL.durationFactor(), 1e-9);
        assertEquals(0.5, SlotsSpinSpeed.FAST.durationFactor(), 1e-9);
    }

    @Test
    void rightClickCyclesSlowThenNormalThenFastAndWrapsBack() {
        assertEquals(SlotsSpinSpeed.NORMAL, SlotsSpinSpeed.SLOW.next());
        assertEquals(SlotsSpinSpeed.FAST, SlotsSpinSpeed.NORMAL.next());
        assertEquals(SlotsSpinSpeed.SLOW, SlotsSpinSpeed.FAST.next());
    }

    @Test
    void threeStepsOfTheCycleReturnToTheStartFromEverySpeed() {
        for (SlotsSpinSpeed speed : SlotsSpinSpeed.values()) {
            assertEquals(speed, speed.next().next().next(), speed + " must complete a three-step cycle");
        }
    }

    @Test
    void labelKeysAreTheThreeLocalizedSpeedNames() {
        assertEquals("slots.spin-speed-slow", SlotsSpinSpeed.SLOW.labelKey());
        assertEquals("slots.spin-speed-normal", SlotsSpinSpeed.NORMAL.labelKey());
        assertEquals("slots.spin-speed-fast", SlotsSpinSpeed.FAST.labelKey());
    }

    @Test
    void parseAcceptsStoredNamesAndFallsBackToNormal() {
        assertEquals(SlotsSpinSpeed.SLOW, SlotsSpinSpeed.parse("SLOW"));
        assertEquals(SlotsSpinSpeed.FAST, SlotsSpinSpeed.parse(" fast "));
        assertEquals(SlotsSpinSpeed.NORMAL, SlotsSpinSpeed.parse(null));
        assertEquals(SlotsSpinSpeed.NORMAL, SlotsSpinSpeed.parse("turbo"));
        assertEquals(SlotsSpinSpeed.NORMAL, SlotsSpinSpeed.parse(""));
    }

    // ---- the reel animation's simulated-tick cadence ---------------------

    @Test
    void normalPlaysExactlyOneSimulatedTickPerRealTick() {
        SlotsReelCadence cadence = SlotsReelCadence.forSpeed(SlotsSpinSpeed.NORMAL);
        for (int realTick = 1; realTick <= 50; realTick++) {
            assertEquals(1L, cadence.advanceOneRealTick());
            assertEquals(realTick, cadence.simulatedTicksElapsed());
        }
    }

    @Test
    void fastPlaysExactlyTwoSimulatedTicksPerRealTick() {
        SlotsReelCadence cadence = SlotsReelCadence.forSpeed(SlotsSpinSpeed.FAST);
        for (int realTick = 1; realTick <= 50; realTick++) {
            assertEquals(2L, cadence.advanceOneRealTick());
            assertEquals(2L * realTick, cadence.simulatedTicksElapsed());
        }
    }

    @Test
    void slowPlaysTwoSimulatedTicksEveryThreeRealTicks() {
        // The zero-yield real tick is exactly what makes SLOW expressible:
        // an integer "at least one simulated tick per real tick" model could
        // only ever speed the presentation up.
        SlotsReelCadence cadence = SlotsReelCadence.forSpeed(SlotsSpinSpeed.SLOW);
        long[] expected = {0L, 1L, 1L, 0L, 1L, 1L, 0L, 1L, 1L};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], cadence.advanceOneRealTick(),
                "real tick " + (i + 1) + " must yield " + expected[i] + " simulated tick(s)");
        }
        assertEquals(6L, cadence.simulatedTicksElapsed());
        assertEquals(9L, cadence.realTicksElapsed());
    }

    @Test
    void cadenceNeverDriftsFromTheExactRationalProgression() {
        for (SlotsSpinSpeed speed : SlotsSpinSpeed.values()) {
            SlotsReelCadence cadence = SlotsReelCadence.forSpeed(speed);
            for (int realTick = 1; realTick <= 500; realTick++) {
                cadence.advanceOneRealTick();
                assertEquals(cadence.simulatedTicksAfter(realTick), cadence.simulatedTicksElapsed(),
                    speed + " drifted at real tick " + realTick);
            }
        }
    }

    @Test
    void presentationDurationMatchesEachSpeedsAdvertisedFactor() {
        // 120 simulated ticks is a comfortably long presentation; the real
        // duration must come out at 180 / 120 / 60 real ticks.
        assertEquals(180L, SlotsReelCadence.forSpeed(SlotsSpinSpeed.SLOW).realTicksFor(120L));
        assertEquals(120L, SlotsReelCadence.forSpeed(SlotsSpinSpeed.NORMAL).realTicksFor(120L));
        assertEquals(60L, SlotsReelCadence.forSpeed(SlotsSpinSpeed.FAST).realTicksFor(120L));
    }

    @Test
    void everySimulatedTickIsPlayedExactlyOnceAndInOrderAtEverySpeed() {
        // The reel schedule is tick-exact: speed may never skip, repeat or
        // reorder a simulated tick, only change how many of them a real tick
        // is worth.
        for (SlotsSpinSpeed speed : SlotsSpinSpeed.values()) {
            SlotsReelCadence cadence = SlotsReelCadence.forSpeed(speed);
            long nextExpected = 0L;
            for (int realTick = 1; realTick <= 300; realTick++) {
                long from = cadence.simulatedTicksElapsed();
                long produced = cadence.advanceOneRealTick();
                assertEquals(nextExpected, from, speed + " skipped or repeated a simulated tick");
                nextExpected = from + produced;
            }
        }
    }

    @Test
    void aNullSpeedFallsBackToTheNormalCadence() {
        assertEquals(1L, SlotsReelCadence.forSpeed(null).advanceOneRealTick());
    }
}
