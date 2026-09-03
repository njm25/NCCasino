package org.nc.nccasino.games.Slots;

/**
 * Pure decision for how a Paylines change should repaint the canvas,
 * extracted so the per-{@link SlotsUiView} matrix can be tested without a
 * live Bukkit inventory.
 */
public final class SlotsLineChangeRepaint {

    private SlotsLineChangeRepaint() {
    }

    public enum Mode { GAME, PAYTABLE }

    public enum Action {
        /** Game View, an ordinary (non-wrap) change: flash only the exact line that (de)activated. */
        FLASH_SINGLE_LINE,
        /** Game View, the 1&lt;-&gt;max wrap: a distinct notice, then an ordinary canvas repaint -- never a single-line flash. */
        ANNOUNCE_WRAP_AND_REPAINT_CANVAS,
        /**
         * Paytable View: every change immediately updates the paytable for
         * the new line count, and never shows the green/black path flash.
         */
        REPAINT_CANVAS
    }

    public static Action decide(Mode mode, boolean wrapped) {
        return switch (mode) {
            case GAME -> wrapped ? Action.ANNOUNCE_WRAP_AND_REPAINT_CANVAS : Action.FLASH_SINGLE_LINE;
            case PAYTABLE -> Action.REPAINT_CANVAS;
        };
    }
}
