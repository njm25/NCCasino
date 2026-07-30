package org.nc.nccasino.currency;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Assigns configured chip denominations to their fixed inventory slots.
 *
 * <p>Gameplay resolves a chip from its slot, never from player-facing text.
 * This keeps translated or reformatted labels from changing wager values.</p>
 */
public final class ChipSlots {

    public static final int FIRST_SLOT = 47;
    public static final int LAST_SLOT = 51;

    private ChipSlots() {
    }

    public static Map<Integer, Double> assign(Collection<Double> configuredValues) {
        Map<Integer, Double> slots = new LinkedHashMap<>();
        if (configuredValues == null) {
            return slots;
        }

        TreeSet<Double> sortedValues = new TreeSet<>();
        for (Double value : configuredValues) {
            if (value != null && Double.isFinite(value) && value > 0) {
                sortedValues.add(value);
            }
        }

        int slot = FIRST_SLOT;
        for (double value : sortedValues) {
            if (slot > LAST_SLOT) {
                break;
            }
            slots.put(slot++, value);
        }
        return slots;
    }

    public static boolean isChipSlot(int slot) {
        return slot >= FIRST_SLOT && slot <= LAST_SLOT;
    }
}
