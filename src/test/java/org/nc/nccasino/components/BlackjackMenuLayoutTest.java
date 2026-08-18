package org.nc.nccasino.components;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.entities.Menu.SlotOption;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure coverage for {@link BlackjackMenu#computeLayout}, the dynamic
 * contingent-visibility slot-compaction algorithm behind the Blackjack
 * settings menu: applicable settings pack toward slot 0 in declaration
 * order, skipping anything not currently visible, while Exit always stays
 * pinned at the last slot. No Bukkit types involved -- this is the exact
 * algorithm {@code layoutMenu()} uses, not a reimplementation of it.
 *
 * <p>The Action Timer is mandatory (no enabled/disabled toggle) -- Edit
 * Turn Timer Timeout is always visible, never contingent on anything.
 */
class BlackjackMenuLayoutTest {

    private static final int MENU_SIZE = 18;

    /** The real declaration order from {@code BlackjackMenu.menuEntries()}. */
    private static final List<SlotOption> ORDERED_OPTIONS = List.of(
        SlotOption.RETURN, SlotOption.EDIT_TIMER, SlotOption.STAND_17, SlotOption.NUMBER_OF_DECKS,
        SlotOption.TOGGLE_INSURANCE_ENABLED, SlotOption.EDIT_INSURANCE_TIMEOUT,
        SlotOption.TOGGLE_SPLITTING_ENABLED, SlotOption.TOGGLE_SPLIT_MATCHING, SlotOption.EDIT_MAX_HANDS,
        SlotOption.TOGGLE_DOUBLE_AFTER_SPLIT, SlotOption.TOGGLE_ACES_HIT,
        SlotOption.TOGGLE_ACES_DOUBLE, SlotOption.TOGGLE_ACES_RESPLIT,
        SlotOption.EDIT_TURN_TIMER_TIMEOUT
    );

    private static Set<SlotOption> allVisibleExcept(SlotOption... hidden) {
        Set<SlotOption> visible = new LinkedHashSet<>(ORDERED_OPTIONS);
        for (SlotOption option : hidden) {
            visible.remove(option);
        }
        return visible;
    }

    @Test
    void defaultConfigPacksAllFourteenSettingsFromSlotZeroWithExitLast() {
        Map<SlotOption, Integer> layout = BlackjackMenu.computeLayout(ORDERED_OPTIONS, allVisibleExcept(), MENU_SIZE);

        for (int i = 0; i < ORDERED_OPTIONS.size(); i++) {
            assertEquals(i, layout.get(ORDERED_OPTIONS.get(i)), ORDERED_OPTIONS.get(i) + " must land at slot " + i);
        }
        assertEquals(MENU_SIZE - 1, layout.get(SlotOption.EXIT));
        assertEquals(15, layout.size(), "14 content entries + Exit");
    }

    @Test
    void disablingSplittingHidesItsSixDependentSettingsAndCompactsEverythingAfterLeft() {
        Set<SlotOption> visible = allVisibleExcept(
            SlotOption.TOGGLE_SPLIT_MATCHING, SlotOption.EDIT_MAX_HANDS,
            SlotOption.TOGGLE_DOUBLE_AFTER_SPLIT, SlotOption.TOGGLE_ACES_HIT,
            SlotOption.TOGGLE_ACES_DOUBLE, SlotOption.TOGGLE_ACES_RESPLIT
        );
        Map<SlotOption, Integer> layout = BlackjackMenu.computeLayout(ORDERED_OPTIONS, visible, MENU_SIZE);

        // Everything up to and including Splitting's own toggle is unaffected.
        assertEquals(0, layout.get(SlotOption.RETURN));
        assertEquals(6, layout.get(SlotOption.TOGGLE_SPLITTING_ENABLED));

        // The six now-hidden entries have no slot at all.
        assertFalse(layout.containsKey(SlotOption.TOGGLE_SPLIT_MATCHING));
        assertFalse(layout.containsKey(SlotOption.EDIT_MAX_HANDS));
        assertFalse(layout.containsKey(SlotOption.TOGGLE_DOUBLE_AFTER_SPLIT));
        assertFalse(layout.containsKey(SlotOption.TOGGLE_ACES_HIT));
        assertFalse(layout.containsKey(SlotOption.TOGGLE_ACES_DOUBLE));
        assertFalse(layout.containsKey(SlotOption.TOGGLE_ACES_RESPLIT));

        // Edit Turn Timer Timeout (always visible -- no toggle to gate it) slides left into the gap.
        assertEquals(7, layout.get(SlotOption.EDIT_TURN_TIMER_TIMEOUT));

        // Exit never moves.
        assertEquals(MENU_SIZE - 1, layout.get(SlotOption.EXIT));
    }

    @Test
    void reenablingSplittingRestoresTheOriginalSlotsExactly() {
        Set<SlotOption> withoutSplitting = allVisibleExcept(
            SlotOption.TOGGLE_SPLIT_MATCHING, SlotOption.EDIT_MAX_HANDS,
            SlotOption.TOGGLE_DOUBLE_AFTER_SPLIT, SlotOption.TOGGLE_ACES_HIT,
            SlotOption.TOGGLE_ACES_DOUBLE, SlotOption.TOGGLE_ACES_RESPLIT
        );
        BlackjackMenu.computeLayout(ORDERED_OPTIONS, withoutSplitting, MENU_SIZE); // simulate the disabled state having been rendered once

        Map<SlotOption, Integer> restored = BlackjackMenu.computeLayout(ORDERED_OPTIONS, allVisibleExcept(), MENU_SIZE);

        for (int i = 0; i < ORDERED_OPTIONS.size(); i++) {
            assertEquals(i, restored.get(ORDERED_OPTIONS.get(i)), ORDERED_OPTIONS.get(i) + " must be back at slot " + i);
        }
        assertEquals(MENU_SIZE - 1, restored.get(SlotOption.EXIT));
    }

    @Test
    void disablingInsuranceHidesOnlyItsOwnTimeoutAndShiftsEverythingAfterLeftByOne() {
        Map<SlotOption, Integer> layout = BlackjackMenu.computeLayout(
            ORDERED_OPTIONS, allVisibleExcept(SlotOption.EDIT_INSURANCE_TIMEOUT), MENU_SIZE);

        assertFalse(layout.containsKey(SlotOption.EDIT_INSURANCE_TIMEOUT));
        assertEquals(4, layout.get(SlotOption.TOGGLE_INSURANCE_ENABLED));
        assertEquals(5, layout.get(SlotOption.TOGGLE_SPLITTING_ENABLED), "shifted left by the one hidden slot");
        assertEquals(12, layout.get(SlotOption.EDIT_TURN_TIMER_TIMEOUT), "every later entry shifts left by exactly one");
    }

    @Test
    void disablingBothParentTogglesLeavesOnlyTheirOwnTogglesAndAlwaysVisibleEntries() {
        // Edit Turn Timer Timeout has no toggle to hide behind (Action Timer
        // is mandatory) -- it stays visible even with both remaining parent
        // toggles (Insurance, Splitting) off.
        Set<SlotOption> visible = allVisibleExcept(
            SlotOption.EDIT_INSURANCE_TIMEOUT,
            SlotOption.TOGGLE_SPLIT_MATCHING, SlotOption.EDIT_MAX_HANDS,
            SlotOption.TOGGLE_DOUBLE_AFTER_SPLIT, SlotOption.TOGGLE_ACES_HIT,
            SlotOption.TOGGLE_ACES_DOUBLE, SlotOption.TOGGLE_ACES_RESPLIT
        );
        Map<SlotOption, Integer> layout = BlackjackMenu.computeLayout(ORDERED_OPTIONS, visible, MENU_SIZE);

        List<SlotOption> expectedRemaining = List.of(
            SlotOption.RETURN, SlotOption.EDIT_TIMER, SlotOption.STAND_17, SlotOption.NUMBER_OF_DECKS,
            SlotOption.TOGGLE_INSURANCE_ENABLED, SlotOption.TOGGLE_SPLITTING_ENABLED,
            SlotOption.EDIT_TURN_TIMER_TIMEOUT
        );
        for (int i = 0; i < expectedRemaining.size(); i++) {
            assertEquals(i, layout.get(expectedRemaining.get(i)));
        }
        assertEquals(expectedRemaining.size() + 1, layout.size(), "7 always-applicable entries + Exit, everything contingent hidden");
        assertEquals(MENU_SIZE - 1, layout.get(SlotOption.EXIT));
    }

    @Test
    void exitIsAlwaysPinnedLastRegardlessOfHowManySettingsAreVisible() {
        Map<SlotOption, Integer> everythingVisible = BlackjackMenu.computeLayout(ORDERED_OPTIONS, allVisibleExcept(), MENU_SIZE);
        Map<SlotOption, Integer> nothingVisible = BlackjackMenu.computeLayout(ORDERED_OPTIONS, Set.of(), MENU_SIZE);

        assertEquals(MENU_SIZE - 1, everythingVisible.get(SlotOption.EXIT));
        assertEquals(MENU_SIZE - 1, nothingVisible.get(SlotOption.EXIT));
        assertTrue(nothingVisible.values().stream().allMatch(slot -> slot == MENU_SIZE - 1),
            "with no content entries visible, Exit is the only occupied slot");
    }

    @Test
    void allFourteenContentSlotsFitStrictlyBeforeExitWithNoOverlap() {
        Map<SlotOption, Integer> layout = BlackjackMenu.computeLayout(ORDERED_OPTIONS, allVisibleExcept(), MENU_SIZE);

        Set<Integer> usedSlots = new java.util.HashSet<>();
        for (Map.Entry<SlotOption, Integer> entry : layout.entrySet()) {
            assertTrue(usedSlots.add(entry.getValue()), "slot " + entry.getValue() + " assigned to more than one option");
            if (entry.getKey() != SlotOption.EXIT) {
                assertTrue(entry.getValue() < MENU_SIZE - 1, "content option must never land on Exit's slot");
            }
        }
    }
}
