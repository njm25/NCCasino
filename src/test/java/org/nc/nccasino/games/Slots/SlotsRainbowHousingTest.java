package org.nc.nccasino.games.Slots;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the housing's column-to-material mapping (Section 1 of the opening
 * redesign): every one of the 45 canvas slots must map to its inventory
 * column's rainbow color via {@code slot % 9}, for every supported
 * geometry, with no per-geometry slot table involved.
 */
class SlotsRainbowHousingTest {

    @Test
    void exactlyNineDistinctColorsOnePerColumn() {
        Material[] palette = SlotsRainbowHousing.palette();
        assertEquals(SlotsGeometry.INVENTORY_WIDTH, palette.length);
        Set<Material> distinct = new HashSet<>();
        for (Material material : palette) {
            distinct.add(material);
        }
        assertEquals(SlotsGeometry.INVENTORY_WIDTH, distinct.size(), "every column must have its own color");
    }

    @Test
    void neverBlack() {
        for (Material material : SlotsRainbowHousing.palette()) {
            assertFalse(material.name().contains("BLACK"), material + " must not be black");
        }
    }

    @Test
    void everyCanvasSlotMapsToItsColumnRegardlessOfGeometry() {
        for (int slot = 0; slot < SlotsGeometry.INVENTORY_WIDTH * SlotsGeometry.CANVAS_ROWS; slot++) {
            int column = slot % SlotsGeometry.INVENTORY_WIDTH;
            assertEquals(SlotsRainbowHousing.materialForColumn(column), SlotsRainbowHousing.materialForSlot(slot),
                "slot " + slot + " must use its own column's color");
        }
    }

    @Test
    void everyGutterSlotForEverySupportedGeometryIsRainbowNeverBlack() {
        for (int columns : SlotsGeometry.supportedColumnCounts()) {
            for (int rows : SlotsGeometry.supportedRowCounts()) {
                for (int slot = 0; slot < SlotsGeometry.INVENTORY_WIDTH * SlotsGeometry.CANVAS_ROWS; slot++) {
                    if (SlotsGeometry.isGridSlot(columns, rows, slot)) {
                        continue;
                    }
                    Material material = SlotsRainbowHousing.materialForSlot(slot);
                    assertTrue(material.name().endsWith("_STAINED_GLASS_PANE"), material + " must be a stained glass pane");
                    assertFalse(material.name().contains("BLACK"));
                }
            }
        }
    }

    @Test
    void exactPaletteOrderMatchesTheApprovedLeftToRightSequence() {
        Material[] expected = {
            Material.RED_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE,
        };
        assertEquals(java.util.List.of(expected), java.util.List.of(SlotsRainbowHousing.palette()));
    }
}
