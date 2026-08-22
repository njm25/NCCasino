package org.nc.nccasino.games.Blackjack;

import java.util.Objects;
import java.util.UUID;

/**
 * Captured identity for one in-flight split operation's scheduled
 * animation steps -- every step must prove it still belongs to the exact
 * same split before mutating or rendering anything: the round/phase
 * haven't moved on, the acting player is still seated at the expected
 * seat, and both hands the split produced still exist by their own stable
 * {@code handId} (never a captured list index or object reference, which a
 * later resplit or the acting player leaving mid-animation could
 * invalidate) <em>and</em> still carry the exact {@code handGeneration}
 * this particular step expects. Complements {@link BlackjackAnimationRun}
 * (which only guards round/phase/animation-generation and the shared task
 * handle) with the split-specific identity checks the table redesign
 * plan's "Stable hand identity" section requires for every scheduled
 * callback.
 *
 * <p><b>Per-step expected generations, not one shared value:</b> the
 * split's own animation legitimately mutates each hand once (the original
 * hand's replacement card lands in step 2, the sibling's in step 3), and
 * each of those mutations bumps that hand's {@code handGeneration} by
 * design. A single guard captured once at the start of the whole sequence
 * would therefore already be "invalidated" by its own animation's second
 * step -- exactly the mistake the plan warns against. Use
 * {@link #withExpectedGenerations} to derive each subsequent step's guard
 * from the previous one, carrying the same identity forward while updating
 * only the generation(s) that step's own prior step legitimately advanced.
 *
 * <p>A random <em>other</em> viewer closing their inventory must never
 * invalidate this guard -- only genuinely table-wide events (round
 * reset/generation change) or the acting player themselves leaving their
 * seat can. Leaving is exactly what {@link #isValid} detects: once the
 * acting player is removed from their seat, {@code currentSeatSlot} comes
 * back null (or their hands come back null/mismatched), and every
 * subsequent step for this operation becomes a harmless no-op.
 */
public final class BlackjackSplitOperationGuard {

    private final UUID playerId;
    private final int seatSlot;
    private final long roundGeneration;
    private final BlackjackFrame.Phase expectedPhase;
    private final long originalHandId;
    private final long siblingHandId;
    private final int expectedOriginalHandGeneration;
    private final int expectedSiblingHandGeneration;

    public BlackjackSplitOperationGuard(
        UUID playerId,
        int seatSlot,
        long roundGeneration,
        BlackjackFrame.Phase expectedPhase,
        long originalHandId,
        long siblingHandId,
        int expectedOriginalHandGeneration,
        int expectedSiblingHandGeneration
    ) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.seatSlot = seatSlot;
        this.roundGeneration = roundGeneration;
        this.expectedPhase = Objects.requireNonNull(expectedPhase, "expectedPhase");
        this.originalHandId = originalHandId;
        this.siblingHandId = siblingHandId;
        this.expectedOriginalHandGeneration = expectedOriginalHandGeneration;
        this.expectedSiblingHandGeneration = expectedSiblingHandGeneration;
    }

    /**
     * Derives the next step's guard: same identity (player, seat, round,
     * phase, both handIds), but with the expected generations updated to
     * whatever this step's own legitimate mutation(s) advanced them to --
     * never re-deriving the whole identity from scratch, so a later step
     * can never accidentally drift onto a different split operation.
     */
    public BlackjackSplitOperationGuard withExpectedGenerations(int expectedOriginalHandGeneration, int expectedSiblingHandGeneration) {
        return new BlackjackSplitOperationGuard(
            playerId, seatSlot, roundGeneration, expectedPhase, originalHandId, siblingHandId,
            expectedOriginalHandGeneration, expectedSiblingHandGeneration
        );
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getSeatSlot() {
        return seatSlot;
    }

    public long getOriginalHandId() {
        return originalHandId;
    }

    public long getSiblingHandId() {
        return siblingHandId;
    }

    public int getExpectedOriginalHandGeneration() {
        return expectedOriginalHandGeneration;
    }

    public int getExpectedSiblingHandGeneration() {
        return expectedSiblingHandGeneration;
    }

    /**
     * @param currentRoundGeneration the table's live round generation
     * @param currentPhase           the table's live captured phase
     * @param currentSeatSlot        the acting player's live seat slot, or null if they're no longer seated
     * @param currentOriginalHand    the hand currently resolved by {@link #getOriginalHandId()}, or null if it no longer exists
     * @param currentSiblingHand     the hand currently resolved by {@link #getSiblingHandId()}, or null if it no longer exists
     */
    public boolean isValid(
        long currentRoundGeneration,
        BlackjackFrame.Phase currentPhase,
        Integer currentSeatSlot,
        BlackjackHand currentOriginalHand,
        BlackjackHand currentSiblingHand
    ) {
        if (roundGeneration != currentRoundGeneration) {
            return false;
        }
        if (expectedPhase != currentPhase) {
            return false;
        }
        if (currentSeatSlot == null || currentSeatSlot != seatSlot) {
            return false;
        }
        if (currentOriginalHand == null
            || currentOriginalHand.getHandId() != originalHandId
            || currentOriginalHand.getHandGeneration() != expectedOriginalHandGeneration) {
            return false;
        }
        return currentSiblingHand != null
            && currentSiblingHand.getHandId() == siblingHandId
            && currentSiblingHand.getHandGeneration() == expectedSiblingHandGeneration;
    }
}
