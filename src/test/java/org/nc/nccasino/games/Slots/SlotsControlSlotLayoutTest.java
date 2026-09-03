package org.nc.nccasino.games.Slots;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the exact bottom-row (45-53) slot layout and the material each control
 * renders as: Exit, Reels, Height, Paytable, the centred Spin lever, the
 * Clock, Paylines, Wager Per Line, and Saved Profiles.
 *
 * <p>These slot indices are {@code private static final int} constants on
 * {@link SlotsMachine} (there is no live-server-free way to construct a
 * machine and read its rendered inventory), so this reads them by reflection
 * rather than duplicating the literals -- a future change to either constant
 * is what this test is meant to catch, not paper over.
 */
class SlotsControlSlotLayoutTest {

    private static int slotConstant(String name) throws Exception {
        Field field = SlotsMachine.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    @Test
    void spinControlIsTheTrueCentreOfTheNineSlotControlRow() throws Exception {
        int controlRowStart = 45;
        int controlRowWidth = 9;
        int centre = controlRowStart + controlRowWidth / 2;
        assertEquals(49, centre);
        assertEquals(49, slotConstant("SPIN_SLOT"));
    }

    @Test
    void exactBottomRowLayout() throws Exception {
        assertEquals(45, slotConstant("EXIT_SLOT"));
        assertEquals(46, slotConstant("REELS_SLOT"));
        assertEquals(47, slotConstant("HEIGHT_SLOT"));
        assertEquals(48, slotConstant("PAYTABLE_SLOT"));
        assertEquals(49, slotConstant("SPIN_SLOT"));
        assertEquals(50, slotConstant("CLOCK_SLOT"));
        assertEquals(51, slotConstant("LINES_SLOT"));
        assertEquals(52, slotConstant("WAGER_SLOT"));
        assertEquals(53, slotConstant("PROFILES_SLOT"));
    }

    /**
     * The four configuration controls must read Reels, Height, Paylines,
     * Wager Per Line from left to right across the row -- the ordering is the
     * requirement, so it is asserted as an ordering rather than only as four
     * independent slot numbers.
     */
    @Test
    void theFourConfigurationControlsReadReelsHeightPaylinesWagerLeftToRight() throws Exception {
        int[] inOrder = {
            slotConstant("REELS_SLOT"),
            slotConstant("HEIGHT_SLOT"),
            slotConstant("LINES_SLOT"),
            slotConstant("WAGER_SLOT")
        };
        for (int i = 1; i < inOrder.length; i++) {
            assertTrue(inOrder[i] > inOrder[i - 1],
                "configuration control " + i + " must sit to the right of the one before it");
        }
        assertEquals(SlotsControlLayout.Target.REELS, SlotsControlLayout.controlAt(inOrder[0]));
        assertEquals(SlotsControlLayout.Target.HEIGHT, SlotsControlLayout.controlAt(inOrder[1]));
        assertEquals(SlotsControlLayout.Target.PAYLINES, SlotsControlLayout.controlAt(inOrder[2]));
        assertEquals(SlotsControlLayout.Target.WAGER, SlotsControlLayout.controlAt(inOrder[3]));
    }

    /**
     * Each configuration control keeps its own colour identity wherever it
     * sits: the colour travels with the control, it is not pinned to a slot.
     */
    @Test
    void eachConfigurationControlKeepsItsOwnColourWhereverItSits() {
        assertEquals(Material.BROWN_STAINED_GLASS_PANE,
            SlotsControlPresentation.Role.REELS_CONTROL.material());
        assertEquals(Material.PINK_STAINED_GLASS_PANE,
            SlotsControlPresentation.Role.HEIGHT_CONTROL.material());
        assertEquals(Material.GREEN_STAINED_GLASS_PANE,
            SlotsControlPresentation.Role.PAYLINES_CONTROL.material());
        assertEquals(Material.BLACK_STAINED_GLASS_PANE,
            SlotsControlPresentation.Role.WAGER_CONTROL.material());
    }

    @Test
    void everyControlSlotIsUsedExactlyOnce() throws Exception {
        String[] names = {
            "EXIT_SLOT", "WAGER_SLOT", "REELS_SLOT", "PAYTABLE_SLOT", "SPIN_SLOT",
            "CLOCK_SLOT", "LINES_SLOT", "HEIGHT_SLOT", "PROFILES_SLOT"
        };
        Set<Integer> seen = new HashSet<>();
        for (String name : names) {
            int slot = slotConstant(name);
            assertTrue(slot >= 45 && slot <= 53, name + " must be in the bottom control row");
            assertTrue(seen.add(slot), name + " duplicates an already-assigned slot");
        }
        assertEquals(9, seen.size(), "all nine bottom slots carry a control -- none is reserved any more");
    }

    @Test
    void exactControlMaterials() {
        assertEquals(Material.SPRUCE_DOOR, SlotsControlPresentation.Role.EXIT_CONTROL.material());
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, SlotsControlPresentation.Role.WAGER_CONTROL.material());
        assertEquals(Material.BROWN_STAINED_GLASS_PANE, SlotsControlPresentation.Role.REELS_CONTROL.material());
        assertEquals(Material.BOOK, SlotsControlPresentation.Role.PAYTABLE_OPEN.material());
        assertEquals(Material.LEVER, SlotsControlPresentation.Role.SPIN_READY.material());
        assertEquals(Material.CLOCK, SlotsControlPresentation.Role.AUTO_SPIN_CONTROL.material());
        assertEquals(Material.GREEN_STAINED_GLASS_PANE, SlotsControlPresentation.Role.PAYLINES_CONTROL.material());
        assertEquals(Material.PINK_STAINED_GLASS_PANE, SlotsControlPresentation.Role.HEIGHT_CONTROL.material());
        assertEquals(Material.ENDER_CHEST, SlotsControlPresentation.Role.PROFILES_CONTROL.material());
        assertEquals(Material.MAGENTA_GLAZED_TERRACOTTA, SlotsControlPresentation.Role.BACK_TO_GAME.material());
    }

