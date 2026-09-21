package org.nc.nccasino.games.Slots;

/**
 * The Paytable view's informational rail: canvas slots 36-44, each sitting
 * directly above the bottom-row control it explains (45-53).
 *
 * <p>The alignment is the whole point, so it is derived rather than
 * hand-listed: rail slot {@code n} is always exactly nine slots above
 * control slot {@code n + 9}. The rail exists only in the Paytable view, is
 * rendered in one material
 * ({@link SlotsControlPresentation.Role#INFO_RAIL} -- a hopper, whose funnel
 * narrows downward toward the control it explains) that is not one of this
 * UI's clickable materials, and every click on it is cancelled.
 */
public final class SlotsInfoRail {

    /** The first rail slot (directly above Exit at 45). */
    public static final int FIRST_SLOT = 36;

    /** The last rail slot (directly above Saved Profiles at 53). */
    public static final int LAST_SLOT = 44;

    private SlotsInfoRail() {
    }

    /** Whether {@code slot} is part of the rail. */
    public static boolean isRailSlot(int slot) {
        return slot >= FIRST_SLOT && slot <= LAST_SLOT;
    }

    /** The bottom-row control slot the given rail slot explains. */
    public static int controlSlotFor(int railSlot) {
        if (!isRailSlot(railSlot)) {
            throw new IllegalArgumentException(
                "rail slots are " + FIRST_SLOT + "-" + LAST_SLOT + "; got " + railSlot);
        }
        return railSlot + SlotsGeometry.INVENTORY_WIDTH;
    }

    /** The rail slot that explains the given bottom-row control slot. */
    public static int railSlotFor(int controlSlot) {
        int railSlot = controlSlot - SlotsGeometry.INVENTORY_WIDTH;
        if (!isRailSlot(railSlot)) {
            throw new IllegalArgumentException(
                "no rail slot aligns with control slot " + controlSlot);
        }
        return railSlot;
    }

    /** Every rail slot, ascending. */
    public static int[] slots() {
        int[] slots = new int[LAST_SLOT - FIRST_SLOT + 1];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = FIRST_SLOT + i;
        }
        return slots;
    }
}
