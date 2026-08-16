package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Slots are a fixed identity mapping now (Hit=47/Stand=48/Double=49,
 * Split=50 reserved for a later phase) -- never dynamically centered.
 * Unavailable actions simply leave their slot empty rather than
 * re-centering the remaining ones.
 */
class BlackjackActionLayoutTest {

    // --- Hit/Stand/Double action order ---

    @Test
    void twoCardHandUnderTwentyOneOffersAllThreeInOrder() {
        List<BlackjackAction> actions = BlackjackActionLayout.availableActions(12, true, true);
        assertEquals(List.of(BlackjackAction.HIT, BlackjackAction.STAND, BlackjackAction.DOUBLE_DOWN), actions);
    }

    @Test
    void fixedIdentitySlotsAreHitFortySevenStandFortyEightDoubleFortyNine() {
        List<BlackjackAction> actions = BlackjackActionLayout.availableActions(12, true, true);
        Map<BlackjackAction, Integer> layout = BlackjackActionLayout.layout(actions);
        assertEquals(47, layout.get(BlackjackAction.HIT));
        assertEquals(48, layout.get(BlackjackAction.STAND));
        assertEquals(49, layout.get(BlackjackAction.DOUBLE_DOWN));
    }

    // --- Double Down disappears after a successful Hit, but Hit/Stand never move ---

    @Test
    void afterHitDoubleDownIsGoneAndHitStandStayAtTheirFixedSlots() {
        // Three cards in hand -> no longer the initial two-card decision.
        List<BlackjackAction> actions = BlackjackActionLayout.availableActions(15, false, true);
        assertEquals(List.of(BlackjackAction.HIT, BlackjackAction.STAND), actions);

        Map<BlackjackAction, Integer> layout = BlackjackActionLayout.layout(actions);
        assertEquals(47, layout.get(BlackjackAction.HIT));
        assertEquals(48, layout.get(BlackjackAction.STAND));
        assertEquals(2, layout.size());
    }

    // --- Double Down eligibility is exactly the initial two-card decision, and requires funds ---

    @Test
    void doubleDownRequiresExactlyTwoCards() {
        assertTrue(BlackjackActionLayout.availableActions(12, true, true).contains(BlackjackAction.DOUBLE_DOWN));
        assertTrue(BlackjackActionLayout.availableActions(15, false, true).isEmpty() == false
            && !BlackjackActionLayout.availableActions(15, false, true).contains(BlackjackAction.DOUBLE_DOWN));
    }

    @Test
    void doubleDownRequiresAffordability() {
        List<BlackjackAction> actions = BlackjackActionLayout.availableActions(12, true, false);
        assertEquals(List.of(BlackjackAction.HIT, BlackjackAction.STAND), actions);
    }

    // --- A resolved hand (>=21) offers no actions at all ---

    @Test
    void handAtOrAboveTwentyOneOffersNoActions() {
        assertEquals(List.of(), BlackjackActionLayout.availableActions(21, true, true));
        assertEquals(List.of(), BlackjackActionLayout.availableActions(23, false, true));
    }

    // --- Fixed layout regardless of which subset of actions is available ---

    @Test
    void singleActionStillRendersAtItsOwnFixedSlot() {
        Map<BlackjackAction, Integer> layout = BlackjackActionLayout.layout(List.of(BlackjackAction.STAND));
        assertEquals(48, layout.get(BlackjackAction.STAND));
        assertEquals(1, layout.size());
    }

    @Test
    void emptyActionsProduceAnEmptyLayout() {
        assertEquals(Map.of(), BlackjackActionLayout.layout(List.of()));
    }

    // --- Slot-to-action resolution used to re-validate a click ---

    @Test
    void actionAtResolvesTheClickedSlotBackToItsAction() {
        List<BlackjackAction> actions = BlackjackActionLayout.availableActions(12, true, true);
        assertEquals(BlackjackAction.HIT, BlackjackActionLayout.actionAt(actions, 47));
        assertEquals(BlackjackAction.STAND, BlackjackActionLayout.actionAt(actions, 48));
        assertEquals(BlackjackAction.DOUBLE_DOWN, BlackjackActionLayout.actionAt(actions, 49));
        assertNull(BlackjackActionLayout.actionAt(actions, 50));
    }

    // --- Split gating (4-arg availableActions) -----------------------------

    @Test
    void splitAppearsOnlyOnTheInitialTwoCardDecisionWhenEligible() {
        List<BlackjackAction> actions = BlackjackActionLayout.availableActions(12, true, true, true);
        assertEquals(List.of(BlackjackAction.HIT, BlackjackAction.STAND, BlackjackAction.DOUBLE_DOWN, BlackjackAction.SPLIT), actions);
        assertEquals(50, BlackjackActionLayout.layout(actions).get(BlackjackAction.SPLIT));
    }

    @Test
    void splitNeverOfferedWhenIneligibleEvenOnInitialDecision() {
        List<BlackjackAction> actions = BlackjackActionLayout.availableActions(12, true, true, false);
        assertFalse(actions.contains(BlackjackAction.SPLIT));
    }

