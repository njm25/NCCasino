package org.nc.nccasino.games.Roulette;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, stateless roulette wheel topology: the physical order of numbers
 * around the wheel and how the four-quadrant GUI grid maps onto it at a
 * given wheel offset. Contains no Bukkit types and no localized text, so
 * every viewer's rendering of the same (quadrant, offset) pair is
 * identical regardless of locale or which view computed it.
 */
public final class RouletteWheelLayout {

    public static final List<Integer> WHEEL_NUMBERS = Collections.unmodifiableList(Arrays.asList(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5,
        24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    ));

    public static final int TOP_RIGHT = 1;
    public static final int TOP_LEFT = 2;
    public static final int BOTTOM_LEFT = 3;
    public static final int BOTTOM_RIGHT = 4;

    private static final int NUMBERS_PER_QUADRANT = 9;

    private static final int[] RED_NUMBERS =
        {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};

    private static final Map<Integer, int[]> MAIN_SLOTS = new HashMap<>();
    private static final Map<Integer, Integer> START_OFFSET = new HashMap<>();
    private static final Map<Integer, Map<Integer, int[]>> EXTRA_SLOTS = new HashMap<>();

    static {
        MAIN_SLOTS.put(TOP_RIGHT, new int[]{27, 28, 29, 30, 31, 32, 33, 43, 53});
        MAIN_SLOTS.put(TOP_LEFT, new int[]{45, 37, 29, 30, 31, 32, 33, 34, 35});
        MAIN_SLOTS.put(BOTTOM_LEFT, new int[]{0, 10, 20, 21, 22, 23, 24, 25, 26});
        MAIN_SLOTS.put(BOTTOM_RIGHT, new int[]{18, 19, 20, 21, 22, 23, 24, 16, 8});

        START_OFFSET.put(TOP_RIGHT, 27);
        START_OFFSET.put(TOP_LEFT, 18);
        START_OFFSET.put(BOTTOM_LEFT, 9);
        START_OFFSET.put(BOTTOM_RIGHT, 0);

        Map<Integer, int[]> topRight = new HashMap<>();
        topRight.put(27, new int[]{18});
        topRight.put(28, new int[]{19});
        topRight.put(29, new int[]{20});
        topRight.put(30, new int[]{21});
        topRight.put(31, new int[]{22});
        topRight.put(32, new int[]{23});
        topRight.put(33, new int[]{24, 25});
        topRight.put(43, new int[]{34, 35});
        topRight.put(53, new int[]{44});
        EXTRA_SLOTS.put(TOP_RIGHT, topRight);

        Map<Integer, int[]> topLeft = new HashMap<>();
        topLeft.put(45, new int[]{36});
        topLeft.put(37, new int[]{28, 27});
        topLeft.put(29, new int[]{20, 19});
        topLeft.put(30, new int[]{21});
        topLeft.put(31, new int[]{22});
        topLeft.put(32, new int[]{23});
        topLeft.put(33, new int[]{24});
        topLeft.put(34, new int[]{25});
        topLeft.put(35, new int[]{26});
        EXTRA_SLOTS.put(TOP_LEFT, topLeft);

        Map<Integer, int[]> bottomLeft = new HashMap<>();
        bottomLeft.put(0, new int[]{9});
        bottomLeft.put(10, new int[]{19, 18});
        bottomLeft.put(20, new int[]{29, 28});
        bottomLeft.put(21, new int[]{30});
        bottomLeft.put(22, new int[]{31});
        bottomLeft.put(23, new int[]{32});
        bottomLeft.put(24, new int[]{33});
        bottomLeft.put(25, new int[]{34});
        bottomLeft.put(26, new int[]{35});
        EXTRA_SLOTS.put(BOTTOM_LEFT, bottomLeft);

        Map<Integer, int[]> bottomRight = new HashMap<>();
        bottomRight.put(18, new int[]{27});
        bottomRight.put(19, new int[]{28});
        bottomRight.put(20, new int[]{29});
        bottomRight.put(21, new int[]{30});
        bottomRight.put(22, new int[]{31});
        bottomRight.put(23, new int[]{32});
        bottomRight.put(24, new int[]{33, 34});
        bottomRight.put(16, new int[]{25, 26});
        bottomRight.put(8, new int[]{17});
        EXTRA_SLOTS.put(BOTTOM_RIGHT, bottomRight);
    }

