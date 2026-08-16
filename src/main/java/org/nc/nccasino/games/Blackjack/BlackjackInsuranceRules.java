package org.nc.nccasino.games.Blackjack;

import java.util.List;
import java.util.Objects;

import org.nc.nccasino.objects.Card;

/**
 * Pure insurance decision/payout math -- no Bukkit types, no funds movement
 * of its own (BlackjackInventory debits/credits the actual currency; this
 * class only computes the numbers and the settlement shape). Mirrors how
 * {@link BlackjackRules} isolates real logic from the controller.
 *
 * <p>Insurance cost is always based on a hand's <b>original pre-split
 * wager</b> ({@link BlackjackHand#getOriginalPreSplitWager()}), never
 * whatever the wager happens to be later -- correct by construction even
 * with real splitting, since insurance is always decided before any
 * player's turn (and therefore any split) can occur.
 */
public final class BlackjackInsuranceRules {

    private BlackjackInsuranceRules() {
    }

    /** Insurance costs exactly half the hand's original pre-split wager. */
    public static double cost(double originalPreSplitWager) {
        return originalPreSplitWager / 2.0;
    }

    /** Standard 2:1 insurance payout: 2x the stake as profit, plus the stake itself returned -- 3x the stake total. */
    public static double payoutTotal(double stake) {
        return stake * 3.0;
    }

    /**
     * Every seated player with a committed wager is eligible for the
     * insurance offer -- deliberately including a player already holding a
     * natural blackjack (the even-money decision). Eligibility is never
     * conditioned on the player's own hand value.
     */
    public static boolean isEligible(double committedWager) {
        return committedWager > 0;
    }

    /** One player's full settlement against a dealer natural blackjack: their main-hand outcome, plus any insurance payout. */
    public static final class Settlement {
        private final BlackjackOutcome mainHandOutcome;
        private final double insurancePayout;

        public Settlement(BlackjackOutcome mainHandOutcome, double insurancePayout) {
            this.mainHandOutcome = Objects.requireNonNull(mainHandOutcome, "mainHandOutcome");
            this.insurancePayout = insurancePayout;
        }

        public BlackjackOutcome getMainHandOutcome() {
            return mainHandOutcome;
        }

        /** Total insurance payout (0 if they didn't take insurance, or the dealer didn't have blackjack). */
        public double getInsurancePayout() {
            return insurancePayout;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Settlement)) {
                return false;
            }
            Settlement other = (Settlement) o;
            return Double.compare(insurancePayout, other.insurancePayout) == 0 && mainHandOutcome == other.mainHandOutcome;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mainHandOutcome, insurancePayout);
        }
    }

    /**
     * Settles one player's hand against the dealer's -- their main-hand
     * outcome via {@link BlackjackRules#classify(List, List)} (a
     * natural-blackjack holder gets BLACKJACK on their main hand, unless the
     * dealer also has a natural, in which case the main hand pushes -- see
     * {@code BlackjackRulesTest}'s both-natural case) plus their insurance
     * payout, if any -- insurance only ever pays out when the dealer
     * actually has a natural blackjack, regardless of the player's own hand.
     * This is exactly what makes even-money coherent: a player who takes
     * insurance on their own natural against a dealer Ace up-card gets a
     * pushed main hand plus the insurance payout, together equivalent to
     * "even money" on the original wager.
     *
     * @param insuranceStakeOrZero the amount this player staked on insurance, or 0 if they didn't take it
     */
    public static Settlement settle(List<Card> playerHand, List<Card> dealerHand, double insuranceStakeOrZero) {
        BlackjackOutcome mainOutcome = BlackjackRules.classify(playerHand, dealerHand);
        boolean dealerHasBlackjack = BlackjackRules.isNaturalBlackjack(dealerHand);
        double insurancePayout = (insuranceStakeOrZero > 0 && dealerHasBlackjack) ? payoutTotal(insuranceStakeOrZero) : 0.0;
        return new Settlement(mainOutcome, insurancePayout);
    }
}