    @Test
    void splitNeverOfferedAfterTheInitialDecisionEvenIfCallerPassesEligibleTrue() {
        // A hand with 3+ cards is never split-eligible in practice (callers
        // gate splitEligible on isInitialTwoCardDecision before calling),
        // but the layout method itself is the authoritative last-line gate.
        List<BlackjackAction> actions = BlackjackActionLayout.availableActions(15, false, true, true);
        assertFalse(actions.contains(BlackjackAction.SPLIT));
    }

    @Test
    void threeArgOverloadNeverOffersSplit() {
        List<BlackjackAction> actions = BlackjackActionLayout.availableActions(12, true, true);
        assertFalse(actions.contains(BlackjackAction.SPLIT));
    }

    // --- Split-ace action matrix (splitAceActions) --------------------------
    // Every combination of the two configurable booleans, plus the presence
    // or absence of an eligible resplit on top of each -- per the table
    // redesign plan's exact split-ace matrix.

    @Test
    void aceMatrixHitFalseDoubleFalseNoResplitAutoCompletes() {
        assertTrue(BlackjackActionLayout.splitAceHandAutoCompletes(false, false, false));
    }

    @Test
    void aceMatrixHitFalseDoubleTrueOffersStandAndDouble() {
        assertFalse(BlackjackActionLayout.splitAceHandAutoCompletes(false, true, false));
        List<BlackjackAction> actions = BlackjackActionLayout.splitAceActions(false, true, false);
        assertEquals(List.of(BlackjackAction.STAND, BlackjackAction.DOUBLE_DOWN), actions);
    }

    @Test
    void aceMatrixHitTrueDoubleFalseOffersHitAndStand() {
        assertFalse(BlackjackActionLayout.splitAceHandAutoCompletes(true, false, false));
        List<BlackjackAction> actions = BlackjackActionLayout.splitAceActions(true, false, false);
        assertEquals(List.of(BlackjackAction.HIT, BlackjackAction.STAND), actions);
    }

    @Test
    void aceMatrixHitTrueDoubleTrueOffersHitStandAndDouble() {
        List<BlackjackAction> actions = BlackjackActionLayout.splitAceActions(true, true, false);
        assertEquals(List.of(BlackjackAction.HIT, BlackjackAction.STAND, BlackjackAction.DOUBLE_DOWN), actions);
    }

    @Test
    void eligibleResplitAddsSplitOnTopOfHitFalseDoubleFalseInsteadOfAutoCompleting() {
        // "never collapse the options down to just Split+Stand" -- here
        // Split+Stand genuinely IS the correct full set (there's nothing
        // else permitted), so this is the expected floor, not a collapse.
        assertFalse(BlackjackActionLayout.splitAceHandAutoCompletes(false, false, true));
        List<BlackjackAction> actions = BlackjackActionLayout.splitAceActions(false, false, true);
        assertEquals(List.of(BlackjackAction.STAND, BlackjackAction.SPLIT), actions);
    }

    @Test
    void eligibleResplitAddsSplitOnTopOfHitTrueDoubleTrueNeverCollapsingTheOthersAway() {
        List<BlackjackAction> actions = BlackjackActionLayout.splitAceActions(true, true, true);
        assertEquals(List.of(BlackjackAction.HIT, BlackjackAction.STAND, BlackjackAction.DOUBLE_DOWN, BlackjackAction.SPLIT), actions);
    }

    @Test
    void eligibleResplitAddsSplitOnTopOfHitTrueDoubleFalse() {
        List<BlackjackAction> actions = BlackjackActionLayout.splitAceActions(true, false, true);
        assertEquals(List.of(BlackjackAction.HIT, BlackjackAction.STAND, BlackjackAction.SPLIT), actions);
    }

    @Test
    void eligibleResplitAddsSplitOnTopOfHitFalseDoubleTrue() {
        List<BlackjackAction> actions = BlackjackActionLayout.splitAceActions(false, true, true);
        assertEquals(List.of(BlackjackAction.STAND, BlackjackAction.DOUBLE_DOWN, BlackjackAction.SPLIT), actions);
    }

    @Test
    void standIsAlwaysPresentInEverySplitAceCombination() {
        boolean[] bools = {false, true};
        for (boolean hit : bools) {
            for (boolean dbl : bools) {
                for (boolean resplit : bools) {
                    if (BlackjackActionLayout.splitAceHandAutoCompletes(hit, dbl, resplit)) {
                        continue; // no prompt at all in the auto-complete case
                    }
                    assertTrue(BlackjackActionLayout.splitAceActions(hit, dbl, resplit).contains(BlackjackAction.STAND),
                        "Stand must always be offered so timeout-always-Stand has something to default to");
                }
            }
        }
    }

    @Test
    void splitAceFixedSlotIsFifty() {
        Map<BlackjackAction, Integer> layout = BlackjackActionLayout.layout(List.of(BlackjackAction.SPLIT));
        assertEquals(50, layout.get(BlackjackAction.SPLIT));
    }
}
