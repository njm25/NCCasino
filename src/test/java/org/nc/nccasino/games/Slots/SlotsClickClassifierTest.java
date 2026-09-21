package org.nc.nccasino.games.Slots;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsClickClassifierTest {

    @Test
    void ordinaryLeftAndRightAreAccepted() {
        assertTrue(SlotsClickClassifier.isOrdinaryClick(ClickType.LEFT));
        assertTrue(SlotsClickClassifier.isOrdinaryClick(ClickType.RIGHT));
    }

    @Test
    void everyOtherClickTypeIsRejected() {
        for (ClickType type : ClickType.values()) {
            if (type == ClickType.LEFT || type == ClickType.RIGHT) {
                continue;
            }
            assertFalse(SlotsClickClassifier.isOrdinaryClick(type),
                type + " must not be treated as an ordinary click");
        }
    }

    @Test
    void shiftClicksAreNotOrdinaryEvenThoughBukkitCallsThemLeftOrRightClicks() {
        // The exact bug this class exists to prevent: Bukkit's own
        // isRightClick()/isLeftClick() both return true for the shift
        // variants too, which is what let "not right" silently mean
        // "left" for every unsupported click type.
        assertFalse(SlotsClickClassifier.isOrdinaryClick(ClickType.SHIFT_LEFT));
        assertFalse(SlotsClickClassifier.isOrdinaryClick(ClickType.SHIFT_RIGHT));
    }

    @Test
    void unsupportedClickTypesAreRejected() {
        assertFalse(SlotsClickClassifier.isOrdinaryClick(ClickType.MIDDLE));
        assertFalse(SlotsClickClassifier.isOrdinaryClick(ClickType.DOUBLE_CLICK));
        assertFalse(SlotsClickClassifier.isOrdinaryClick(ClickType.DROP));
        assertFalse(SlotsClickClassifier.isOrdinaryClick(ClickType.CONTROL_DROP));
        assertFalse(SlotsClickClassifier.isOrdinaryClick(ClickType.NUMBER_KEY));
        assertFalse(SlotsClickClassifier.isOrdinaryClick(ClickType.WINDOW_BORDER_LEFT));
        assertFalse(SlotsClickClassifier.isOrdinaryClick(ClickType.WINDOW_BORDER_RIGHT));
        assertFalse(SlotsClickClassifier.isOrdinaryClick(ClickType.UNKNOWN));
    }

    @Test
    void leftIsForwardAndRightIsBackward() {
        assertEquals(1, SlotsClickClassifier.cycleDirection(ClickType.LEFT));
        assertEquals(-1, SlotsClickClassifier.cycleDirection(ClickType.RIGHT));
    }

    @Test
    void cycleDirectionRejectsAnyUnsupportedClickTypeRatherThanGuessing() {
        assertThrows(IllegalArgumentException.class, () -> SlotsClickClassifier.cycleDirection(ClickType.SHIFT_LEFT));
        assertThrows(IllegalArgumentException.class, () -> SlotsClickClassifier.cycleDirection(ClickType.MIDDLE));
        assertThrows(IllegalArgumentException.class, () -> SlotsClickClassifier.cycleDirection(ClickType.DOUBLE_CLICK));
    }
}
