package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the redesign audit's Section 5 fix and its post-audit correction
 * (Section 3): {@link SlotsCommittedResult} has exactly one construction
 * path, {@link SlotsCommittedResult#fromStops}, which always derives
 * {@link SlotsCommittedResult#outcome()} internally so it can never diverge
 * from {@link SlotsCommittedResult#stops()}, and which rejects a null
 * variance rather than silently defaulting it.
 */
class SlotsCommittedResultTest {

    @Test
    void mismatchedStopsLengthIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsCommittedResult.fromStops(5, 3, SlotsVariance.BALANCED, new int[] {1, 2, 3}));
    }

    @Test
    void tooFewOrTooManyStopsIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED, new int[] {1}));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED, new int[] {1, 2, 3, 4, 5}));
    }

    @Test
    void negativeOrOutOfRangeStopIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED, new int[] {-1, 2, 3}));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED,
                new int[] {0, SlotsReelStrip.SIZE, 3}));
    }

    @Test
    void nullStopsIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED, null));
    }

    @Test
    void nullVarianceIsRejectedNeverSilentlyDefaulted() {
        // The post-audit correction: fromStops() used to normalize a null
        // variance to BALANCED instead of rejecting it, contradicting the
        // canonical constructor it wrapped (which already rejected null).
        // Production callers (SlotsSpinController) are responsible for
        // resolving their own default before calling in.
        assertThrows(NullPointerException.class,
            () -> SlotsCommittedResult.fromStops(3, 3, null, new int[] {1, 2, 3}));
    }

    @Test
    void unsupportedColumnOrRowCountIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> SlotsCommittedResult.fromStops(4, 3, SlotsVariance.BALANCED, new int[] {1, 2, 3, 4}));
        assertThrows(IllegalArgumentException.class,
            () -> SlotsCommittedResult.fromStops(3, 2, SlotsVariance.BALANCED, new int[] {1, 2, 3}));
    }

    @Test
    void validStopsReconstructExactlyViaFromStops() {
        int[] stops = {1, 2, 3};
        SlotsCommittedResult result = SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED, stops);
        SlotsOutcome expected = SlotsSpinGenerator.outcomeFromStops(stops, 3, SlotsVariance.BALANCED);
        assertNotNull(result.outcome());
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                assertEquals(expected.symbolAt(row, col), result.outcome().symbolAt(row, col));
            }
        }
    }

    @Test
    void callerMutationOfInputStopsArrayDoesNotAffectStoredResult() {
        int[] input = {5, 6, 7};
        SlotsCommittedResult result = SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED, input);
        input[0] = 99;
        assertArrayEquals(new int[] {5, 6, 7}, result.stops());
    }

    @Test
    void callerMutationOfReturnedStopsArrayDoesNotAffectStoredResult() {
        SlotsCommittedResult result = SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED, new int[] {5, 6, 7});
        int[] returned = result.stops();
        returned[0] = 99;
        assertArrayEquals(new int[] {5, 6, 7}, result.stops());
    }

    @Test
    void compatibilityOverloadReturnsRealStops() {
        SlotsSpinController controller = new SlotsSpinController();
        SlotsPaytable paytable = SlotsPaytable.forConfig(5, 0.03, SlotsVariance.BALANCED);
        SlotsSpinController.SpinAttempt attempt =
            controller.trySpin(10L, 5, 9, false, paytable, bound -> 17, amount -> true);

        assertNotNull(attempt);
        SlotsSpinController.SpinAttempt.Accepted accepted = (SlotsSpinController.SpinAttempt.Accepted) attempt;
        assertEquals(5, accepted.stops().length);
        assertArrayEquals(controller.currentResult().stops(), accepted.stops());
    }

    @Test
    void reconstructionDrawsNoRandomnessByConstruction() {
        // SlotsCommittedResult.fromStops() and SlotsSpinGenerator.outcomeFromStops()
        // take no SlotsRandomSource parameter at all -- reconstructing the
        // visible grid from already-committed stops cannot draw randomness
        // regardless of what a controller's own settlement math might do
        // separately (e.g. payout rounding), which is exactly the "no
        // additional RNG draw during reconstruction" guarantee.
        int[] stops = {3, 33, 66};
        SlotsCommittedResult first = SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED, stops);
        SlotsCommittedResult second = SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED, stops);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                assertEquals(first.outcome().symbolAt(row, col), second.outcome().symbolAt(row, col));
            }
        }
    }

    @Test
    void withVisibleRowsDoesNotDrawAnyNewRandomness() {
        int[] stops = {10, 50, 90};
        SlotsCommittedResult atFive = SlotsCommittedResult.fromStops(3, 5, SlotsVariance.BALANCED, stops);
        // withVisibleRows() takes no SlotsRandomSource at all -- there is no
        // way for it to draw randomness; this proves reconstruction is pure
        // arithmetic over the committed stops.
        SlotsCommittedResult atThree = atFive.withVisibleRows(3);
        assertArrayEquals(stops, atThree.stops());
    }
}
