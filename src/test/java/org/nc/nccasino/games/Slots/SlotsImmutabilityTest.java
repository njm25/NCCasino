package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every array-bearing record introduced by the redesign must actually be
 * immutable from the outside: mutating a constructor's input array, or
 * mutating a returned array, must never change what the record itself
 * reports afterwards.
 */
class SlotsImmutabilityTest {

    @Test
    void paylineCatalogLineIsImmutable() {
        int[] input = {1, 1, 1};
        SlotsPaylineCatalog.Line line = new SlotsPaylineCatalog.Line(1, "middle", input);

        input[0] = 99;
        assertArrayEquals(new int[] {1, 1, 1}, line.rows(),
            "mutating the constructor's input array must not affect the record");

        int[] returned = line.rows();
        returned[0] = 42;
        assertArrayEquals(new int[] {1, 1, 1}, line.rows(),
            "mutating a returned array must not affect the record, or a later call");

        // Catalog behavior itself must be unaffected by any of the above.
        SlotsPaylineCatalog.Line fromCatalog = SlotsPaylineCatalog.forGeometry(3, 3).get(0);
        assertArrayEquals(new int[] {1, 1, 1}, fromCatalog.rows());
    }

    @Test
    void stripResultIsImmutable() {
        int[] input = {1, 2, 3};
        SlotsOutcome outcome = SlotsSpinGenerator.outcomeFromStops(input, 3, SlotsVariance.BALANCED);
        SlotsSpinGenerator.StripResult result = new SlotsSpinGenerator.StripResult(input, outcome);

        input[0] = 77;
        assertArrayEquals(new int[] {1, 2, 3}, result.stops());

        int[] returned = result.stops();
        returned[0] = 88;
        assertArrayEquals(new int[] {1, 2, 3}, result.stops());
    }

    @Test
    void generateFromStripsStopsAreIndependentOfCallerMutation() {
        SlotsSpinGenerator.StripResult first =
            SlotsSpinGenerator.generateFromStrips(3, 3, bound -> 5, SlotsVariance.BALANCED);
        int[] stops = first.stops();
        stops[0] = -1;
        assertEquals(5, first.stops()[0], "mutating a returned stops array must not affect the generator's own record");
    }

    @Test
    void spinAttemptAcceptedStopsAreImmutable() {
        SlotsSpinController controller = new SlotsSpinController();
        SlotsPaytable paytable = SlotsPaytable.forConfig(3, 0.03, SlotsVariance.BALANCED);
        SlotsSpinController.SpinAttempt attempt = controller.trySpin(
            10L, 3, 3, 1, false, paytable, bound -> 5, SlotsUnderwriting.unlimited(), amount -> true);
        SlotsSpinController.SpinAttempt.Accepted accepted = (SlotsSpinController.SpinAttempt.Accepted) attempt;

        int[] returned = accepted.stops();
        returned[0] = -1;
        assertEquals(5, accepted.stops()[0], "mutating a returned stops array must not affect the accepted result");
    }

    @Test
    void committedResultIsImmutable() {
        int[] input = {4, 5, 6};
        SlotsCommittedResult result = SlotsCommittedResult.fromStops(3, 3, SlotsVariance.BALANCED, input);

        input[0] = -1;
        assertArrayEquals(new int[] {4, 5, 6}, result.stops(),
            "mutating the constructor's input array must not affect the committed result");

        int[] returned = result.stops();
        returned[1] = -1;
        assertArrayEquals(new int[] {4, 5, 6}, result.stops(),
            "mutating a returned array must not affect the committed result");
    }

    @Test
    void controllerRetainedStopsReconstructTheExactCurrentOutcome() {
        SlotsSpinController controller = new SlotsSpinController();
        SlotsPaytable paytable = SlotsPaytable.forConfig(5, 0.03, SlotsVariance.BALANCED);
        controller.trySpin(10L, 5, 5, 9, false, paytable, bound -> 17, SlotsUnderwriting.unlimited(), amount -> true);

        SlotsCommittedResult committed = controller.currentResult();
        SlotsOutcome reconstructed = SlotsSpinGenerator.outcomeFromStops(
            committed.stops(), committed.visibleRows(), committed.variance());

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                assertEquals(controller.currentOutcome().symbolAt(row, col), reconstructed.symbolAt(row, col));
            }
        }
    }

    @Test
    void changingHeightForTheSameStopsChangesOnlyTheCenteredOffsets() {
        int[] stops = {10, 50, 90};
        SlotsCommittedResult atFive = SlotsCommittedResult.fromStops(3, 5, SlotsVariance.BALANCED, stops);
        SlotsCommittedResult atThree = atFive.withVisibleRows(3);
        SlotsCommittedResult atOne = atFive.withVisibleRows(1);

        assertArrayEquals(stops, atThree.stops());
        assertArrayEquals(stops, atOne.stops());
        for (int col = 0; col < 3; col++) {
            assertEquals(atFive.outcome().symbolAt(2, col), atThree.outcome().symbolAt(1, col));
            assertEquals(atFive.outcome().symbolAt(2, col), atOne.outcome().symbolAt(0, col));
        }
    }
}