    @Test
    void eachModalViewSwapsExactlyOneControlForBackToGame() throws Exception {
        assertEquals(-1, SlotsUiView.GAME.backToGameSlot());
        assertEquals(slotConstant("PAYTABLE_SLOT"), SlotsUiView.PAYTABLE.backToGameSlot());
        assertEquals(slotConstant("CLOCK_SLOT"), SlotsUiView.AUTO_SETTINGS.backToGameSlot());
        assertEquals(slotConstant("PROFILES_SLOT"), SlotsUiView.PROFILES.backToGameSlot());
    }

    @Test
    void modalViewsOwnDistinctBackSlots() {
        Set<Integer> slots = new HashSet<>();
        for (SlotsUiView view : SlotsUiView.values()) {
            if (view.isModal()) {
                assertTrue(slots.add(view.backToGameSlot()),
                    view + " must not share its Back to Game slot with another view");
            }
        }
        assertEquals(3, slots.size());
    }

    @Test
    void onlyGameAndPaytableAllowConfigurationChanges() {
        assertTrue(SlotsUiView.GAME.allowsConfigurationChanges());
        assertTrue(SlotsUiView.PAYTABLE.allowsConfigurationChanges());
        assertFalse(SlotsUiView.PROFILES.allowsConfigurationChanges());
        assertFalse(SlotsUiView.AUTO_SETTINGS.allowsConfigurationChanges());
    }

    @Test
    void gameViewIsTheOnlyNonModalView() {
        assertFalse(SlotsUiView.GAME.isModal());
        for (SlotsUiView view : SlotsUiView.values()) {
            if (view != SlotsUiView.GAME) {
                assertTrue(view.isModal(), view + " must be modal");
                assertNotEquals(-1, view.backToGameSlot());
            }
        }
    }
}
