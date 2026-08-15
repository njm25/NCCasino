package org.nc.nccasino.games.Blackjack;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.scheduler.BukkitTask;

/**
 * Runtime wrapper around one scheduled animation sequence (built from one of
 * the pure {@code Blackjack*Plan} classes) -- carries enough captured
 * identity for every scheduled step to validate itself as still current
 * before touching shared state, and enough of a Bukkit task handle to be
 * cancelled outright.
 *
 * <p>Two categories, distinguished solely by {@link #getViewerId()}:
 * <ul>
 *   <li><b>Private</b> (viewerId non-null): chair guide, wager guide,
 *       bet-spot blink, door reveal/conceal, action guide -- cancelled the
 *       instant that viewer's own inventory closes/quits, never affecting
 *       any other viewer.</li>
 *   <li><b>Shared/table-owned</b> (viewerId == null): the dealer U-path
 *       inspection and the split slide/park/reactivate sequence -- owned by
 *       the table, never cancelled just because one viewer closes their
 *       inventory. Only genuinely table-wide events end it: reset/cancel,
 *       dealer replacement, plugin shutdown, or the animation's own natural
 *       completion.</li>
 * </ul>
 *
 * <p>Callers are responsible for actually respecting that scoping rule --
 * this class only exposes {@link #isShared()}/{@link #isPrivate()} and a
 * {@link #cancel()} method; it does not enforce who may call cancel() when.
 * See BlackjackInventory's cancelPrivateAnimation/cancelSharedAnimation for
 * where that rule is actually applied.
 */
public final class BlackjackAnimationRun {

    private final UUID viewerId; // null = shared/table-owned
    private final long roundGeneration;
    private final int animationGeneration;
    private final BlackjackFrame.Phase expectedPhase;
    private BukkitTask task;
    private boolean cancelled;

    public BlackjackAnimationRun(UUID viewerId, long roundGeneration, int animationGeneration, BlackjackFrame.Phase expectedPhase) {
        this.viewerId = viewerId;
        this.roundGeneration = roundGeneration;
        this.animationGeneration = animationGeneration;
        this.expectedPhase = Objects.requireNonNull(expectedPhase, "expectedPhase");
    }

    /** True for a shared/table-owned run (dealer U-path, split sequence). */
    public boolean isShared() {
        return viewerId == null;
    }

    /** True for a private, per-viewer run (chair guide, wager guide, bet-spot blink, door reveal/conceal, action guide). */
    public boolean isPrivate() {
        return viewerId != null;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public long getRoundGeneration() {
        return roundGeneration;
    }

    public int getAnimationGeneration() {
        return animationGeneration;
    }

    public BlackjackFrame.Phase getExpectedPhase() {
        return expectedPhase;
    }

    /**
     * Whether a step scheduled under this run is still valid. A pure
     * comparison against caller-supplied current values -- deliberately
     * never reads any live BlackjackInventory state itself, so it stays
     * testable without a running server. Every scheduled step must check
     * this (or an equivalent) before mutating shared state, mirroring the
     * roundGeneration+handId+handGeneration+expected-state pattern already
     * used for hit/double-down callbacks.
     */
    public boolean isStale(long currentRoundGeneration, int currentAnimationGeneration, BlackjackFrame.Phase currentPhase) {
        return cancelled
            || roundGeneration != currentRoundGeneration
            || animationGeneration != currentAnimationGeneration
            || expectedPhase != currentPhase;
    }

    /** Attaches the Bukkit task this run's steps were actually scheduled under, so {@link #cancel()} can stop it. */
    public void attachTask(BukkitTask task) {
        this.task = task;
    }

    /**
     * Cancels this run's scheduled Bukkit task, if any, and marks it stale
     * for any in-flight step. Idempotent. Callers must respect the
     * cancellation-scope rule documented on the class: only call this for a
     * private run on that viewer's own close/quit, or for the shared run on
     * a genuinely table-wide event -- never for the shared run just because
     * one viewer closed their inventory.
     */
    public void cancel() {
        cancelled = true;
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
