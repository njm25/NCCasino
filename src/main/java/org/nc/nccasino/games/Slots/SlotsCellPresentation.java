package org.nc.nccasino.games.Slots;

/**
 * Which of the three ways {@code SlotsMachine} can render one reel cell,
 * extracted as a pure decision so it can be tested without a live Bukkit
 * inventory.
 *
 * <ul>
 *   <li>{@link #NEUTRAL} -- the pregame/reset placeholder ({@code symbol ==
 *   null}). Never evaluated as an outcome, never demo-labelled (there is
 *   nothing to disclaim), never a rolled SEEDS.
 *   <li>{@link #DEMO} -- a real rolled symbol shown while a Demo Spin is
 *   animating or displaying its result. Must always carry the demo/no-currency
 *   disclaimer, on every cell including SEEDS and including matched winning
 *   cells -- Section 1/12 of the redesign audit require this to be
 *   unmistakable everywhere, not only in the end-of-spin chat message.
 *   <li>{@link #PAID} -- a real rolled symbol from an actual paid spin (or the
 *   idle canvas showing the last one). Never carries demo lore.
 * </ul>
 */
public enum SlotsCellPresentation {
    NEUTRAL,
    DEMO,
    PAID;

    /**
     * @param symbol the cell's rolled symbol, or {@code null} for the
     *     pregame/reset placeholder
     * @param demo whether the animation currently painting this cell is a
     *     Demo Spin
     */
    public static SlotsCellPresentation of(SlotsSymbol symbol, boolean demo) {
        if (symbol == null) {
            return NEUTRAL;
        }
        return demo ? DEMO : PAID;
    }

    /** Whether this presentation must carry the demo/no-currency disclaimer lore. */
    public boolean isDemoLabelled() {
        return this == DEMO;
    }
}
