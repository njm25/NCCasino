package org.nc.nccasino.games.Blackjack;

import java.util.Objects;

/**
 * One scheduled step of a pure animation plan -- which slot, how many ticks
 * after the plan starts, and what kind of visual change happens there.
 * Shared shape across every {@code Blackjack*Plan} class in this package
 * (mirrors {@link BlackjackDealPlan.Step}) so the runtime wrapper
 * ({@link BlackjackAnimationRun}) has one uniform type to schedule
 * regardless of which plan produced it. Zero Bukkit types -- pure data.
 */
public final class BlackjackAnimationStep {

    /** What visual change a step represents -- interpreted by the runtime renderer, not by any plan class. */
    public enum Kind {
        /** Turn glow on at this slot. */
        GLOW_ON,
        /** Turn glow off at this slot. */
        GLOW_OFF,
        /** Reveal (paint in) whatever belongs at this slot. */
        REVEAL,
        /** Conceal (paint over/remove) whatever belongs at this slot. */
        CONCEAL,
        /** The dealer (or another mover) occupies this slot next. */
        MOVE,
        /** A card/hand slides out of this slot toward its new position. */
        SLIDE_OUT,
        /** A replacement card is dealt into this slot. */
        DEAL,
        /** A hand is parked (marked inactive/pending, no visible slot) at this slot's owner. */
        PARK,
        /** A pending hand is reactivated into this slot. */
        REACTIVATE
    }

    private final int slot;
    private final long delayTicks;
    private final Kind kind;

    public BlackjackAnimationStep(int slot, long delayTicks, Kind kind) {
        this.slot = slot;
        this.delayTicks = delayTicks;
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public int getSlot() {
        return slot;
    }

    public long getDelayTicks() {
        return delayTicks;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlackjackAnimationStep)) {
            return false;
        }
        BlackjackAnimationStep other = (BlackjackAnimationStep) o;
        return slot == other.slot && delayTicks == other.delayTicks && kind == other.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, delayTicks, kind);
    }

    @Override
    public String toString() {
        return "BlackjackAnimationStep{slot=" + slot + ", delayTicks=" + delayTicks + ", kind=" + kind + "}";
    }
}
