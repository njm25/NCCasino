package org.nc.nccasino.games.Roulette;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.nc.nccasino.objects.Pair;

/**
 * Pure roulette spin-result evaluation, extracted out of the ~1,400-line
 * Bukkit-backed {@link BettingTable} so it's testable without a live
 * inventory. Every accumulator here is long -- an individual bet's wager is
 * still bounded to int-range by {@link org.nc.nccasino.currency.MoneyHelper#toWagerUnits},
 * but a straight-up payout is that wager times 36, and both per-category and
 * round totals sum many bets together, any of which can carry the result
 * past Integer.MAX_VALUE even though no single wager does.
 */
final class RoulettePayoutMath {

    private RoulettePayoutMath() {
    }

    static final class BetCategoryTotals {
        long totalWager;
        long totalPayout;
    }

    static final class Result {
        final Map<String, BetCategoryTotals> categories;
        final long overallWager;
        final long totalPayout;

        Result(Map<String, BetCategoryTotals> categories, long overallWager, long totalPayout) {
            this.categories = categories;
            this.overallWager = overallWager;
            this.totalPayout = totalPayout;
        }
    }

    /** Evaluates every bet against {@code result}, aggregating wagers and payouts by category and in total. */
    static Result evaluate(int result, List<Pair<String, Integer>> bets) {
        Map<String, BetCategoryTotals> categoryMap = new LinkedHashMap<>();
        long overallWager = 0L;
        long totalPayout = 0L;

        for (Pair<String, Integer> bet : bets) {
            String betType = bet.getFirst();
            long wager = bet.getSecond();

            String categoryName = parseCategory(betType);
            BetCategoryTotals cat = categoryMap.computeIfAbsent(categoryName, k -> new BetCategoryTotals());
            cat.totalWager += wager;
            overallWager += wager;

            long payout = payoutFor(betType, wager, result);
            if (payout > 0) {
                cat.totalPayout += payout;
                totalPayout += payout;
            }
        }

        return new Result(categoryMap, overallWager, totalPayout);
    }

    static long payoutFor(String betType, long wager, int result) {
        if (betType.equalsIgnoreCase(result + " - 35:1")) {
            return wager * 36;
        } else if (result != 0 && betType.contains("Row - 2:1") && betType.toLowerCase().contains(getColumn(result).toLowerCase() + " row")) {
            // getColumn(0) falls through to "Top" and getDozen(0) falls
            // through to "3rd" purely as an artifact of their %/range
            // checks not having a zero case of their own -- zero has no
            // row or dozen in real roulette and must lose every outside
            // bet, so it's excluded here rather than by redesigning those
            // helpers to return a "none" column/dozen for it.
            return wager * 3;
        } else if (result != 0 && betType.contains("Dozen - 2:1") && betType.toLowerCase().contains(getDozen(result).toLowerCase() + " dozen")) {
            return wager * 3;
        } else if (betType.equalsIgnoreCase("red - 1:1") && isRed(result)) {
            return wager * 2;
        } else if (betType.equalsIgnoreCase("black - 1:1") && isBlack(result)) {
            return wager * 2;
        } else if (betType.equalsIgnoreCase(getOddEven(result))) {
            return wager * 2;
        } else if (betType.equals("1-18 - 1:1") && (1 <= result && result <= 18)) {
            return wager * 2;
        } else if (betType.equals("19-36 - 1:1") && (19 <= result && result <= 36)) {
            return wager * 2;
        }
        return 0L;
    }

    static String parseCategory(String betType) {
        betType = betType.toLowerCase();

        if (betType.contains("dozen")) {
            return "Dozens";
        } else if (betType.contains("row")) {
            return "Rows";
        } else if (betType.contains("red") || betType.contains("black")) {
            return "Colors";
        } else if (betType.contains("odd") || betType.contains("even")) {
            return "Odd/Even";
        } else if (betType.contains("1-18") || betType.contains("19-36")) {
            return "High/Low";
        }

        return "Straight Up";
    }

    static String getColumn(int result) {
        if (result % 3 == 1) {
            return "Bottom";
        } else if (result % 3 == 2) {
            return "Middle";
        } else {
            return "Top";
        }
    }

    static String getDozen(int result) {
        if (result >= 1 && result <= 12) {
            return "1st";
        } else if (result >= 13 && result <= 24) {
            return "2nd";
        } else {
            return "3rd";
        }
    }

    static boolean isRed(int result) {
        int[] redNumbers = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};
        for (int num : redNumbers) {
            if (num == result) {
                return true;
            }
        }
        return false;
    }

    static boolean isBlack(int result) {
        int[] blackNumbers = {2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35};
        for (int num : blackNumbers) {
            if (num == result) {
                return true;
            }
        }
        return false;
    }

    static String getOddEven(int result) {
        if (result == 0) {
            return "none";
        }
        return (result % 2 == 0) ? "Even - 1:1" : "Odd - 1:1";
    }
}
