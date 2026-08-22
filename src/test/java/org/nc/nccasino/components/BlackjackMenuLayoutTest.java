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

    // ==================================================================
    // Animated relayout: pure frame math (computeRelayoutFrame)
    // ==================================================================

    @Test
    void slideStepAdvancesExactlyOneSlotPerStepTowardTheTargetOnRealSplittingCollapse() {
        Map<SlotOption, Integer> oldSlots = BlackjackMenu.computeLayout(ORDERED_OPTIONS, allVisibleExcept(), MENU_SIZE);
        Map<SlotOption, Integer> newSlots = BlackjackMenu.computeLayout(ORDERED_OPTIONS, allVisibleExcept(
            SlotOption.TOGGLE_SPLIT_MATCHING, SlotOption.EDIT_MAX_HANDS,
            SlotOption.TOGGLE_DOUBLE_AFTER_SPLIT, SlotOption.TOGGLE_ACES_HIT,
            SlotOption.TOGGLE_ACES_DOUBLE, SlotOption.TOGGLE_ACES_RESPLIT
        ), MENU_SIZE);
        // Confirmed by disablingSplittingHidesItsSixDependentSettingsAndCompactsEverythingAfterLeft: 13 -> 7, delta -6.
        assertEquals(13, oldSlots.get(SlotOption.EDIT_TURN_TIMER_TIMEOUT));
        assertEquals(7, newSlots.get(SlotOption.EDIT_TURN_TIMER_TIMEOUT));

        for (int step = 1; step <= 6; step++) {
            Map<SlotOption, Integer> frame = BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, step, 6);
            assertEquals(13 - step, frame.get(SlotOption.EDIT_TURN_TIMER_TIMEOUT), "step " + step);
        }
        // The final step must land exactly on the real target slot.
        assertEquals(newSlots.get(SlotOption.EDIT_TURN_TIMER_TIMEOUT),
            BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, 6, 6).get(SlotOption.EDIT_TURN_TIMER_TIMEOUT));
    }

    @Test
    void entriesThatDontMoveHoldAtTheSameSlotOnEveryFrame() {
        Map<SlotOption, Integer> oldSlots = Map.of(SlotOption.STAND_17, 2, SlotOption.NUMBER_OF_DECKS, 3);
        Map<SlotOption, Integer> newSlots = Map.of(SlotOption.STAND_17, 2, SlotOption.NUMBER_OF_DECKS, 3);

        for (int step = 1; step <= 4; step++) {
            Map<SlotOption, Integer> frame = BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, step, 4);
            assertEquals(2, frame.get(SlotOption.STAND_17));
            assertEquals(3, frame.get(SlotOption.NUMBER_OF_DECKS));
        }
    }

    @Test
    void anEntryWithASmallerDeltaArrivesEarlyAndHoldsForTheRemainingSteps() {
        // One entry travels 2 slots, another travels 5 -- the 2-slot mover
        // must reach its target by step 2 and stay there through step 5.
        Map<SlotOption, Integer> oldSlots = Map.of(SlotOption.STAND_17, 10, SlotOption.NUMBER_OF_DECKS, 10);
        Map<SlotOption, Integer> newSlots = Map.of(SlotOption.STAND_17, 8, SlotOption.NUMBER_OF_DECKS, 5);

        assertEquals(9, BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, 1, 5).get(SlotOption.STAND_17));
        assertEquals(8, BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, 2, 5).get(SlotOption.STAND_17), "reaches its target at step 2");
        assertEquals(8, BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, 3, 5).get(SlotOption.STAND_17), "holds once arrived");
        assertEquals(8, BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, 5, 5).get(SlotOption.STAND_17), "still holding at the final step");

        assertEquals(9, BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, 1, 5).get(SlotOption.NUMBER_OF_DECKS));
        assertEquals(5, BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, 5, 5).get(SlotOption.NUMBER_OF_DECKS), "the 5-slot mover only arrives on the final step");
    }

    @Test
    void aDisappearingEntryNeverAppearsInAnyFrame() {
        Map<SlotOption, Integer> oldSlots = Map.of(SlotOption.TOGGLE_SPLIT_MATCHING, 7, SlotOption.EDIT_TURN_TIMER_TIMEOUT, 13);
        Map<SlotOption, Integer> newSlots = Map.of(SlotOption.EDIT_TURN_TIMER_TIMEOUT, 7); // Split Matching hidden entirely

        for (int step = 1; step <= 6; step++) {
            Map<SlotOption, Integer> frame = BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, step, 6);
            assertFalse(frame.containsKey(SlotOption.TOGGLE_SPLIT_MATCHING), "a hidden entry must never render at any step, including step " + step);
        }
    }

    @Test
    void anAppearingEntryOnlyRendersOnTheFinalStep() {
        Map<SlotOption, Integer> oldSlots = Map.of(SlotOption.EDIT_TURN_TIMER_TIMEOUT, 7);
        Map<SlotOption, Integer> newSlots = Map.of(SlotOption.TOGGLE_SPLIT_MATCHING, 7, SlotOption.EDIT_TURN_TIMER_TIMEOUT, 13); // Split Matching newly shown

        for (int step = 1; step < 6; step++) {
            Map<SlotOption, Integer> frame = BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, step, 6);
            assertFalse(frame.containsKey(SlotOption.TOGGLE_SPLIT_MATCHING), "must not appear before the slide finishes, step " + step);
        }
        Map<SlotOption, Integer> finalFrame = BlackjackMenu.computeRelayoutFrame(oldSlots, newSlots, 6, 6);
        assertEquals(7, finalFrame.get(SlotOption.TOGGLE_SPLIT_MATCHING), "appears exactly at its final slot on the last step");
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
