package org.nc.nccasino.games.Roulette;

import java.util.Objects;

/**
 * Immutable, locale-free snapshot of one Roulette dealer's shared logical
 * round state at a single instant. Any number of independently-rendered
 * per-player views can translate the same frame into their own localized
 * inventory contents; none of them can observe player identity, display
 * strings, or mutate round state through it. Carries no Bukkit types so it
 * stays trivially unit-testable.
 */
public final class RouletteFrame {

    public enum Phase { BETTING_OPEN, BETS_CLOSED, SPINNING, ROUND_COMPLETE }

    /** No ball currently rendered on the wheel. */
    public static final int NO_BALL = -1;

    private final Phase phase;
    private final int quadrant;
    private final int wheelOffset;
    private final int countdownSeconds;
    private final int ballSlot;
    private final Integer winningNumber;

    public RouletteFrame(
        Phase phase,
        int quadrant,
        int wheelOffset,
        int countdownSeconds,
        int ballSlot,
        Integer winningNumber
    ) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.quadrant = quadrant;
        this.wheelOffset = wheelOffset;
        this.countdownSeconds = countdownSeconds;
        this.ballSlot = ballSlot;
        this.winningNumber = winningNumber;
    }

    public Phase phase() {
        return phase;
    }

    public int quadrant() {
        return quadrant;
    }

    public int wheelOffset() {
        return wheelOffset;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public int ballSlot() {
        return ballSlot;
    }

    public boolean ballVisible() {
        return ballSlot != NO_BALL;
    }

    public Integer winningNumber() {
        return winningNumber;
    }

    public boolean bettingOpen() {
        return phase == Phase.BETTING_OPEN;
    }

    /** Whether a view should currently offer the "open betting table" action. */
    public boolean canOpenBettingTable() {
        return phase == Phase.BETTING_OPEN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RouletteFrame)) {
            return false;
        }
        RouletteFrame other = (RouletteFrame) o;
        return quadrant == other.quadrant
            && wheelOffset == other.wheelOffset
            && countdownSeconds == other.countdownSeconds
            && ballSlot == other.ballSlot
            && phase == other.phase
            && Objects.equals(winningNumber, other.winningNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phase, quadrant, wheelOffset, countdownSeconds, ballSlot, winningNumber);
    }
}
