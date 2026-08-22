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

    /** Short display symbol for a rank: 2-9 as digits, TEN as "10", face cards as their initial, Ace as "A". */
    public static String rankAbbreviation(Rank rank) {
        switch (rank) {
            case ACE:
                return "A";
            case KING:
                return "K";
            case QUEEN:
                return "Q";
            case JACK:
                return "J";
            default:
                // TWO..TEN: rankValue already gives the right digits ("2".."10").
                return String.valueOf(rankValue(rank));
        }
    }

    /**
     * Every card's {@link #rankAbbreviation}, joined by "/", followed by
     * " -> " and the hand's best total -- e.g. {@code "K/A -> 21"}, {@code
     * "2/2/3/10 -> 17"}. Always the single best (Ace-optimized) total,
     * never the old dual soft/hard display -- with the actual cards now
     * shown too, a viewer can already see there's an Ace in play, so a
     * second number doesn't add anything.
     *
     * @return null for an empty (or null) hand -- callers must render no
     *     lore line at all in that case, never a placeholder {@code "0"}.
     */
    public static String formatHandCardsAndTotal(List<Card> hand) {
        if (hand == null || hand.isEmpty()) {
            return null;
        }
        StringBuilder cards = new StringBuilder();
        for (int i = 0; i < hand.size(); i++) {
            if (i > 0) {
                cards.append('/');
            }
            cards.append(rankAbbreviation(hand.get(i).getRank()));
        }
        return cards + " -> " + handValue(hand);
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
     * Classifies a finished player hand against the dealer's: a player
     * natural blackjack pays 3:2 unless the dealer also has a natural
     * blackjack, in which case the main wager pushes (standard blackjack
     * rules -- neither hand beats the other). Then player bust, then
     * dealer-bust-or-player-higher as a win, then player-lower as a loss,
     * else push.
     *
     * <p>This replaces the table's previous nonstandard behavior, where a
     * player natural always paid 3:2 even against a dealer natural. That
     * old behavior also made insurance/even-money settlement incoherent (the
     * whole point of even-money is that it substitutes for a push you'd
     * otherwise get on the main wager) -- fixing this is required for
     * insurance to be correct, not an independent rules change.
     */
    public static BlackjackOutcome classify(List<Card> playerHand, List<Card> dealerHand) {
        return classify(playerHand, dealerHand, true);
    }

    /**
     * Classifies a finished player hand against the dealer's, exactly like
     * {@link #classify(List, List)}, except a player's own two-card 21 only
     * pays the {@code BLACKJACK} 3:2 rate when {@code eligibleForNaturalBlackjack}
     * is true. This exists for {@code split-21-is-blackjack}: an unsplit
     * hand is always eligible (the 2-arg overload always passes {@code true});
     * a split hand is eligible only when that config is enabled AND its 21
     * was reached on exactly two cards (the replacement card itself made
     * 21) -- never a 21 reached via a later Hit, regardless of the config.
     * With the config disabled (or the hand ineligible for any other
     * reason), every such 21 is an ordinary 1:1 {@code WIN} instead.
     */
    public static BlackjackOutcome classify(List<Card> playerHand, List<Card> dealerHand, boolean eligibleForNaturalBlackjack) {
        boolean playerNatural = eligibleForNaturalBlackjack && isNaturalBlackjack(playerHand);
        boolean dealerNatural = isNaturalBlackjack(dealerHand);
        if (playerNatural && dealerNatural) {
            return BlackjackOutcome.PUSH;
        }
        if (playerNatural) {
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
