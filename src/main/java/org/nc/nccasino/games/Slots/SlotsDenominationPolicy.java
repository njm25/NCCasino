package org.nc.nccasino.games.Slots;

/** Pure selection policy for skipping denominations that cannot be safely paid in the active currency mode. */
public final class SlotsDenominationPolicy {

    private SlotsDenominationPolicy() {
    }

    public static boolean isAllowed(double denomination, int activeLines, boolean itemMode, SlotsPaytable paytable) {
        if (!Double.isFinite(denomination) || paytable == null) {
            return false;
        }
        long units = Math.max(0L, Math.round(denomination));
        if (units <= 0) {
            return false;
        }
        try {
            SlotsMath.totalBet(units, activeLines);
            long maximumPayout = SlotsMath.maxPossiblePayout(units, activeLines, paytable);
            return !itemMode || maximumPayout <= SlotsMath.MAX_ITEM_MODE_PAYOUT;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    /**
     * Cycles in {@code delta}'s direction to the next safe denomination.
     * If no alternative is safe, the current index is retained so the spin
     * controller can surface the precise rejection reason.
     */
    public static int nextAllowedIndex(
        double[] denominations,
        int currentIndex,
        int delta,
        int activeLines,
        boolean itemMode,
        SlotsPaytable paytable
    ) {
        if (denominations == null || denominations.length == 0) {
            throw new IllegalArgumentException("denominations must not be empty");
        }
        if (currentIndex < 0 || currentIndex >= denominations.length) {
            throw new IllegalArgumentException("currentIndex is out of range");
        }
        int direction = Integer.compare(delta, 0);
        if (direction == 0) {
            return currentIndex;
        }
        for (int offset = 1; offset <= denominations.length; offset++) {
            int candidate = Math.floorMod(currentIndex + direction * offset, denominations.length);
            if (isAllowed(denominations[candidate], activeLines, itemMode, paytable)) {
                return candidate;
            }
        }
        return currentIndex;
    }
}
