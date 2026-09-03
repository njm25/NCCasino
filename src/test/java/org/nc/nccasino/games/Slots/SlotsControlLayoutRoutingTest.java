package org.nc.nccasino.games.Slots;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole click-routing matrix for the redesigned control row: every slot,
 * every accepted click type, in every view.
 *
 * <p>The ordering of the checks inside {@link SlotsControlLayout#route} is
 * itself the contract -- in particular the Clock's shift-right-click has to
 * be recognized ahead of the ordinary-click gate that would otherwise
 * swallow the only shift-modified action in the UI.
 */
class SlotsControlLayoutRoutingTest {

    private static SlotsControlLayout.Target target(SlotsUiView view, int slot, ClickType click) {
        return SlotsControlLayout.route(view, slot, click).target();
    }

    private static int direction(SlotsUiView view, int slot, ClickType click) {
        return SlotsControlLayout.route(view, slot, click).direction();
    }

    @Test
    void gameViewRoutesEveryBottomControlToItsOwnAction() {
        assertEquals(SlotsControlLayout.Target.EXIT, target(SlotsUiView.GAME, 45, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.REELS, target(SlotsUiView.GAME, 46, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.HEIGHT, target(SlotsUiView.GAME, 47, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.PAYTABLE, target(SlotsUiView.GAME, 48, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.SPIN, target(SlotsUiView.GAME, 49, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.CLOCK, target(SlotsUiView.GAME, 50, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.PAYLINES, target(SlotsUiView.GAME, 51, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.WAGER, target(SlotsUiView.GAME, 52, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.PROFILES, target(SlotsUiView.GAME, 53, ClickType.LEFT));
    }

    @Test
    void rightClickReachesTheSameControlsWithTheOppositeCycleDirection() {
        for (int slot = 45; slot <= 53; slot++) {
            assertEquals(target(SlotsUiView.GAME, slot, ClickType.LEFT),
                target(SlotsUiView.GAME, slot, ClickType.RIGHT),
                "slot " + slot + " must reach the same control on either ordinary click");
            assertEquals(1, direction(SlotsUiView.GAME, slot, ClickType.LEFT));
            assertEquals(-1, direction(SlotsUiView.GAME, slot, ClickType.RIGHT));
        }
    }

    @Test
    void controlAtIgnoresTheOpenViewEntirely() {
        assertEquals(SlotsControlLayout.Target.EXIT, SlotsControlLayout.controlAt(45));
        assertEquals(SlotsControlLayout.Target.PAYTABLE, SlotsControlLayout.controlAt(48));
        assertEquals(SlotsControlLayout.Target.CLOCK, SlotsControlLayout.controlAt(50));
        assertEquals(SlotsControlLayout.Target.PROFILES, SlotsControlLayout.controlAt(53));
        assertEquals(SlotsControlLayout.Target.NONE, SlotsControlLayout.controlAt(54));
        assertEquals(SlotsControlLayout.Target.NONE, SlotsControlLayout.controlAt(-1));
    }

    @Test
    void shiftLeftClickOnTheClockOpensAutoSpinSettingsFromGameAndPaytable() {
        assertEquals(SlotsControlLayout.Target.AUTO_SETTINGS,
            target(SlotsUiView.GAME, 50, ClickType.SHIFT_LEFT));
        assertEquals(SlotsControlLayout.Target.AUTO_SETTINGS,
            target(SlotsUiView.PAYTABLE, 50, ClickType.SHIFT_LEFT));
    }

    @Test
    void shiftLeftClickIsRecognizedAheadOfTheOrdinaryClickGate() {
        // SHIFT_LEFT is not an ordinary click, so the gate would drop it if
        // the Clock's own case were checked second.
        assertEquals(SlotsControlLayout.Target.NONE, target(SlotsUiView.GAME, 49, ClickType.SHIFT_LEFT));
        assertNotEquals(SlotsControlLayout.Target.NONE, target(SlotsUiView.GAME, 50, ClickType.SHIFT_LEFT));
    }

    @Test
    void shiftLeftClickOnTheMenusOwnClockGoesBackToTheGame() {
        // The gesture is symmetric: the same shift-left that opened the menu
        // closes it again from the Clock the menu carries on its canvas.
        assertEquals(SlotsControlLayout.Target.BACK_TO_GAME,
            target(SlotsUiView.AUTO_SETTINGS, SlotsAutoSettingsLayout.CLOCK_SLOT, ClickType.SHIFT_LEFT));
    }

    @Test
    void shiftLeftClickDoesNotOpenAThirdMenuFromInsideAModalEditor() {
        // Auto Spin Settings is already open in one of them; Profiles must not
        // be openable straight into another menu.
        assertEquals(SlotsControlLayout.Target.BACK_TO_GAME,
            target(SlotsUiView.AUTO_SETTINGS, 50, ClickType.SHIFT_LEFT));
        assertEquals(SlotsControlLayout.Target.NONE,
            target(SlotsUiView.PROFILES, 50, ClickType.SHIFT_LEFT));
    }

    @Test
    void everyNonOrdinaryClickTypeIsASafeNoOpOnEveryControl() {
        ClickType[] rejected = {
            ClickType.SHIFT_RIGHT, ClickType.MIDDLE, ClickType.DOUBLE_CLICK, ClickType.DROP,
            ClickType.CONTROL_DROP, ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND,
            ClickType.WINDOW_BORDER_LEFT, ClickType.WINDOW_BORDER_RIGHT, ClickType.CREATIVE,
            ClickType.UNKNOWN
        };
        for (SlotsUiView view : SlotsUiView.values()) {
            for (int slot = 0; slot <= 53; slot++) {
                for (ClickType click : rejected) {
                    assertEquals(SlotsControlLayout.Target.NONE, target(view, slot, click),
                        view + " slot " + slot + " must ignore " + click);
                }
            }
        }
    }

    @Test
    void eachModalViewOwnsExactlyOneBottomSlotAsBackToGame() {
        assertEquals(SlotsControlLayout.Target.BACK_TO_GAME,
            target(SlotsUiView.PAYTABLE, 48, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.BACK_TO_GAME,
            target(SlotsUiView.AUTO_SETTINGS, 50, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.BACK_TO_GAME,
            target(SlotsUiView.PROFILES, 53, ClickType.LEFT));
    }

    @Test
    void backToGameOwnsItsSlotOnEitherOrdinaryClick() {
        assertEquals(SlotsControlLayout.Target.BACK_TO_GAME,
            target(SlotsUiView.PAYTABLE, 48, ClickType.RIGHT));
        assertEquals(SlotsControlLayout.Target.BACK_TO_GAME,
            target(SlotsUiView.PROFILES, 53, ClickType.RIGHT));
    }

    @Test
    void gameViewNeverRoutesAnythingToBackToGame() {
        for (int slot = 0; slot <= 53; slot++) {
            assertNotEquals(SlotsControlLayout.Target.BACK_TO_GAME,
                target(SlotsUiView.GAME, slot, ClickType.LEFT));
        }
    }

    @Test
    void paytableKeepsEveryConfigurationControlLiveBesidesItsOwnBackSlot() {
        // The paytable's numbers are defined to track the live configuration,
        // so changing height/reels/paylines/wager there must stay live.
        assertEquals(SlotsControlLayout.Target.REELS, target(SlotsUiView.PAYTABLE, 46, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.HEIGHT, target(SlotsUiView.PAYTABLE, 47, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.SPIN, target(SlotsUiView.PAYTABLE, 49, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.CLOCK, target(SlotsUiView.PAYTABLE, 50, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.PAYLINES, target(SlotsUiView.PAYTABLE, 51, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.WAGER, target(SlotsUiView.PAYTABLE, 52, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.PROFILES, target(SlotsUiView.PAYTABLE, 53, ClickType.LEFT));
    }

    @Test
    void theProfilesListMakesEveryBottomControlExceptExitInert() {
        SlotsUiView view = SlotsUiView.PROFILES;
        for (int slot = 45; slot <= 53; slot++) {
            SlotsControlLayout.Target actual = target(view, slot, ClickType.LEFT);
            if (slot == 45) {
                assertEquals(SlotsControlLayout.Target.EXIT, actual, "Exit must keep working in " + view);
            } else if (slot == view.backToGameSlot()) {
                assertEquals(SlotsControlLayout.Target.BACK_TO_GAME, actual);
            } else {
                assertEquals(SlotsControlLayout.Target.MODAL_LOCKED, actual,
                    view + " slot " + slot + " must not change the game behind the menu");
            }
        }
    }

    @Test
    void autoSpinSettingsKeepsEveryBottomControlLiveExceptTheSpinLever() {
        // The settings menu is the one modal view a player is expected to
        // reach past: the configuration controls apply and hand them back to
        // the reels, and Paytable/Profiles open directly. Only the Spin lever
        // stays inert, because it commits a real wager.
        SlotsUiView view = SlotsUiView.AUTO_SETTINGS;
        for (int slot = 45; slot <= 53; slot++) {
            SlotsControlLayout.Target actual = target(view, slot, ClickType.LEFT);
            if (slot == view.backToGameSlot()) {
                assertEquals(SlotsControlLayout.Target.BACK_TO_GAME, actual);
            } else if (slot == 49) {
                assertEquals(SlotsControlLayout.Target.MODAL_LOCKED, actual,
                    "the Spin lever must never commit a wager from inside the menu");
            } else {
                assertEquals(SlotsControlLayout.controlAt(slot), actual,
                    "slot " + slot + " must stay live inside Auto Spin Settings");
            }
        }
    }

    @Test
    void inertnessIsAskedPerSlotRatherThanPerView() {
        // What the renderer dims must be exactly what the router refuses.
        assertTrue(SlotsControlLayout.isInertIn(SlotsUiView.AUTO_SETTINGS, 49));
        assertFalse(SlotsControlLayout.isInertIn(SlotsUiView.AUTO_SETTINGS, 46));
        assertFalse(SlotsControlLayout.isInertIn(SlotsUiView.AUTO_SETTINGS, 48));
        assertFalse(SlotsControlLayout.isInertIn(SlotsUiView.AUTO_SETTINGS, 53));
        assertTrue(SlotsControlLayout.isInertIn(SlotsUiView.PROFILES, 46));
        assertFalse(SlotsControlLayout.isInertIn(SlotsUiView.GAME, 49));
        assertFalse(SlotsControlLayout.isInertIn(SlotsUiView.PAYTABLE, 46));
    }

    @Test
    void everyUpperCanvasSlotIsHandedToTheOpenView() {
        for (SlotsUiView view : SlotsUiView.values()) {
            for (int slot = 0; slot < SlotsControlLayout.CANVAS_SLOT_COUNT; slot++) {
                assertEquals(SlotsControlLayout.Target.CANVAS, target(view, slot, ClickType.LEFT),
                    view + " slot " + slot + " belongs to the canvas");
            }
        }
    }

    @Test
    void theCanvasIsExactlyTheFortyFiveSlotsAboveTheControlRow() {
        assertEquals(45, SlotsControlLayout.CANVAS_SLOT_COUNT);
        assertEquals(SlotsControlLayout.CANVAS_SLOT_COUNT, SlotsControlLayout.FIRST_CONTROL_SLOT);
        assertEquals(53, SlotsControlLayout.LAST_CONTROL_SLOT);
    }

    @Test
    void anOutOfRangeSlotIsASafeNoOp() {
        for (SlotsUiView view : SlotsUiView.values()) {
            assertEquals(SlotsControlLayout.Target.NONE, target(view, 54, ClickType.LEFT));
            assertEquals(SlotsControlLayout.Target.NONE, target(view, -1, ClickType.LEFT));
        }
    }

    @Test
    void aNullViewIsTreatedAsGameRatherThanThrowing() {
        assertEquals(SlotsControlLayout.Target.SPIN, target(null, 49, ClickType.LEFT));
        assertEquals(SlotsControlLayout.Target.AUTO_SETTINGS, target(null, 50, ClickType.SHIFT_LEFT));
    }
}
