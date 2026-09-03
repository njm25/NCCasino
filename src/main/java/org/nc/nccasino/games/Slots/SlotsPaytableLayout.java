package org.nc.nccasino.games.Slots;

/**
 * Where the redesigned Paytable view puts each of its pieces inside the
 * upper 45-slot canvas.
 *
 * <p>The bottom canvas row (slots 36-44) is reserved for the informational
 * rail that aligns with the bottom control row, so the paytable proper only
 * ever owns canvas rows 0-3 (slots 0-35):
 *
 * <pre>
 *   I  .  .  .  L  .  .  .  M      row 0: info 1, Legend, Current Machine
 *   I  c  c  c  c  c  c  c  c      rows 1-3: the symbol-card block
 *   I  c  c  c  c  c  c  c  c
 *   I  c  c  c  c  c  c  c  c
 *   R  R  R  R  R  R  R  R  R      row 4: the informational rail (36-44)
 * </pre>
 *
 * <p>Column 0 is the narrow explanatory column; the symbol cards are packed
 * into the 3x8 block to its right and centred both ways, so the layout stays
 * symmetric and correct if the authoritative paying-symbol count ever
 * changes -- it is derived from the count, never from a hand-written slot
 * table.
 */
public final class SlotsPaytableLayout {

    /** Canvas row 4 (slots 36-44) belongs to the informational rail, not the paytable. */
    public static final int PAYTABLE_ROWS = SlotsGeometry.CANVAS_ROWS - 1;

    /** Slot 4: the single centred Legend that explains the Run / Multiplier / Return format. */
    public static final int LEGEND_SLOT = 4;

    /** Slot 8: the current machine's live summary, balancing the Legend on the top row. */
    public static final int MACHINE_SLOT = 8;

    /** The explanatory column's four slots, top to bottom. */
    private static final int[] INFO_COLUMN = {0, 9, 18, 27};

    /** The symbol-card block: canvas rows 1-3, columns 1-8. */
    private static final int CARD_FIRST_ROW = 1;
    private static final int CARD_ROW_COUNT = 3;
    private static final int CARD_FIRST_COLUMN = 1;
    private static final int CARD_COLUMN_COUNT = SlotsGeometry.INVENTORY_WIDTH - CARD_FIRST_COLUMN;

    private SlotsPaytableLayout() {
    }

    /** The four left-column explanatory cards, top to bottom. */
    public static int[] infoColumnSlots() {
        return INFO_COLUMN.clone();
    }

    /** How many symbol cards this layout can place at once. */
    public static int cardCapacity() {
        return CARD_ROW_COUNT * CARD_COLUMN_COUNT;
    }

    /**
     * The inventory slots for {@code cardCount} symbol cards, in order.
     *
     * <p>The cards fill as few rows as they need, those rows are centred
     * vertically in the three available rows, and each row's cards are
     * centred horizontally in the eight available columns -- so five cards
     * become one centred band through the canvas's true centre, and a larger
     * set grows outward symmetrically instead of piling up in a corner.
     *
     * @throws IllegalArgumentException if {@code cardCount} exceeds
     *     {@link #cardCapacity()} -- silently dropping a paying symbol from
     *     the paytable would be worse than failing loudly
     */
    public static int[] symbolCardSlots(int cardCount) {
        if (cardCount < 0) {
            throw new IllegalArgumentException("cardCount must not be negative; got " + cardCount);
        }
        if (cardCount > cardCapacity()) {
            throw new IllegalArgumentException(
                "the paytable canvas fits at most " + cardCapacity() + " symbol cards; got " + cardCount);
        }
        if (cardCount == 0) {
            return new int[0];
        }

        int rowsUsed = Math.min(CARD_ROW_COUNT, ceilDiv(cardCount, CARD_COLUMN_COUNT));
        int firstRow = CARD_FIRST_ROW + (CARD_ROW_COUNT - rowsUsed) / 2;

        int[] slots = new int[cardCount];
        int placed = 0;
        for (int row = 0; row < rowsUsed; row++) {
            int remaining = cardCount - placed;
            int rowsLeft = rowsUsed - row;
            int inThisRow = ceilDiv(remaining, rowsLeft);
            int firstColumn = CARD_FIRST_COLUMN + (CARD_COLUMN_COUNT - inThisRow) / 2;
            for (int i = 0; i < inThisRow; i++) {
                slots[placed++] = (firstRow + row) * SlotsGeometry.INVENTORY_WIDTH + firstColumn + i;
            }
        }
        return slots;
    }

    /** Every canvas slot the paytable proper owns (rows 0-3), ascending. */
    public static int[] paytableCanvasSlots() {
        int[] slots = new int[PAYTABLE_ROWS * SlotsGeometry.INVENTORY_WIDTH];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
