package org.nc.nccasino.games.Slots;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Auto Spin Settings menu's exact upper-canvas layout, and the backdrop
 * rule that stops any stale reel symbol from showing through behind it.
 */
class SlotsAutoSettingsLayoutTest {

    private static final int CANVAS_SLOTS =
        SlotsGeometry.INVENTORY_WIDTH * SlotsGeometry.CANVAS_ROWS;

    @Test
    void everyEntrySitsOnItsRequiredSlot() {
        assertEquals(4, SlotsAutoSettingsLayout.CLOCK_SLOT);
        assertEquals(11, SlotsAutoSettingsLayout.SPIN_LIMIT_SLOT);
        assertEquals(13, SlotsAutoSettingsLayout.STOP_ON_ANY_WIN_SLOT);
        assertEquals(15, SlotsAutoSettingsLayout.BIG_WIN_SLOT);
        assertEquals(29, SlotsAutoSettingsLayout.PROFIT_TARGET_SLOT);
        assertEquals(31, SlotsAutoSettingsLayout.LOSS_LIMIT_SLOT);
        assertEquals(33, SlotsAutoSettingsLayout.RESET_SLOT);
    }

    @Test
    void everySlotResolvesBackToItsOwnEntry() {
        assertEquals(SlotsAutoSettingsLayout.Entry.CLOCK, SlotsAutoSettingsLayout.entryAt(4));
        assertEquals(SlotsAutoSettingsLayout.Entry.SPIN_LIMIT, SlotsAutoSettingsLayout.entryAt(11));
        assertEquals(SlotsAutoSettingsLayout.Entry.STOP_ON_ANY_WIN, SlotsAutoSettingsLayout.entryAt(13));
        assertEquals(SlotsAutoSettingsLayout.Entry.BIG_WIN_MULTIPLIER, SlotsAutoSettingsLayout.entryAt(15));
        assertNull(SlotsAutoSettingsLayout.entryAt(22), "the standalone Start lever is gone");
        assertEquals(SlotsAutoSettingsLayout.Entry.PROFIT_TARGET, SlotsAutoSettingsLayout.entryAt(29));
        assertEquals(SlotsAutoSettingsLayout.Entry.LOSS_LIMIT, SlotsAutoSettingsLayout.entryAt(31));
        assertEquals(SlotsAutoSettingsLayout.Entry.RESET, SlotsAutoSettingsLayout.entryAt(33));
    }

    @Test
    void everyEntryIsPlacedExactlyOnceAndNoneIsMissing() {
        Set<Integer> slots = new HashSet<>();
        Set<SlotsAutoSettingsLayout.Entry> entries = new HashSet<>();
        for (int slot : SlotsAutoSettingsLayout.entrySlots()) {
            assertTrue(slots.add(slot), "slot " + slot + " carries two entries");
            SlotsAutoSettingsLayout.Entry entry = SlotsAutoSettingsLayout.entryAt(slot);
            assertTrue(entries.add(entry), entry + " is placed twice");
        }
        assertEquals(SlotsAutoSettingsLayout.Entry.values().length, entries.size());
        assertEquals(7, slots.size());
    }

    @Test
    void everyEntryStaysInsideTheUpperCanvasAndNeverOnTheInformationalRailRow() {
        for (int slot : SlotsAutoSettingsLayout.entrySlots()) {
            assertTrue(slot >= 0 && slot < CANVAS_SLOTS, "slot " + slot + " is outside the canvas");
            assertFalse(SlotsInfoRail.isRailSlot(slot),
                "slot " + slot + " sits on the Paytable's rail row and must not carry a setting");
        }
    }

