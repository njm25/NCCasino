package org.nc.nccasino.games.Roulette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RouletteWheelLayoutTest {

    @Test
    void sameQuadrantAndOffsetAlwaysProduceEqualIndependentMaps() {
        // Simulates two independently-rendered views of one shared frame:
        // neither call may see or mutate the other's result.
        Map<Integer, Integer> viewA = RouletteWheelLayout.numbersForQuadrant(RouletteWheelLayout.TOP_RIGHT, 5);
        Map<Integer, Integer> viewB = RouletteWheelLayout.numbersForQuadrant(RouletteWheelLayout.TOP_RIGHT, 5);

        assertEquals(viewA, viewB);
        assertNotSame(viewA, viewB);

        int unmodifiedValue = viewB.get(27);
        viewA.put(27, -999);
        assertEquals(unmodifiedValue, viewB.get(27));
    }

    @Test
    void differentOffsetsRotateTheDisplayedNumbers() {
        Map<Integer, Integer> atZero = RouletteWheelLayout.numbersForQuadrant(RouletteWheelLayout.BOTTOM_RIGHT, 0);
        Map<Integer, Integer> atFive = RouletteWheelLayout.numbersForQuadrant(RouletteWheelLayout.BOTTOM_RIGHT, 5);

        assertTrue(!atZero.equals(atFive));
    }

    @Test
    void extraSlotsMirrorTheirMainSlotsNumber() {
        Map<Integer, Integer> quadrant = RouletteWheelLayout.numbersForQuadrant(RouletteWheelLayout.TOP_RIGHT, 0);

        // Slot 33 owns extra slots 24 and 25 in the top-right quadrant.
        assertEquals(quadrant.get(33), quadrant.get(24));
        assertEquals(quadrant.get(33), quadrant.get(25));
    }

    @Test
    void topRightQuadrantStartsAtOffsetPlus27() {
        for (int offset = 0; offset < RouletteWheelLayout.WHEEL_NUMBERS.size(); offset++) {
            Map<Integer, Integer> quadrant = RouletteWheelLayout.numbersForQuadrant(RouletteWheelLayout.TOP_RIGHT, offset);
            int expectedFirst = RouletteWheelLayout.WHEEL_NUMBERS.get(
                Math.floorMod(offset + 27, RouletteWheelLayout.WHEEL_NUMBERS.size())
            );
            assertEquals(expectedFirst, quadrant.get(27));
        }
    }

    @Test
    void findWinningNumberQuadrantIsPureAndDeterministic() {
        // Characterizes existing behavior as-is: repeated calls with the
        // same (number, offset) always agree. Note this quadrant guess is
        // not guaranteed to actually contain the number in every case
        // (pre-existing behavior of the code this was extracted from,
        // preserved verbatim rather than "corrected" here) -- the search
        // it feeds is self-correcting elsewhere in the caller.
        for (int offset = 0; offset < RouletteWheelLayout.WHEEL_NUMBERS.size(); offset++) {
            for (int number : RouletteWheelLayout.WHEEL_NUMBERS) {
                int first = RouletteWheelLayout.findWinningNumberQuadrant(number, offset);
                int second = RouletteWheelLayout.findWinningNumberQuadrant(number, offset);
                assertEquals(first, second);
            }
        }
    }

    @Test
    void findWinningNumberQuadrantGuessDoesNotAlwaysMatchTheDisplayedGrid() {
        // Pins down a pre-existing discrepancy in the code this was
        // extracted from, verbatim: findWinningNumberQuadrant's quadrant
        // boundaries (0-8/9-17/18-26/27-36 by adjusted index) don't line up
        // with the relative wheel positions numbersForQuadrant actually
        // walks for quadrant 4 (which covers {0, 29..36}, not {0..8}). This
        // is intentionally preserved rather than corrected -- the caller's
        // own re-check loop is what actually keeps the display consistent
        // with the settled ball. If this test starts failing, the quadrant
        // math changed; confirm that's intended before "fixing" it.
        int quadrant = RouletteWheelLayout.findWinningNumberQuadrant(32, 0);
        assertEquals(RouletteWheelLayout.BOTTOM_RIGHT, quadrant);
        Map<Integer, Integer> grid = RouletteWheelLayout.numbersForQuadrant(quadrant, 0);
        assertFalse(grid.containsValue(32));
    }

    @Test
    void colorClassificationMatchesStandardWheel() {
        assertEquals(RouletteWheelLayout.Color.GREEN, RouletteWheelLayout.colorOf(0));
        assertEquals(RouletteWheelLayout.Color.RED, RouletteWheelLayout.colorOf(1));
        assertEquals(RouletteWheelLayout.Color.BLACK, RouletteWheelLayout.colorOf(2));
        assertTrue(RouletteWheelLayout.isRed(32));
        assertTrue(RouletteWheelLayout.isBlack(35));
    }

    @Test
    void everyWheelNumberHasExactlyOneColor() {
        for (int number : RouletteWheelLayout.WHEEL_NUMBERS) {
            boolean red = RouletteWheelLayout.isRed(number);
            boolean black = RouletteWheelLayout.isBlack(number);
            assertTrue(!(red && black), "number " + number + " classified as both red and black");
        }
    }
}
