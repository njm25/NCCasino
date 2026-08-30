package org.nc.nccasino.games.Baccarat;

import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * What a Baccarat hand could owe one player, evaluated against every result
 * the table can produce.
 *
 * <h2>Why enumerate rather than reason</h2>
 *
 * <p>A Baccarat portfolio pays on more than one axis at once: the main result
 * (Player, Banker or Tie) and the two pair side bets, which are decided by the
 * dealt hands and are independent of who wins. A player betting Banker and both
 * pairs collects all three on a Banker win with two pairs showing.
 *
 * <p>It would be possible to argue that the worst case always has both pairs
 * hitting, since pair payouts are non-negative and additive. That argument is
 * correct today and would silently stop being correct the moment a pair rule,
 * a commission or a Tie push changed. So the twelve result families
 * (3 main x pair/no-pair x pair/no-pair) are enumerated instead, priced with
 * the same numbers {@link BaccaratServer} settles with. Twelve multiplications
 * is not a cost worth optimizing against a wrong answer about money.
 *
 * <h2>Payout table</h2>
 *
 * <p>These mirror {@code BaccaratServer.processPayouts} exactly, and are gross
 * returns including the stake:
 *
 * <ul>
 *   <li>Player wins: Player bets return 2x; a Tie pushes them at 1x.
 *   <li>Banker wins: Banker bets return 1.95x -- 2x less the standard 5%
 *       commission on the winnings; a Tie pushes them at 1x.
 *   <li>Tie: Tie bets return 9x.
 *   <li>Either pair: that pair bet returns 12x.
 * </ul>
 */
public final class BaccaratLiability {

    /** Player win, gross, including the returned stake. */
    static final BigDecimal PLAYER_WIN = new BigDecimal("2");
    /** Banker win, gross: 2x less the 5% commission on the winnings. */
    static final BigDecimal BANKER_WIN = new BigDecimal("1.95");
    /** A Tie returns Player and Banker stakes untouched. */
    static final BigDecimal PUSH = BigDecimal.ONE;
    static final BigDecimal TIE_WIN = new BigDecimal("9");
    static final BigDecimal PAIR_WIN = new BigDecimal("12");

    /** The three ways a hand can be decided. */
    private enum MainResult {
        PLAYER_WINS,
        BANKER_WINS,
        TIE
    }

    private BaccaratLiability() {
    }

    /**
     * The largest total this portfolio could pay on any single hand.
     *
     * @param bets each bet option's total staked amount; missing options are
     *     treated as unstaked
     */
    public static BigDecimal maxPossiblePayout(Map<BaccaratClient.BetOption, Double> bets) {
        if (bets == null || bets.isEmpty()) {
            return Money.ZERO;
        }
        Map<BaccaratClient.BetOption, BigDecimal> staked = normalize(bets);

        BigDecimal worst = Money.ZERO;
        for (MainResult main : MainResult.values()) {
            for (boolean playerPair : new boolean[] {false, true}) {
                for (boolean bankerPair : new boolean[] {false, true}) {
                    worst = Money.max(worst, payoutFor(staked, main, playerPair, bankerPair));
                }
            }
        }
        return worst;
    }

    /** Everything the player has staked across every option. */
    public static BigDecimal totalStake(Map<BaccaratClient.BetOption, Double> bets) {
        BigDecimal total = Money.ZERO;
        if (bets == null) {
            return total;
        }
        for (Double wager : bets.values()) {
            total = Money.add(total, Money.of(wager == null ? 0.0 : wager));
        }
        return total;
    }

    /** This portfolio as the shared budget sees it. */
    public static Exposure exposureOf(Map<BaccaratClient.BetOption, Double> bets) {
        return Exposure.of(totalStake(bets), maxPossiblePayout(bets));
    }

    /**
     * The exposure this player would carry if {@code additional} were staked on
     * {@code option}.
     *
     * <p>Returns the whole updated portfolio rather than the increment: the
     * dealer must cover the total, and a new bet can raise the worst case by
     * more than its own payout by combining with what is already down.
     */
    public static Exposure exposureAfterAdding(
        Map<BaccaratClient.BetOption, Double> bets,
        BaccaratClient.BetOption option,
        double additional
    ) {
        Map<BaccaratClient.BetOption, Double> hypothetical =
            new EnumMap<>(BaccaratClient.BetOption.class);
        if (bets != null) {
            hypothetical.putAll(bets);
        }
        hypothetical.merge(option, additional, Double::sum);
        return exposureOf(hypothetical);
    }

    private static Map<BaccaratClient.BetOption, BigDecimal> normalize(
        Map<BaccaratClient.BetOption, Double> bets) {

        Map<BaccaratClient.BetOption, BigDecimal> staked =
            new EnumMap<>(BaccaratClient.BetOption.class);
        for (BaccaratClient.BetOption option : BaccaratClient.BetOption.values()) {
            Double wager = bets.get(option);
            staked.put(option, Money.of(wager == null ? 0.0 : wager));
        }
        return staked;
    }

    private static BigDecimal payoutFor(
        Map<BaccaratClient.BetOption, BigDecimal> staked,
        MainResult main,
        boolean playerPair,
        boolean bankerPair
    ) {
        BigDecimal payout = Money.ZERO;
        BigDecimal onPlayer = staked.get(BaccaratClient.BetOption.PLAYER);
        BigDecimal onBanker = staked.get(BaccaratClient.BetOption.BANKER);
        BigDecimal onTie = staked.get(BaccaratClient.BetOption.TIE);

        switch (main) {
            case PLAYER_WINS -> payout = Money.add(payout, Money.multiply(onPlayer, PLAYER_WIN));
            case BANKER_WINS -> payout = Money.add(payout, Money.multiply(onBanker, BANKER_WIN));
            case TIE -> {
                payout = Money.add(payout, Money.multiply(onTie, TIE_WIN));
                // A Tie is a push for the main bets, not a loss.
                payout = Money.add(payout, Money.multiply(onPlayer, PUSH));
                payout = Money.add(payout, Money.multiply(onBanker, PUSH));
            }
        }

        if (playerPair) {
            payout = Money.add(payout,
                Money.multiply(staked.get(BaccaratClient.BetOption.PLAYERPAIR), PAIR_WIN));
        }
        if (bankerPair) {
            payout = Money.add(payout,
                Money.multiply(staked.get(BaccaratClient.BetOption.BANKERPAIR), PAIR_WIN));
        }
        return payout;
    }
}
