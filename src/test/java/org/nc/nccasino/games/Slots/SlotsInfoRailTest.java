package org.nc.nccasino.games.Slots;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Paytable view's informational rail: the nine canvas slots (36-44) that
 * sit directly above the nine bottom-row controls (45-53).
 *
 * <p>Two things are load-bearing and both are checked here: the rail must
 * align exactly, control for control, so a tile never explains the wrong
 * control; and a click on the rail must never reach a control action, so the
 * rail can never behave like a second row of controls.
 */
class SlotsInfoRailTest {

    @Test
    void theRailIsExactlyTheNineSlotsOfCanvasRowFour() {
        assertEquals(36, SlotsInfoRail.FIRST_SLOT);
        assertEquals(44, SlotsInfoRail.LAST_SLOT);
        assertArrayEqualsInts(
            new int[] {36, 37, 38, 39, 40, 41, 42, 43, 44}, SlotsInfoRail.slots());
    }

    @Test
    void theRailSitsEntirelyInsideTheCanvasAndNeverOverlapsTheControlRow() {
        for (int slot : SlotsInfoRail.slots()) {
            assertTrue(slot >= 0 && slot < SlotsControlLayout.CANVAS_SLOT_COUNT,
                "rail slot " + slot + " must be a canvas slot");
            assertTrue(slot < SlotsControlLayout.FIRST_CONTROL_SLOT,
                "rail slot " + slot + " must sit above the control row");
        }
    }

    @Test
    void theRailNeverIntrudesOnThePaytablesOwnCardArea() {
        for (int slot : SlotsPaytableLayout.paytableCanvasSlots()) {
            assertFalse(SlotsInfoRail.isRailSlot(slot),
                "the paytable proper must not own rail slot " + slot);
        }
        assertEquals(SlotsControlLayout.CANVAS_SLOT_COUNT,
            SlotsPaytableLayout.paytableCanvasSlots().length + SlotsInfoRail.slots().length,
            "the paytable rows and the rail must together cover the whole canvas exactly once");
    }

    @Test
    void everyRailTileSitsExactlyOneRowAboveTheControlItExplains() {
        int[] controls = {
            SlotsControlLayout.EXIT_SLOT,
            SlotsControlLayout.REELS_SLOT,
            SlotsControlLayout.HEIGHT_SLOT,
            SlotsControlLayout.PAYTABLE_SLOT,
            SlotsControlLayout.SPIN_SLOT,
            SlotsControlLayout.CLOCK_SLOT,
            SlotsControlLayout.LINES_SLOT,
            SlotsControlLayout.WAGER_SLOT,
            SlotsControlLayout.PROFILES_SLOT
        };
        for (int control : controls) {
            int rail = SlotsInfoRail.railSlotFor(control);
            assertEquals(control - SlotsGeometry.INVENTORY_WIDTH, rail,
                "the tile for control " + control + " must be directly above it");
            assertTrue(SlotsInfoRail.isRailSlot(rail));
            assertEquals(control % SlotsGeometry.INVENTORY_WIDTH,
                rail % SlotsGeometry.INVENTORY_WIDTH,
                "rail slot " + rail + " must share control " + control + "'s column");
        }
    }

    @Test
    void theRailAndControlMappingsAreExactInversesOfEachOther() {
        for (int rail = SlotsInfoRail.FIRST_SLOT; rail <= SlotsInfoRail.LAST_SLOT; rail++) {
            int control = SlotsInfoRail.controlSlotFor(rail);
            assertTrue(control >= SlotsControlLayout.FIRST_CONTROL_SLOT
                && control <= SlotsControlLayout.LAST_CONTROL_SLOT);
            assertEquals(rail, SlotsInfoRail.railSlotFor(control));
            assertNotEquals(SlotsControlLayout.Target.NONE, SlotsControlLayout.controlAt(control),
                "every rail tile must align with a real control");
        }
    }

    @Test
    void thePreciseRailToControlPairingIsTheOneTheRedesignSpecifies() {
        assertEquals(SlotsControlLayout.Target.EXIT, SlotsControlLayout.controlAt(SlotsInfoRail.controlSlotFor(36)));
        assertEquals(SlotsControlLayout.Target.REELS, SlotsControlLayout.controlAt(SlotsInfoRail.controlSlotFor(37)));
        assertEquals(SlotsControlLayout.Target.HEIGHT, SlotsControlLayout.controlAt(SlotsInfoRail.controlSlotFor(38)));
        assertEquals(SlotsControlLayout.Target.PAYTABLE, SlotsControlLayout.controlAt(SlotsInfoRail.controlSlotFor(39)));
        assertEquals(SlotsControlLayout.Target.SPIN, SlotsControlLayout.controlAt(SlotsInfoRail.controlSlotFor(40)));
        assertEquals(SlotsControlLayout.Target.CLOCK, SlotsControlLayout.controlAt(SlotsInfoRail.controlSlotFor(41)));
        assertEquals(SlotsControlLayout.Target.PAYLINES, SlotsControlLayout.controlAt(SlotsInfoRail.controlSlotFor(42)));
        assertEquals(SlotsControlLayout.Target.WAGER, SlotsControlLayout.controlAt(SlotsInfoRail.controlSlotFor(43)));
        assertEquals(SlotsControlLayout.Target.PROFILES, SlotsControlLayout.controlAt(SlotsInfoRail.controlSlotFor(44)));
    }

    @Test
    void aClickOnTheRailIsHandedToTheOpenViewAndNeverToAControl() {
        // Canvas is the only thing a rail click may ever resolve to: the
        // Paytable view then deliberately ignores it, so the rail is inert.
        for (int rail : SlotsInfoRail.slots()) {
            for (SlotsUiView view : SlotsUiView.values()) {
                for (ClickType click : new ClickType[] {ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_RIGHT}) {
                    SlotsControlLayout.Target target = SlotsControlLayout.route(view, rail, click).target();
                    assertTrue(target == SlotsControlLayout.Target.CANVAS
                            || target == SlotsControlLayout.Target.NONE,
                        view + " rail slot " + rail + " must never reach a control on " + click
                            + "; got " + target);
                }
            }
        }
    }

    @Test
    void theRailNeverStealsAModalViewsBackToGameSlot() {
        for (SlotsUiView view : SlotsUiView.values()) {
            int back = view.backToGameSlot();
            if (back >= 0) {
                assertFalse(SlotsInfoRail.isRailSlot(back),
                    view + "'s Back to Game slot must be in the control row, not the rail");
            }
        }
    }

    @Test
    void anythingOutsideTheRailIsRejectedRatherThanSilentlyMisaligned() {
        assertFalse(SlotsInfoRail.isRailSlot(35));
        assertFalse(SlotsInfoRail.isRailSlot(45));
        assertThrows(IllegalArgumentException.class, () -> SlotsInfoRail.controlSlotFor(35));
        assertThrows(IllegalArgumentException.class, () -> SlotsInfoRail.controlSlotFor(45));
        // 44 is the last control slot's rail tile; 54 has no control at all.
        assertThrows(IllegalArgumentException.class, () -> SlotsInfoRail.railSlotFor(44));
        assertThrows(IllegalArgumentException.class, () -> SlotsInfoRail.railSlotFor(54));
    }

    @Test
    void theRailSlotsAccessorHandsBackACopy() {
        int[] first = SlotsInfoRail.slots();
        first[0] = -1;
        assertEquals(SlotsInfoRail.FIRST_SLOT, SlotsInfoRail.slots()[0]);
    }

    private static void assertArrayEqualsInts(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length, "length");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "index " + i);
        }
    }
}
