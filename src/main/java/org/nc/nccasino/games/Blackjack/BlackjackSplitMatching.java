package org.nc.nccasino.games.Blackjack;

/**
 * How a two-card hand's cards must match to be split -- configurable per
 * dealer ({@code dealers.<name>.splitting.matching}), not fixed policy.
 */
public enum BlackjackSplitMatching {
    /** Only identical ranks split (e.g. King-King, but not King-Queen). */
    SAME_RANK,
    /** Any two ten-value cards split (e.g. King-Queen, Ten-Jack, as well as King-King). */
    SAME_VALUE
}
