package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.nc.nccasino.objects.Card;

/**
 * One player's hand -- pure, locale-neutral, no Bukkit types -- carrying
 * enough identity for a table with real per-player hand queues (splitting)
 * to keep track of "which hand" and "which action on that hand" a delayed
 * scheduled callback was meant for.
 *
 * <p><b>Why {@code handId} alone isn't enough:</b> a hand is never captured
 * by its position in its player's hand list, since indexes shift as sibling
 * hands are inserted mid-round (splitting). {@code handId} is a stable,
 * unique identity assigned at creation -- but it only tells a callback
 * *which* hand it's about, not whether the specific action it was scheduled
 * for is still current: a delayed callback from an earlier Hit can still
 * find the same hand present (same {@code handId}, same round) while a
 * *later* action has already superseded it. {@code handGeneration} (an
 * alias in spirit for what the design doc also calls {@code
 * actionSequence}) is incremented every time an action begins, completes,
 * is invalidated, or the hand otherwise advances -- so a scheduled callback
 * must validate {@code roundGeneration + handId + handGeneration +}
 * expected phase/state, never {@code roundGeneration + handId} alone.
 */
public final class BlackjackHand {

    private static final AtomicLong NEXT_HAND_ID = new AtomicLong(1);

    private final long handId;
    private final List<Card> cards = new ArrayList<>();
    private double wager;
    private boolean done;
    private boolean doubled;
    private boolean splitFromAce;
    private boolean fromSplit;
    private double originalPreSplitWager;
    private int handGeneration;

    public BlackjackHand(double wager) {
        this.handId = NEXT_HAND_ID.getAndIncrement();
        this.wager = wager;
        this.originalPreSplitWager = wager;
    }

    public long getHandId() {
        return handId;
    }

    /** Mutable by design (internal controller state, not a value object) -- never a Bukkit type. */
    public List<Card> getCards() {
        return cards;
    }

    /** Appends a card and advances {@link #handGeneration} -- every Hit/deal is itself an action. */
    public void addCard(Card card) {
        cards.add(card);
        bumpGeneration();
    }

    public double getWager() {
        return wager;
    }

    public void setWager(double wager) {
        this.wager = wager;
        bumpGeneration();
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
        bumpGeneration();
    }

    public boolean isDoubled() {
        return doubled;
    }

    public void setDoubled(boolean doubled) {
        this.doubled = doubled;
        bumpGeneration();
    }

    public boolean isSplitFromAce() {
        return splitFromAce;
    }

    public void setSplitFromAce(boolean splitFromAce) {
        this.splitFromAce = splitFromAce;
        bumpGeneration();
    }

    /**
     * True for both hands produced by a split (the hand that kept its
     * original card plus a new one, and its sibling), regardless of rank --
     * distinct from {@link #isSplitFromAce()}, which specifically drives the
     * split-ace action matrix. Used to scope {@code split-21-is-blackjack}:
     * only a split hand's two-card 21 is ever eligible for that payout, an
     * unsplit hand's natural is always eligible regardless of this flag. See
     * {@link BlackjackRules#classify(java.util.List, java.util.List, boolean)}.
     */
    public boolean isFromSplit() {
        return fromSplit;
    }

    public void setFromSplit(boolean fromSplit) {
        this.fromSplit = fromSplit;
        bumpGeneration();
    }

    /** The wager this hand had before any split occurred -- insurance is priced off this, never the post-split wager. */
    public double getOriginalPreSplitWager() {
        return originalPreSplitWager;
    }

    public void setOriginalPreSplitWager(double originalPreSplitWager) {
        this.originalPreSplitWager = originalPreSplitWager;
    }

    /**
     * Bumped every time an action begins, completes, is invalidated, or the
     * hand otherwise advances (Hit resolved, Double resolved, Split
     * occurred, Stand, turn moved on). Callers scheduling a delayed
     * callback capture this value and compare it when the callback fires.
     */
    public int getHandGeneration() {
        return handGeneration;
    }

    /** Explicit advance hook for callers whose action doesn't itself go through addCard/setDone/etc. */
    public void bumpGeneration() {
        handGeneration++;
    }

    /**
     * Whether this hand's own 21 (if it has one) is eligible for the
     * {@code BLACKJACK} 3:2 payout rather than an ordinary 1:1 {@code WIN}
     * -- see {@link BlackjackRules#classify(java.util.List, java.util.List, boolean)}.
     * An unsplit hand is always eligible. A split hand is eligible only when
     * {@code split21IsBlackjackConfig} is enabled AND its 21 was reached on
     * exactly two cards (the replacement card itself made 21) -- never a 21
     * reached via a later Hit.
     */
    public boolean eligibleForNaturalBlackjack(boolean split21IsBlackjackConfig) {
        if (!fromSplit) {
            return true;
        }
        return split21IsBlackjackConfig && cards.size() == 2;
    }
}
