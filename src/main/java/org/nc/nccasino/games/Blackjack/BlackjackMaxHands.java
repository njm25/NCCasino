package org.nc.nccasino.games.Blackjack;

import java.util.Objects;
import java.util.Optional;

/**
 * A player's per-table maximum hand-queue length -- either genuinely
 * unbounded, or a validated integer &gt;= 2. Modeled as a small value type
 * wrapping {@code Optional<Integer>} (empty = unbounded) rather than a
 * large sentinel magic number, per the table redesign plan. This limit
 * applies per player's own hand queue, never as a table-wide total.
 */
public final class BlackjackMaxHands {

    private static final BlackjackMaxHands UNBOUNDED = new BlackjackMaxHands(Optional.empty());

    private final Optional<Integer> limit;

    private BlackjackMaxHands(Optional<Integer> limit) {
        this.limit = limit;
    }

    public static BlackjackMaxHands unbounded() {
        return UNBOUNDED;
    }

    /** @param limit must be &gt;= 2 */
    public static BlackjackMaxHands limited(int limit) {
        if (limit < 2) {
            throw new IllegalArgumentException("max-hands must be >= 2: " + limit);
        }
        return new BlackjackMaxHands(Optional.of(limit));
    }

    public boolean isUnbounded() {
        return limit.isEmpty();
    }

    /** Present only when {@link #isUnbounded()} is false. */
    public Optional<Integer> limit() {
        return limit;
    }

    /** Whether a player currently holding {@code currentHandCount} hands may split again. */
    public boolean allowsAnotherHand(int currentHandCount) {
        return limit.map(max -> currentHandCount < max).orElse(true);
    }

    /** The exact string this value should be persisted as in config ({@code "UNBOUNDED"} or the integer). */
    public String configValue() {
        return limit.map(String::valueOf).orElse("UNBOUNDED");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlackjackMaxHands)) {
            return false;
        }
        return limit.equals(((BlackjackMaxHands) o).limit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(limit);
    }

    @Override
    public String toString() {
        return "BlackjackMaxHands[" + configValue() + "]";
    }
}
