package org.nc.nccasino.games.Blackjack;

import java.util.List;

import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;

/**
 * Locale-neutral Blackjack rules. Operates only on {@link Card}/{@link Rank}
 * and never on ItemStacks or localized text, so hand math can never be
 * affected by which language a card's display name is rendered in.
 *
 * Preserves the exact semantics of the previous ItemStack-display-name-based
 * calculation in BlackjackInventory (getCardValue / calculateHandValue /
 * calculateHandValueWithSoftCheck / hasAceAndTenValueCard / finishGame's
 * outcome ladder) -- this is a characterization extraction, not a rules
 * rewrite.
 */
public final class BlackjackRules {

    private BlackjackRules() {
    }

    /** Base value of a rank: Ace = 1, ten/face cards = 10, else pip value. */
    public static int rankValue(Rank rank) {
        switch (rank) {
            case ACE:
                return 1;
            case TEN:
            case JACK:
            case QUEEN:
            case KING:
                return 10;
            default:
                // TWO..NINE: ordinal 0..7, so ordinal + 2 == pip value.
                return rank.ordinal() + 2;
        }
    }

    /** Card stack size used to render the card in the GUI (matches rankValue for all ranks). */
    public static int cardStackSize(Card card) {
        return rankValue(card.getRank());
    }

    /**
     * Evaluates a hand's hard total (every Ace = 1) and best total (Aces
     * promoted to 11 one at a time as long as doing so doesn't bust),
     * matching the Ace-adjustment loop previously duplicated in
     * calculateHandValue and calculateHandValueWithSoftCheck.
     */
    public static BlackjackHandValue evaluate(List<Card> hand) {
        if (hand == null || hand.isEmpty()) {
            return new BlackjackHandValue(0, 0);
        }

        int hardTotal = 0;
        int aces = 0;
        for (Card card : hand) {
            int value = rankValue(card.getRank());
            hardTotal += value;
            if (value == 1) {
                aces++;
            }
        }

        int bestTotal = hardTotal;
        int remainingAces = aces;
        while (remainingAces > 0 && bestTotal + 10 <= 21) {
            bestTotal += 10;
            remainingAces--;
        }

        return new BlackjackHandValue(hardTotal, bestTotal);
    }

    /** Convenience for the value most callers care about (Aces optimized). */
    public static int handValue(List<Card> hand) {
        return evaluate(hand).getBestTotal();
    }

    public static boolean isBust(List<Card> hand) {
        return evaluate(hand).isBust();
    }

    private static boolean hasAceAndTenValueCard(List<Card> hand) {
        boolean hasAce = false;
        boolean hasTenValueCard = false;
        for (Card card : hand) {
            int value = rankValue(card.getRank());
            if (value == 1) {
                hasAce = true;
            } else if (value == 10) {
                hasTenValueCard = true;
            }
        }
        return hasAce && hasTenValueCard;
    }

    /** Natural blackjack: exactly two cards totalling 21 via an Ace + a ten-value card. */
    public static boolean isNaturalBlackjack(List<Card> hand) {
        if (hand == null || hand.size() != 2) {
            return false;
        }
        return evaluate(hand).getBestTotal() == 21 && hasAceAndTenValueCard(hand);
    }

    /**
     * Dealer hit/stand decision at exactly 17, matching the previous
     * {@code Math.random() * 100 < standOn17Chance} check: the dealer stands
     * when the draw falls below the configured stand-on-17 chance, and hits
     * otherwise. Below 17 the dealer always hits; above 17 it never does --
     * this method exists to make the one genuinely probabilistic branch
     * (exactly 17) independently testable via an injected draw instead of
     * Math.random().
     *
     * @param handValue           current dealer hand total
     * @param standOn17Chance     configured percent chance (0-100) to stand on 17
     * @param randomDrawPercent   a value in [0, 100) standing in for {@code Math.random() * 100}
     * @return true if the dealer should take another card
     */
    public static boolean dealerShouldHit(int handValue, int standOn17Chance, double randomDrawPercent) {
        if (handValue < 17) {
            return true;
        }
        if (handValue > 17) {
            return false;
        }
        return !(randomDrawPercent < standOn17Chance);
    }

    /**
     * Classifies a finished player hand against the dealer's, matching the
     * exact branch order of BlackjackInventory#finishGame: natural blackjack
     * first, then player bust, then dealer-bust-or-player-higher as a win,
     * then player-lower as a loss, else push.
     */
    public static BlackjackOutcome classify(List<Card> playerHand, List<Card> dealerHand) {
        if (isNaturalBlackjack(playerHand)) {
            return BlackjackOutcome.BLACKJACK;
        }

        int playerTotal = handValue(playerHand);
        if (playerTotal > 21) {
            return BlackjackOutcome.BUST;
        }

        int dealerTotal = handValue(dealerHand);
        boolean dealerBusted = dealerTotal > 21;
        if (dealerBusted || playerTotal > dealerTotal) {
            return BlackjackOutcome.WIN;
        }
        if (playerTotal < dealerTotal) {
            return BlackjackOutcome.LOSS;
        }
        return BlackjackOutcome.PUSH;
    }
}
