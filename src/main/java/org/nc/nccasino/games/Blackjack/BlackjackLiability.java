package org.nc.nccasino.games.Blackjack;

import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;

import java.math.BigDecimal;

/**
 * What a Blackjack seat could owe, action by action.
 *
 * <h2>Reserve what was taken, not what might be</h2>
 *
 * <p>Blackjack is the game where over-reserving is as wrong as
 * under-reserving. A seat could in principle split, resplit to the table's
 * hand limit, double every resulting hand and take insurance -- but reserving
 * for all of that at the opening wager would make a modestly funded dealer
 * refuse ordinary hands it can trivially cover, for exposure the player will
 * almost never create.
 *
 * <p>So each exposure-increasing action is priced and reserved when the player
 * actually takes it, per section 19 of the design. The opening wager reserves
 * the opening hand. A split reserves the new hand at the moment it is
 * attempted. A double reserves the increase at the moment it is attempted.
 * Every one of those checks happens before money moves and before a card is
 * drawn, so a refusal costs the player nothing.
 *
 * <h2>Gross payouts</h2>
 *
 * <p>All figures here are gross -- what leaves the dealer, stake included --
 * because that is what a reservation has to cover:
 *
 * <ul>
 *   <li>A hand that can be a natural blackjack: {@code 2.5x} its wager
 *       ({@link BlackjackOutcome#BLACKJACK}).
 *   <li>A hand that cannot: {@code 2x} ({@link BlackjackOutcome#WIN}).
 *   <li>Insurance: {@code 3x} its stake -- 2:1 profit plus the stake
 *       ({@link BlackjackInsuranceRules#payoutTotal}).
 * </ul>
 *
 * <p>A push returns {@code 1x} and is always covered by the win figure, so it
 * never needs its own branch.
 */
public final class BlackjackLiability {

    /** Natural blackjack, gross: 3:2 profit plus the stake. */
    static final BigDecimal BLACKJACK_GROSS = BigDecimal.valueOf(BlackjackOutcome.BLACKJACK.getMultiplier());
    /** An ordinary win, gross: even money plus the stake. */
    static final BigDecimal WIN_GROSS = BigDecimal.valueOf(BlackjackOutcome.WIN.getMultiplier());
    /** Insurance, gross: 2:1 profit plus the stake. */
    static final BigDecimal INSURANCE_GROSS = BigDecimal.valueOf(3.0);

    private BlackjackLiability() {
    }

    /**
     * The opening hand.
     *
     * <p>Reserves the blackjack payout, because the very first two cards can
     * be a natural. Nothing is reserved here for a split or a double the
     * player has not asked for.
     */
    public static Exposure openingHand(double wager) {
        BigDecimal stake = Money.of(wager);
        return Exposure.of(stake, Money.multiply(stake, BLACKJACK_GROSS));
    }

    /**
     * One additional hand created by a split or resplit.
     *
     * @param splitHandPaysBlackjack whether this table lets a split hand's 21
     *     count as a natural blackjack. When it does the new hand carries the
     *     full {@code 2.5x}; when it does not, {@code 2x} is the true ceiling
     *     and reserving more would tie up funds the dealer cannot lose.
     */
    public static Exposure splitHand(double wager, boolean splitHandPaysBlackjack) {
        BigDecimal stake = Money.of(wager);
        return Exposure.of(stake,
            Money.multiply(stake, splitHandPaysBlackjack ? BLACKJACK_GROSS : WIN_GROSS));
    }

    /**
     * The <em>increase</em> a double adds to one hand.
     *
     * <p>A doubled hand has two cards plus exactly one more, so it can never
     * be a natural: its ceiling is {@code 2x} the doubled wager. The extra
     * exposure is therefore {@code 2x} the additional stake alone -- the
     * original half of the wager is already reserved by whatever created the
     * hand.
     */
    public static Exposure doubleIncrease(double additionalStake) {
        BigDecimal stake = Money.of(additionalStake);
        return Exposure.of(stake, Money.multiply(stake, WIN_GROSS));
    }

    /**
     * A doubled hand's total exposure, for a caller that prices the whole hand
     * rather than the increment.
     *
     * <p>Note this is <em>lower</em> than the blackjack ceiling a fresh hand of
     * the same total stake would carry: doubling forfeits the chance of a
     * natural. Recomputing the hand from scratch as a blackjack-capable one
     * would over-reserve.
     */
    public static Exposure doubledHand(double totalWagerAfterDoubling) {
        BigDecimal stake = Money.of(totalWagerAfterDoubling);
        return Exposure.of(stake, Money.multiply(stake, WIN_GROSS));
    }

    /**
     * An insurance bet.
     *
     * <p>Priced independently of the hand it protects: insurance is settled
     * against the dealer's hole card, not against the player's hand, and both
     * can pay in the same round.
     */
    public static Exposure insurance(double insuranceStake) {
        BigDecimal stake = Money.of(insuranceStake);
        return Exposure.of(stake, Money.multiply(stake, INSURANCE_GROSS));
    }

    /**
     * A seat's total exposure once an action is added to what it already
     * holds.
     *
     * <p>Blackjack hands and insurance settle independently and can all pay in
     * the same round, so the seat's obligations add. Callers hold one
     * reservation per seat and grow it through this rather than opening a
     * second reservation per hand, which keeps settlement to a single
     * idempotent step.
     */
    public static Exposure combine(Exposure existing, Exposure added) {
        if (existing == null) {
            return added == null ? Exposure.none() : added;
        }
        return existing.plus(added);
    }
}
