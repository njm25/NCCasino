package org.nc.nccasino.games.Slots;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the largest payout multiplier that the configured reel strips can
 * actually produce for one geometry and active payline prefix.
 *
 * <p>The former liability probe multiplied the best single-line jackpot by
 * the number of active lines. That is safe, but it assumes every line can be
 * a full-width Seven simultaneously. The shared reel window makes that
 * impossible: one selected stop supplies every visible cell on that reel,
 * and the deliberately spaced strip cannot put a Seven on all of those rows.
 * This dynamic program walks the real windows one reel at a time and retains
 * the best accrued return for each still-live set of left-anchored runs. It
 * therefore remains an upper bound while no longer rejecting bets on an
 * outcome the machine cannot generate.
 */
final class SlotsReachablePayoutCeiling {

    private static final int DEAD = 1;
    private static final int BITS_PER_LINE = 6;
    private static final long LINE_MASK = (1L << BITS_PER_LINE) - 1L;

    private SlotsReachablePayoutCeiling() {
    }

    static double maximumMultiplier(int visibleRows, int activeLines, SlotsPaytable paytable) {
        if (paytable == null) {
            throw new IllegalArgumentException("paytable must not be null");
        }
        int columns = paytable.columns();
        List<SlotsPaylineCatalog.Line> lines =
            SlotsPaylineCatalog.active(columns, visibleRows, activeLines);
        Map<Long, Double> states = Map.of(0L, 0.0);

        for (int column = 0; column < columns; column++) {
            List<SlotsSymbol[]> windows = distinctWindows(paytable.variance(), column, visibleRows);
            int[] lineRows = new int[lines.size()];
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                lineRows[lineIndex] = lines.get(lineIndex).rows()[column];
            }
            Map<Long, Double> next = new HashMap<>();
            for (Map.Entry<Long, Double> entry : states.entrySet()) {
                for (SlotsSymbol[] window : windows) {
                    Transition transition = advance(
                        entry.getKey(), entry.getValue(), window, lineRows, column, paytable);
                    next.merge(transition.state(), transition.accruedMultiplier(), Math::max);
                }
            }
            states = next;
        }

        double maximum = 0.0;
        for (Map.Entry<Long, Double> entry : states.entrySet()) {
            double completed = entry.getValue() + finishLiveRuns(entry.getKey(), lines.size(), paytable);
            maximum = Math.max(maximum, completed);
        }
        return maximum;
    }

    private static Transition advance(
        long state,
        double accrued,
        SlotsSymbol[] window,
        int[] lineRows,
        int column,
        SlotsPaytable paytable
    ) {
        long nextState = 0L;
        double nextAccrued = accrued;
        for (int lineIndex = 0; lineIndex < lineRows.length; lineIndex++) {
            SlotsSymbol landed = window[lineRows[lineIndex]];
            int code = codeAt(state, lineIndex);
            int nextCode;

            if (column == 0) {
                nextCode = landed.pays() ? encodeActive(landed, 1) : DEAD;
            } else if (code == DEAD) {
                nextCode = DEAD;
            } else {
                SlotsSymbol running = decodeSymbol(code);
                int run = decodeRun(code);
                if (landed == running) {
                    nextCode = encodeActive(running, run + 1);
                } else {
                    nextAccrued += paytable.multiplier(running, run);
                    nextCode = DEAD;
                }
            }
            nextState |= (long) nextCode << (lineIndex * BITS_PER_LINE);
        }
        return new Transition(nextState, nextAccrued);
    }

    private static double finishLiveRuns(long state, int lineCount, SlotsPaytable paytable) {
        double payout = 0.0;
        for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
            int code = codeAt(state, lineIndex);
            if (code != DEAD) {
                payout += paytable.multiplier(decodeSymbol(code), decodeRun(code));
            }
        }
        return payout;
    }

    /** Deduplicates the 100 stops that show the same visible symbol window. */
    private static List<SlotsSymbol[]> distinctWindows(
        SlotsVariance variance, int reelIndex, int visibleRows) {

        SlotsReelStrip strip = SlotsReelStrip.forReel(variance, reelIndex);
        Map<String, SlotsSymbol[]> unique = new LinkedHashMap<>();
        for (int stop = 0; stop < strip.size(); stop++) {
            SlotsSymbol[] window = strip.window(stop, visibleRows);
            StringBuilder key = new StringBuilder(window.length);
            for (SlotsSymbol symbol : window) {
                key.append((char) ('A' + symbol.ordinal()));
            }
            unique.putIfAbsent(key.toString(), window);
        }
        return List.copyOf(unique.values());
    }

    private static int codeAt(long state, int lineIndex) {
        return (int) ((state >>> (lineIndex * BITS_PER_LINE)) & LINE_MASK);
    }

    private static int encodeActive(SlotsSymbol symbol, int run) {
        return 2 + (symbol.ordinal() * 8) + run;
    }

    private static SlotsSymbol decodeSymbol(int code) {
        return SlotsSymbol.values()[(code - 2) / 8];
    }

    private static int decodeRun(int code) {
        return (code - 2) % 8;
    }

    private record Transition(long state, double accruedMultiplier) {
    }
}
