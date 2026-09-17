package org.nc.nccasino.games.Roulette;

import org.nc.nccasino.budget.Exposure;
import org.nc.nccasino.budget.Money;
import org.nc.nccasino.objects.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Roulette table could owe one player, evaluated against every pocket
 * the wheel can land in.
 *
 * <h2>Why the whole portfolio, and why every result</h2>
 *
 * <p>Roulette bets are not independent. A player covering red, even, and the
 * first dozen wins all three at once on a 12, and the dealer owes the sum. Any
 * approximation -- the largest single bet, the sum of the best few, a
 * multiplier on the total stake -- is wrong in a way that is only discovered
 * when a real portfolio pays more than the dealer reserved.
 *
 * <p>So the worst case is computed the only way that is actually correct:
 * price the entire portfolio against all 37 results and take the maximum, using
 * the same {@link RoulettePayoutMath} a real spin settles with. 37 evaluations
 * of a handful of bets is trivial next to being wrong about money.
 */
final class RouletteLiability {

    /** European single-zero wheel: pockets 0 through 36. */
    static final int POCKETS = 37;

    private RouletteLiability() {
    }

    /**
     * The largest total this portfolio could pay on any single spin.
     *
     * <p>Gross: it includes the returned stake, because that is what actually
     * has to leave the dealer at settlement.
     */
    static long maxPossiblePayout(List<Pair<String, Integer>> bets) {
        if (bets == null || bets.isEmpty()) {
            return 0L;
        }
        long worst = 0L;
        for (int result = 0; result < POCKETS; result++) {
            long payout = RoulettePayoutMath.evaluate(result, bets).totalPayout;
            if (payout > worst) {
                worst = payout;
            }
        }
        return worst;
    }

    /** Everything the player has staked on the table. */
    static long totalStake(List<Pair<String, Integer>> bets) {
        if (bets == null) {
            return 0L;
        }
        long total = 0L;
        for (Pair<String, Integer> bet : bets) {
            total += bet.getSecond();
        }
        return total;
    }

    /** This portfolio as the shared budget sees it. */
    static Exposure exposureOf(List<Pair<String, Integer>> bets) {
        return Exposure.of(Money.of(totalStake(bets)), Money.of(maxPossiblePayout(bets)));
    }

    /**
     * The exposure the table would carry if {@code betType} were added at
     * {@code wagerAmount}.
     *
     * <p>Used to decide before taking the bet, so a bet that would push the
     * portfolio past what the dealer can cover is refused while the player
     * still has their money. The returned exposure is the whole updated
     * portfolio, not the increment -- the dealer must cover the total, and a
     * new bet can raise the worst case by more than its own payout by
     * combining with bets already on the table.
     */
    static Exposure exposureAfterAdding(
        List<Pair<String, Integer>> currentBets, String betType, int wagerAmount) {

        List<Pair<String, Integer>> hypothetical =
            new ArrayList<>(currentBets == null ? List.of() : currentBets);
        hypothetical.add(new Pair<>(betType, wagerAmount));
        return exposureOf(hypothetical);
    }
}
