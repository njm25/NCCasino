package org.nc.nccasino.games.Blackjack;

import java.util.Objects;

/**
 * A seated player's persistent wager-selection tool -- what a chip or All In
 * click sets, distinct from any committed currency (see the table redesign
 * plan's wager-selection-vs-commitment split). Immutable value type with two
 * variants:
 *
 * <ul>
 *   <li>{@link Kind#FIXED} -- a specific chip denomination, captured once at
 *       selection time and reused unchanged by every subsequent bet-spot
 *       click until replaced or cleared.</li>
 *   <li>{@link Kind#ALL_IN} -- a dynamic mode, never a captured balance. Each
 *       bet-spot click must re-resolve the player's live available balance
 *       at commit time (see BlackjackInventory#resolveSelectionAmount) --
 *       this type deliberately carries no amount of its own so nothing can
 *       accidentally reuse a stale snapshot.</li>
 * </ul>
 */
public final class BlackjackWagerSelection {

    enum Kind { FIXED, ALL_IN }

    private static final BlackjackWagerSelection ALL_IN = new BlackjackWagerSelection(Kind.ALL_IN, 0.0);

    private final Kind kind;
    private final double fixedAmount;

    private BlackjackWagerSelection(Kind kind, double fixedAmount) {
        this.kind = kind;
        this.fixedAmount = fixedAmount;
    }

    static BlackjackWagerSelection fixed(double amount) {
        return new BlackjackWagerSelection(Kind.FIXED, amount);
    }

    static BlackjackWagerSelection allIn() {
        return ALL_IN;
    }

    boolean isAllIn() {
        return kind == Kind.ALL_IN;
    }

    boolean isFixed() {
        return kind == Kind.FIXED;
    }

    /** Only meaningful when {@link #isFixed()} -- an All In selection carries no amount of its own. */
    double getFixedAmount() {
        return fixedAmount;
    }

    /**
     * True only if {@code selection} is a {@link Kind#FIXED} selection whose
     * captured amount exactly matches {@code chipValue} -- used by
     * BlackjackInventory#buildSeatedBottomBarSlotItem to decide which single
     * chip (if any) gets the enchant glint in a player's own view. Compares
     * canonical values only -- never localized item names -- so it stays
     * correct regardless of viewer locale. An All In selection never matches
     * any chip value here; see {@link #isAllInSelected} for its own slot.
     *
     * @param selection the player's currently selected wager, or null if they haven't selected one
     * @param chipValue the denomination a specific chip slot represents
     */
    static boolean isSelected(BlackjackWagerSelection selection, double chipValue) {
        return selection != null && selection.isFixed() && selection.fixedAmount == chipValue;
    }

    /** True only if {@code selection} is currently in All In mode -- used for the All In slot's own glint. */
    static boolean isAllInSelected(BlackjackWagerSelection selection) {
        return selection != null && selection.isAllIn();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlackjackWagerSelection other)) return false;
        return kind == other.kind && Double.compare(fixedAmount, other.fixedAmount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, fixedAmount);
    }

    @Override
    public String toString() {
        return kind == Kind.ALL_IN ? "BlackjackWagerSelection[ALL_IN]" : "BlackjackWagerSelection[FIXED " + fixedAmount + "]";
    }
}
