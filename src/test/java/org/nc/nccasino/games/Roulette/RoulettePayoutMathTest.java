package org.nc.nccasino.games.Roulette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Pair;

class RoulettePayoutMathTest {

    @Test
    void straightUpPayoutStaysExactAtTheLastSafeIntProduct() {
        // 59_652_323 * 36 = 2_147_483_628, one below Integer.MAX_VALUE --
        // the last wager whose straight-up payout still fits in an int.
        assertEquals(2_147_483_628L, RoulettePayoutMath.payoutFor("17 - 35:1", 59_652_323, 17));
    }

    @Test
    void straightUpPayoutDoesNotWrapAboveTheOldIntBoundary() {
        // 59_652_324 * 36 = 2_147_483_664, the first product above
        // Integer.MAX_VALUE -- this used to silently wrap negative and get
        // erased by the payout > 0 guard.
        assertEquals(2_147_483_664L, RoulettePayoutMath.payoutFor("17 - 35:1", 59_652_324, 17));
    }

    @Test
    void losingStraightUpBetPaysNothing() {
        assertEquals(0L, RoulettePayoutMath.payoutFor("17 - 35:1", 100, 18));
    }

    @Test
    void rowDozenColorOddEvenHighLowPayRepresentativeMultiples() {
        assertEquals(300L, RoulettePayoutMath.payoutFor("Bottom Row - 2:1", 100, 4)); // 4 % 3 == 1 -> Bottom
        assertEquals(300L, RoulettePayoutMath.payoutFor("1st Dozen - 2:1", 100, 5));
        assertEquals(200L, RoulettePayoutMath.payoutFor("Red - 1:1", 100, 1));
        assertEquals(0L, RoulettePayoutMath.payoutFor("Black - 1:1", 100, 1));
        assertEquals(200L, RoulettePayoutMath.payoutFor("Odd - 1:1", 100, 1));
        assertEquals(200L, RoulettePayoutMath.payoutFor("Even - 1:1", 100, 2));
        assertEquals(200L, RoulettePayoutMath.payoutFor("1-18 - 1:1", 100, 10));
        assertEquals(200L, RoulettePayoutMath.payoutFor("19-36 - 1:1", 100, 25));
        assertEquals(0L, RoulettePayoutMath.payoutFor("1-18 - 1:1", 100, 25));
    }

    @Test
    void aggregatesMultipleWinningBetsBeyondIntegerMaxValue() {
        // Two individually int-safe straight-up wagers on the same winning
        // number must still sum to their true combined payout, not wrap.
        List<Pair<String, Integer>> bets = List.of(
            new Pair<>("17 - 35:1", 59_652_324),
            new Pair<>("17 - 35:1", 59_652_324)
        );
        RoulettePayoutMath.Result result = RoulettePayoutMath.evaluate(17, bets);

        assertEquals(119_304_648L, result.overallWager);
        assertEquals(4_294_967_328L, result.totalPayout);
        assertTrue(result.totalPayout > Integer.MAX_VALUE);
        RoulettePayoutMath.BetCategoryTotals straightUp = result.categories.get("Straight Up");
        assertEquals(119_304_648L, straightUp.totalWager);
        assertEquals(4_294_967_328L, straightUp.totalPayout);
    }

    @Test
    void losingBetsContributeWagerButNoPayout() {
        List<Pair<String, Integer>> bets = List.of(
            new Pair<>("Red - 1:1", 500),
            new Pair<>("Black - 1:1", 300)
        );
        RoulettePayoutMath.Result result = RoulettePayoutMath.evaluate(1, bets); // 1 is red

        assertEquals(800L, result.overallWager);
        assertEquals(1000L, result.totalPayout);
        RoulettePayoutMath.BetCategoryTotals colors = result.categories.get("Colors");
        assertEquals(800L, colors.totalWager);
        assertEquals(1000L, colors.totalPayout);
    }
}