    private RouletteWheelLayout() {
    }

    public enum Color { RED, BLACK, GREEN }

    /**
     * Deterministic slot-to-number grid for one quadrant at one wheel
     * offset, including slots that duplicate a neighboring main slot's
     * number. Two calls with the same arguments always return equal (but
     * independent) maps.
     */
    public static Map<Integer, Integer> numbersForQuadrant(int quadrant, int globalOffset) {
        return numbersForQuadrant(quadrant, globalOffset, quadrant);
    }

    /**
     * Same slot grid as {@link #numbersForQuadrant(int, int)}, but the
     * ascend-vs-descend wheel-walk direction is taken from
     * {@code directionQuadrant} instead of {@code slotQuadrant}. This
     * preserves a legacy quirk in the original inline implementation: the
     * caller can snapshot which slots to fill from the quadrant in effect
     * before a same-tick quadrant switch, while the direction was (and
     * still is here) read from whatever quadrant is in effect by the time
     * the render loop actually runs. Pass the same value for both
     * parameters for the ordinary case where no such switch happens
     * in between.
     */
    public static Map<Integer, Integer> numbersForQuadrant(int slotQuadrant, int globalOffset, int directionQuadrant) {
        int[] mainSlots = requireMainSlots(slotQuadrant);
        int startOffset = START_OFFSET.get(slotQuadrant);
        Map<Integer, int[]> extraSlotsMap = EXTRA_SLOTS.get(slotQuadrant);
        int startPosition = Math.floorMod(globalOffset + startOffset, WHEEL_NUMBERS.size());
        boolean ascending = (directionQuadrant == TOP_RIGHT || directionQuadrant == TOP_LEFT);

        Map<Integer, Integer> slotToNumber = new HashMap<>();
        for (int i = 0; i < mainSlots.length; i++) {
            int wheelPosition = ascending
                ? Math.floorMod(startPosition + i, WHEEL_NUMBERS.size())
                : Math.floorMod(startPosition - i, WHEEL_NUMBERS.size());
            int number = WHEEL_NUMBERS.get(wheelPosition);
            slotToNumber.put(mainSlots[i], number);

            int[] extraSlots = extraSlotsMap.get(mainSlots[i]);
            if (extraSlots != null) {
                for (int extraSlot : extraSlots) {
                    slotToNumber.put(extraSlot, number);
                }
            }
        }
        return slotToNumber;
    }

    public static Map<Integer, int[]> extraSlotsForQuadrant(int quadrant) {
        return EXTRA_SLOTS.get(quadrant);
    }

    /** Returns a fresh copy each call; safe for a caller to treat as its own array. */
    public static int[] mainSlotsForQuadrant(int quadrant) {
        return requireMainSlots(quadrant).clone();
    }

    /**
     * Which quadrant currently displays {@code winningNumber} at the given
     * wheel offset.
     */
    public static int findWinningNumberQuadrant(int winningNumber, int globalOffset) {
        int winningIndex = WHEEL_NUMBERS.indexOf(winningNumber);
        int adjustedWinningIndex = Math.floorMod(winningIndex - globalOffset, WHEEL_NUMBERS.size());

        if (adjustedWinningIndex < NUMBERS_PER_QUADRANT) {
            return BOTTOM_RIGHT;
        } else if (adjustedWinningIndex < NUMBERS_PER_QUADRANT * 2) {
            return BOTTOM_LEFT;
        } else if (adjustedWinningIndex < NUMBERS_PER_QUADRANT * 3) {
            return TOP_LEFT;
        } else {
            return TOP_RIGHT;
        }
    }

    public static Color colorOf(int number) {
        if (number == 0) {
            return Color.GREEN;
        }
        for (int red : RED_NUMBERS) {
            if (red == number) {
                return Color.RED;
            }
        }
        return Color.BLACK;
    }

    public static boolean isRed(int number) {
        return colorOf(number) == Color.RED;
    }

    public static boolean isBlack(int number) {
        return colorOf(number) == Color.BLACK;
    }

    private static int[] requireMainSlots(int quadrant) {
        int[] slots = MAIN_SLOTS.get(quadrant);
        if (slots == null) {
            throw new IllegalArgumentException("Invalid quadrant index: " + quadrant);
        }
        return slots;
    }
}
