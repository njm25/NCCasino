package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Characterizes the typed wager-selection value object BlackjackInventory
 * stores per seated player (fixed denomination vs. All In mode -- see the
 * table redesign plan) and the pure predicates
 * BlackjackInventory#buildSeatedBottomBarSlotItem calls to decide which
 * single control, if any, gets the enchant glint in a given player's own
 * view. Compares canonical values only -- no localized text, no Bukkit
 * types -- so it's directly testable.
 */
class BlackjackWagerSelectionTest {

    @Test
    void matchingChipValueIsSelected() {
        assertTrue(BlackjackWagerSelection.isSelected(BlackjackWagerSelection.fixed(5.0), 5.0));
    }

    @Test
    void allNonMatchingValuesAreNotSelected() {
        double[] chipValues = {1.0, 5.0, 10.0, 25.0, 100.0};
        BlackjackWagerSelection selected = BlackjackWagerSelection.fixed(10.0);
        for (double value : chipValues) {
            if (value == 10.0) {
                assertTrue(BlackjackWagerSelection.isSelected(selected, value));
            } else {
                assertFalse(BlackjackWagerSelection.isSelected(selected, value));
            }
        }
    }

    @Test
    void changingSelectionMovesTheHighlight() {
        double[] chipValues = {1.0, 5.0, 10.0, 25.0, 100.0};

        BlackjackWagerSelection firstSelection = BlackjackWagerSelection.fixed(5.0);
        assertTrue(BlackjackWagerSelection.isSelected(firstSelection, 5.0));
        for (double value : chipValues) {
            if (value != 5.0) {
                assertFalse(BlackjackWagerSelection.isSelected(firstSelection, value));
            }
        }

        BlackjackWagerSelection secondSelection = BlackjackWagerSelection.fixed(25.0);
        assertFalse(BlackjackWagerSelection.isSelected(secondSelection, 5.0), "previous highlight must not remain after selection changes");
        assertTrue(BlackjackWagerSelection.isSelected(secondSelection, 25.0));
    }

    @Test
    void noSelectionProducesNoHighlightedChip() {
        double[] chipValues = {1.0, 5.0, 10.0, 25.0, 100.0};
        for (double value : chipValues) {
            assertFalse(BlackjackWagerSelection.isSelected(null, value));
        }
    }

    @Test
    void selectionsBelongingToDifferentPlayersRemainIndependent() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Map<UUID, BlackjackWagerSelection> selectedWagers = new HashMap<>();
        selectedWagers.put(alice, BlackjackWagerSelection.fixed(5.0));
        selectedWagers.put(bob, BlackjackWagerSelection.fixed(25.0));

        // Alice's view resolves only her own entry.
        assertTrue(BlackjackWagerSelection.isSelected(selectedWagers.get(alice), 5.0));
        assertFalse(BlackjackWagerSelection.isSelected(selectedWagers.get(alice), 25.0));

        // Bob's view resolves only his own entry -- Alice's pick never leaks in.
        assertTrue(BlackjackWagerSelection.isSelected(selectedWagers.get(bob), 25.0));
        assertFalse(BlackjackWagerSelection.isSelected(selectedWagers.get(bob), 5.0));

        // A third, unseated/unselected player (absent from the map, exactly
        // like the legacy inventory's null-viewer lookup) sees nothing selected.
        UUID carol = UUID.randomUUID();
        for (double value : new double[] {1.0, 5.0, 10.0, 25.0, 100.0}) {
            assertFalse(BlackjackWagerSelection.isSelected(selectedWagers.get(carol), value));
        }
    }

    @Test
    void allInSelectionMatchesNoFixedChipValue() {
        BlackjackWagerSelection allIn = BlackjackWagerSelection.allIn();
        for (double value : new double[] {1.0, 5.0, 10.0, 25.0, 100.0}) {
            assertFalse(BlackjackWagerSelection.isSelected(allIn, value), "All In is never mistaken for a fixed-chip match");
        }
        assertTrue(BlackjackWagerSelection.isAllInSelected(allIn));
    }

    @Test
    void fixedSelectionIsNotAnAllInSelection() {
        assertFalse(BlackjackWagerSelection.isAllInSelected(BlackjackWagerSelection.fixed(10.0)));
        assertFalse(BlackjackWagerSelection.isAllInSelected(null));
    }

    @Test
    void allInCarriesNoCapturedAmount() {
        BlackjackWagerSelection allIn = BlackjackWagerSelection.allIn();
        assertTrue(allIn.isAllIn());
        assertFalse(allIn.isFixed());
    }

    @Test
    void fixedSelectionCarriesItsExactAmount() {
        BlackjackWagerSelection fixed = BlackjackWagerSelection.fixed(25.0);
        assertTrue(fixed.isFixed());
        assertFalse(fixed.isAllIn());
        assertEquals(25.0, fixed.getFixedAmount());
    }
}
