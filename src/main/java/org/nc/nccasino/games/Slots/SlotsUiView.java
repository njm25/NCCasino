package org.nc.nccasino.games.Slots;

/**
 * The machine's explicit canvas views, replacing the earlier general-purpose
 * Info Mode and its persistent Preview Line surface.
 *
 * <p>{@link #GAME} is the ordinary reel canvas (idle, mid-spin, or showing
 * the last result). {@link #PAYTABLE}, {@link #PROFILES} and
 * {@link #AUTO_SETTINGS} each repaint only the upper 45-slot canvas as their
 * own surface -- the bottom control row (45-53) stays exactly as it is in
 * {@link #GAME}, except for the one slot each view swaps in place for Back
 * to Game (see {@link #backToGameSlot()}).
 *
 * <p>Only {@link #GAME} is a playable view: the other three are modal, and
 * every geometry/wager control is inert while one of them owns the canvas,
 * with the single deliberate exception of {@link #PAYTABLE}, whose displayed
 * numbers are defined to track the live configuration.
 */
public enum SlotsUiView {
    /** The reel canvas. */
    GAME(-1),
    /** The condensed symbol-card paytable; slot 48 (the Book) becomes Back to Game. */
    PAYTABLE(48),
    /** The saved-profile list; slot 53 (the Ender Chest) becomes Back to Game. */
    PROFILES(53),
    /** The Auto Spin settings menu; slot 50 (the Clock) becomes Back to Game. */
    AUTO_SETTINGS(50);

    private final int backToGameSlot;

    SlotsUiView(int backToGameSlot) {
        this.backToGameSlot = backToGameSlot;
    }

    /**
     * The single bottom-row slot this view replaces with Back to Game, or
     * {@code -1} for {@link #GAME}, which replaces nothing.
     */
    public int backToGameSlot() {
        return backToGameSlot;
    }

    /** Whether this view owns the canvas modally -- i.e. anything but {@link #GAME}. */
    public boolean isModal() {
        return this != GAME;
    }

    /**
     * Whether a geometry/wager control click is allowed to change the machine
     * while this view is open. Only {@link #GAME} and {@link #PAYTABLE} allow
     * it: the Paytable's displayed multipliers and returns are defined to
     * track the live configuration, so changing Height/Reels/Paylines/Wager
     * there stays in the Paytable and refreshes it. {@link #PROFILES} and
     * {@link #AUTO_SETTINGS} are modal editors and must never be changed out
     * from under by an accidental bottom-row click.
     */
    public boolean allowsConfigurationChanges() {
        return this == GAME || this == PAYTABLE;
    }
}
