package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Characterizes the pure predicate BlackjackInventory#paintWagerControls
 * actually calls to decide which chip (if any) gets the enchant glint in a
 * given player's own view. Compares canonical double values only -- no
 * localized text, no Bukkit types -- so it's directly testable.
 */
class BlackjackWagerSelectionTest {

    @Test
    void matchingChipValueIsSelected() {
        assertTrue(BlackjackWagerSelection.isSelected(5.0, 5.0));
    }

    @Test
    void allNonMatchingValuesAreNotSelected() {
        double[] chipValues = {1.0, 5.0, 10.0, 25.0, 100.0};
        Double selected = 10.0;
        for (double value : chipValues) {
            if (value == selected) {
                assertTrue(BlackjackWagerSelection.isSelected(selected, value));
            } else {
                assertFalse(BlackjackWagerSelection.isSelected(selected, value));
            }
        }
    }

    @Test
    void changingSelectionMovesTheHighlight() {
        double[] chipValues = {1.0, 5.0, 10.0, 25.0, 100.0};

        Double firstSelection = 5.0;
        assertTrue(BlackjackWagerSelection.isSelected(firstSelection, 5.0));
        for (double value : chipValues) {
            if (value != 5.0) {
                assertFalse(BlackjackWagerSelection.isSelected(firstSelection, value));
            }
        }

        Double secondSelection = 25.0;
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
        Map<UUID, Double> selectedWagers = new HashMap<>();
        selectedWagers.put(alice, 5.0);
        selectedWagers.put(bob, 25.0);

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
}
