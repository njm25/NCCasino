package org.nc.nccasino.games.Blackjack;

import java.util.List;

import org.nc.nccasino.objects.Card;

/**
 * Pure, locale-neutral split-eligibility computation -- no Bukkit types, no
 * funds/shoe lookups of its own (the controller resolves affordability,
 * the player's current hand count, and shoe availability, then passes them
 * in). Mirrors how {@link BlackjackRules}/{@link BlackjackActionLayout}
 * isolate real logic from the controller.
 *
 * <p>Every check here is required in addition to, not instead of, every
 * other -- a hand is only ever split-eligible when it genuinely has exactly
 * two cards, those two cards match per the configured rule, the player can
 * afford exactly one more matching wager, their own per-player hand-count
 * allowance (never a table-wide total) isn't exhausted, and the shoe can
 * immediately supply both replacement cards.
 */
public final class BlackjackSplitEligibility {

    private BlackjackSplitEligibility() {
    }

    /**
     * Whether two cards match under {@code matching}: {@code SAME_RANK}
     * requires identical rank; {@code SAME_VALUE} requires equal
     * {@link BlackjackRules#rankValue}, a strict superset of same-rank (so
     * two Aces, or two of any identical rank, always qualify under either
     * rule -- {@code SAME_VALUE} additionally allows any two distinct
     * ten-value ranks, e.g. King-Queen).
     */
    public static boolean cardsMatch(Card first, Card second, BlackjackSplitMatching matching) {
        if (matching == BlackjackSplitMatching.SAME_RANK) {
            return first.getRank() == second.getRank();
        }
        return BlackjackRules.rankValue(first.getRank()) == BlackjackRules.rankValue(second.getRank());
    }

    /**
     * Full split-eligibility check for a hand about to split.
     *
     * @param handCards                 the hand's current cards -- must be exactly 2
     * @param matching                  the dealer's configured matching rule
     * @param splittingEnabled          {@code dealers.<name>.splitting.enabled}
     * @param canAffordAdditionalWager  whether the player can cover exactly one more wager equal to this hand's wager
     * @param currentHandCountForPlayer how many hands are already in this player's own queue (before this split)
     * @param maxHands                  this dealer's configured per-player max-hands allowance
     * @param cardsRemainingInShoe      how many cards the shoe can still immediately deal -- a split never reshuffles mid-round
     */
    public static boolean isEligible(
        List<Card> handCards,
        BlackjackSplitMatching matching,
        boolean splittingEnabled,
        boolean canAffordAdditionalWager,
        int currentHandCountForPlayer,
        BlackjackMaxHands maxHands,
        int cardsRemainingInShoe
    ) {
        if (!splittingEnabled) {
            return false;
        }
        if (handCards == null || handCards.size() != 2) {
            return false;
        }
        if (!cardsMatch(handCards.get(0), handCards.get(1), matching)) {
            return false;
        }
        if (!canAffordAdditionalWager) {
            return false;
        }
        if (!maxHands.allowsAnotherHand(currentHandCountForPlayer)) {
            return false;
        }
        return cardsRemainingInShoe >= 2;
    }
}
