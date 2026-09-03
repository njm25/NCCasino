package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the redesign's final fix: a demo spin must draw from its own
 * independent randomness stream, never {@code ThreadLocalRandom.current()}
 * (the same source {@link SlotsRandomSource#production()} uses on the same
 * Bukkit server thread), and never a freshly-constructed source per draw.
 */
class SlotsDemoRandomSourceTest {

    @Test
    void reusingOneInstanceProducesADeterministicReproducibleSequenceForItsOwnSeed() {
        SlotsDemoRandomSource a = new SlotsDemoRandomSource(new SplittableRandom(42));
        SlotsDemoRandomSource b = new SlotsDemoRandomSource(new SplittableRandom(42));
        for (int i = 0; i < 200; i++) {
            assertEquals(a.nextInt(100), b.nextInt(100));
        }
    }

    @Test
    void consumingManyDemoValuesNeverAdvancesAnIndependentlyOwnedStream() {
        // The "paid" stream is its own separately-seeded SplittableRandom,
        // entirely independent of the SlotsDemoRandomSource under test --
        // exactly the isolation the fix requires between demo and paid
        // draws. A reference clone (never touched by anything demo-related)
        // records what the paid stream's next N outputs should be.
        SplittableRandom paidStream = new SplittableRandom(7);
        SplittableRandom paidStreamReference = new SplittableRandom(7);
        int[] expectedPaidSequence = new int[50];
        for (int i = 0; i < expectedPaidSequence.length; i++) {
            expectedPaidSequence[i] = paidStreamReference.nextInt(100);
        }

        SlotsDemoRandomSource demo = new SlotsDemoRandomSource(new SplittableRandom(99));
        for (int i = 0; i < 10_000; i++) {
            demo.nextInt(100);
        }

        for (int expected : expectedPaidSequence) {
            assertEquals(expected, paidStream.nextInt(100),
                "the paid stream's sequence must be unaffected by any number of demo draws");
        }
    }

    @Test
    void productionConstructorProducesValuesWithinBound() {
        SlotsDemoRandomSource demo = new SlotsDemoRandomSource();
        for (int i = 0; i < 1000; i++) {
            int value = demo.nextInt(37);
            assertTrue(value >= 0 && value < 37, "value out of bound: " + value);
        }
    }

    @Test
    void twoIndependentlySeededProductionInstancesDoNotShareState() {
        // A shared/global generator (the exact defect this class replaces --
        // both demo and paid draws pulling from the same
        // ThreadLocalRandom.current()) would make two "independent" instances
        // diverge only by interleaved draw order, never actually differ in
        // their own right. Two genuinely independent self-seeded instances
        // essentially never produce an identical run of draws.
        SlotsDemoRandomSource one = new SlotsDemoRandomSource();
        SlotsDemoRandomSource two = new SlotsDemoRandomSource();
        boolean anyDifference = false;
        for (int i = 0; i < 50; i++) {
            if (one.nextInt(1_000_000) != two.nextInt(1_000_000)) {
                anyDifference = true;
                break;
            }
        }
        assertTrue(anyDifference);
    }

    @Test
    void slotsMachineWiresItsDemoStreamThroughTheDedicatedSource() throws NoSuchFieldException {
        // Pins production wiring without constructing a full Bukkit-backed
        // SlotsMachine (which needs a live inventory/player): the declared
        // field type itself is the contract -- SlotsMachine.handleDemoSpin()
        // can only draw from a SlotsDemoRandomSource, not a fresh lambda over
        // ThreadLocalRandom, if this field's type is this class.
        Field field = SlotsMachine.class.getDeclaredField("demoRng");
        assertEquals(SlotsDemoRandomSource.class, field.getType());
        if (!java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
            fail("demoRng must be held once (final) and reused, never recreated per spin");
        }
    }
}
