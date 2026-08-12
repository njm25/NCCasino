package org.nc.nccasino.games.Blackjack;

/**
 * Pure predicate for whether a chip denomination is the one a player has
 * currently selected, used by BlackjackInventory#paintWagerControls to
 * decide which single chip (if any) gets the enchant glint in that
 * player's own view. Compares canonical values only -- never localized
 * item names -- so it stays correct regardless of viewer locale.
 */
public final class BlackjackWagerSelection {

    private BlackjackWagerSelection() {
    }

    /**
     * @param selectedWager the player's currently selected wager amount, or null if they haven't selected one
     * @param chipValue     the denomination a specific chip slot represents
     * @return true only if {@code selectedWager} is non-null and exactly matches {@code chipValue}
     */
    public static boolean isSelected(Double selectedWager, double chipValue) {
        return selectedWager != null && selectedWager == chipValue;
    }
}
