package org.nc.nccasino.games.Slots;

/**
 * Symmetric, glanceable layout for the Paytable's upper 45-slot canvas.
 *
 * <p>The paytable proper owns rows 0-3. Rows 0 and 3 form a rainbow marquee,
 * while the useful content is centred in the dark two-row interior:
 *
 * <pre>
 *   r  r  r  r  M  r  r  r  r      row 0: rainbow header + machine summary
 *   .  .  C  L  B  D  7  .  .      row 1: the five paying-symbol cards
 *   .  .  S  .  L  .  V  .  .      row 2: Seeds, Legend, Volatility
 *   r  r  r  r  r  r  r  r  r      row 3: rainbow footer
 *   H  H  H  H  H  H  H  H  H      row 4: hopper control-information rail
 * </pre>
 *
 * <p>This deliberately avoids separate odds cards and a detached jackpot
 * card. Each symbol has one obvious home and all secondary reading is grouped
 * into a smaller, balanced row below it.
 */
public final class SlotsPaytableLayout {

    /** Canvas row 4 belongs to the hopper rail, not the paytable proper. */
    public static final int PAYTABLE_ROWS = SlotsGeometry.CANVAS_ROWS - 1;

    public static final int MACHINE_SLOT = 4;
    public static final int SEEDS_SLOT = 20;
    public static final int LEGEND_SLOT = 22;
    public static final int VOLATILITY_SLOT = 24;

    private static final int SYMBOL_ROW = 1;
    private static final int SYMBOL_ROW_CAPACITY = SlotsGeometry.INVENTORY_WIDTH;

    private SlotsPaytableLayout() {
    }

    /** Rows 0 and 3 are the marquee frame surrounding the two content rows. */
    public static boolean isRainbowFrameSlot(int slot) {
        int row = slot / SlotsGeometry.INVENTORY_WIDTH;
        return slot >= 0 && slot < PAYTABLE_ROWS * SlotsGeometry.INVENTORY_WIDTH
            && (row == 0 || row == PAYTABLE_ROWS - 1);
    }

    /** The number of paying symbols that fit in the single centred band. */
    public static int cardCapacity() {
        return SYMBOL_ROW_CAPACITY;
    }

    /**
     * Centres the paying-symbol cards in one horizontal row.
     *
     * @throws IllegalArgumentException if the requested cards cannot fit in
     *     that row; silently dropping a paying symbol would be misleading
     */
    public static int[] symbolCardSlots(int cardCount) {
        if (cardCount < 0) {
            throw new IllegalArgumentException("cardCount must not be negative; got " + cardCount);
        }
        if (cardCount > cardCapacity()) {
            throw new IllegalArgumentException(
                "the paytable row fits at most " + cardCapacity() + " symbol cards; got " + cardCount);
        }

        int firstColumn = (SlotsGeometry.INVENTORY_WIDTH - cardCount) / 2;
        int[] slots = new int[cardCount];
        for (int i = 0; i < cardCount; i++) {
            slots[i] = SYMBOL_ROW * SlotsGeometry.INVENTORY_WIDTH + firstColumn + i;
        }
        return slots;
    }

    /**
     * Whether this Paytable canvas slot carries content rather than housing.
     * Everything else in rows 0-3 is plain rainbow pane, which is the shared
     * Golden Slumbers play/pause control.
     *
     * @param cardCount how many paying symbols are on show, since the symbol
     *     band is centred and so moves with that count
     */
    public static boolean isContentSlot(int slot, int cardCount) {
        if (slot == MACHINE_SLOT || slot == SEEDS_SLOT
            || slot == LEGEND_SLOT || slot == VOLATILITY_SLOT) {
            return true;
        }
        for (int card : symbolCardSlots(cardCount)) {
            if (card == slot) {
                return true;
            }
        }
        return false;
    }

    /** Every canvas slot the paytable proper owns (rows 0-3), ascending. */
    public static int[] paytableCanvasSlots() {
        int[] slots = new int[PAYTABLE_ROWS * SlotsGeometry.INVENTORY_WIDTH];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }
}