    @Test
    void theLayoutIsSymmetricAboutTheCanvasCentreColumn() {
        int width = SlotsGeometry.INVENTORY_WIDTH;
        int centreColumn = width / 2;
        // The Clock is centred; the six editable entries mirror in pairs
        // about the same column.
        assertEquals(centreColumn, SlotsAutoSettingsLayout.CLOCK_SLOT % width);
        assertEquals(centreColumn, SlotsAutoSettingsLayout.STOP_ON_ANY_WIN_SLOT % width);
        assertEquals(centreColumn, SlotsAutoSettingsLayout.LOSS_LIMIT_SLOT % width);

        assertEquals(width - 1 - (SlotsAutoSettingsLayout.SPIN_LIMIT_SLOT % width),
            SlotsAutoSettingsLayout.BIG_WIN_SLOT % width);
        assertEquals(width - 1 - (SlotsAutoSettingsLayout.PROFIT_TARGET_SLOT % width),
            SlotsAutoSettingsLayout.RESET_SLOT % width);

        // The three-entry rows share a row each, below the Clock.
        assertEquals(SlotsAutoSettingsLayout.SPIN_LIMIT_SLOT / width,
            SlotsAutoSettingsLayout.BIG_WIN_SLOT / width);
        assertEquals(SlotsAutoSettingsLayout.PROFIT_TARGET_SLOT / width,
            SlotsAutoSettingsLayout.RESET_SLOT / width);
        assertTrue(SlotsAutoSettingsLayout.CLOCK_SLOT / width
            < SlotsAutoSettingsLayout.SPIN_LIMIT_SLOT / width);
        assertTrue(SlotsAutoSettingsLayout.SPIN_LIMIT_SLOT / width
            < SlotsAutoSettingsLayout.PROFIT_TARGET_SLOT / width);
    }

    @Test
    void everyOtherCanvasSlotIsABackdropSoNoStaleReelSymbolShowsThrough() {
        Set<Integer> entrySlots = new HashSet<>();
        for (int slot : SlotsAutoSettingsLayout.entrySlots()) {
            entrySlots.add(slot);
        }
        int backdrops = 0;
        for (int slot = 0; slot < CANVAS_SLOTS; slot++) {
            boolean isEntry = entrySlots.contains(slot);
            assertEquals(!isEntry, SlotsAutoSettingsLayout.isBackdrop(slot), "slot " + slot);
            if (!isEntry) {
                backdrops++;
            }
        }
        assertEquals(CANVAS_SLOTS - entrySlots.size(), backdrops);
        assertEquals(38, backdrops);
    }

    @Test
    void nothingOutsideTheCanvasIsEverABackdropOrAnEntry() {
        for (int slot : new int[] {-1, CANVAS_SLOTS, 45, 49, 53, SlotsGeometry.INVENTORY_SIZE}) {
            assertFalse(SlotsAutoSettingsLayout.isBackdrop(slot), "slot " + slot);
            assertNull(SlotsAutoSettingsLayout.entryAt(slot), "slot " + slot);
        }
    }

    @Test
    void theBackdropIsTheMachinesOwnRainbowHousingAndNeverAReelCell() {
        // The menu is painted over the cabinet's rainbow rather than a flat
        // grey sheet, but a backdrop cell must still never be mistakable for
        // the reel bay or for a rolled symbol.
        for (int slot = 0; slot < CANVAS_SLOTS; slot++) {
            if (!SlotsAutoSettingsLayout.isBackdrop(slot)) {
                continue;
            }
            Material backdrop = SlotsRainbowHousing.materialForSlot(slot);
            assertNotEquals(SlotsControlPresentation.Role.NEUTRAL_CELL.material(), backdrop,
                "slot " + slot + " would read as an empty reel window");
            for (SlotsSymbol symbol : SlotsSymbol.values()) {
                assertNotEquals(symbol.material(), backdrop, "slot " + slot);
            }
        }
    }

    @Test
    void theOnAndOffMaterialsAreDistinctForEveryToggleableEntry() {
        Material off = SlotsControlPresentation.Role.AUTO_SETTINGS_OFF.material();
        assertNotEquals(off, SlotsControlPresentation.Role.AUTO_SETTINGS_ANY_WIN_ON.material());
        assertNotEquals(off, SlotsControlPresentation.Role.AUTO_SETTINGS_BIG_WIN_ON.material());
        assertNotEquals(off, SlotsControlPresentation.Role.AUTO_SETTINGS_PROFIT_ON.material());
        assertNotEquals(off, SlotsControlPresentation.Role.AUTO_SETTINGS_LOSS_ON.material());
    }

    @Test
    void noAutoSettingsRoleIsApprovedToGlint() {
        // Glint stays reserved for a ready real Spin and matched winning
        // symbols; a menu full of glinting entries would destroy that signal.
        for (SlotsControlPresentation.Role role : SlotsControlPresentation.Role.values()) {
            if (role.name().startsWith("AUTO_SETTINGS")) {
                assertFalse(role.approvedToGlint(), role + " must never glint");
            }
        }
    }
}
