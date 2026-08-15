package org.nc.nccasino.games.Blackjack;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.nc.nccasino.objects.Card;

/**
 * Immutable, locale-free snapshot of one Blackjack table's shared logical
 * round state at a single instant. Used to paint a newly-opened
 * {@link BlackjackView} with the exact current state instead of a blank or
 * stale board -- every other in-round update reaches open views directly
 * through BlackjackInventory's fan-out renderers, the same way
 * RouletteInventory fans out to RouletteWheelView. Carries no Bukkit types,
 * no Player references, and no localized strings, so it stays trivially
 * unit-testable.
 */
public final class BlackjackFrame {

    public enum Phase { LOBBY, COUNTDOWN, START_TRANSITION, ACTIVE, INSURANCE }

    /**
     * One hand's render-relevant snapshot -- a seat carries a list of these
     * (its hand queue) plus which index is currently active, so a late
     * viewer bootstraps correctly mid-round even with pending (post-split)
     * hands that aren't rendered. Phase 1: every seat has exactly one.
     */
    public static final class HandSnapshot {
        private final List<Card> cards;
        private final double wager;
        private final boolean done;
        private final boolean isActive;

        public HandSnapshot(List<Card> cards, double wager, boolean done, boolean isActive) {
            this.cards = List.copyOf(cards);
            this.wager = wager;
            this.done = done;
            this.isActive = isActive;
        }

        public List<Card> getCards() {
            return cards;
        }

        public double getWager() {
            return wager;
        }

        public boolean isDone() {
            return done;
        }

        public boolean isActive() {
            return isActive;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof HandSnapshot)) {
                return false;
            }
            HandSnapshot other = (HandSnapshot) o;
            return Double.compare(wager, other.wager) == 0
                && done == other.done
                && isActive == other.isActive
                && cards.equals(other.cards);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cards, wager, done, isActive);
        }
    }

    /** One seated player's render-relevant state. */
    public static final class Seat {
        private final UUID playerId;
        private final int seatSlot;
        private final List<HandSnapshot> hands;
        private final int activeHandIndex;
        private final boolean currentTurn;
        private final boolean actionable;

        public Seat(UUID playerId, int seatSlot, List<HandSnapshot> hands, int activeHandIndex, boolean currentTurn, boolean actionable) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.seatSlot = seatSlot;
            if (hands.isEmpty()) {
                throw new IllegalArgumentException("Seat must carry at least one hand snapshot");
            }
            this.hands = List.copyOf(hands);
            if (activeHandIndex < 0 || activeHandIndex >= this.hands.size()) {
                throw new IllegalArgumentException("activeHandIndex out of range: " + activeHandIndex);
            }
            this.activeHandIndex = activeHandIndex;
            this.currentTurn = currentTurn;
            this.actionable = actionable;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public int getSeatSlot() {
            return seatSlot;
        }

        /** This seat's full hand queue -- only {@link #getActiveHandIndex()} is ever rendered into the seat's 7-card row. */
        public List<HandSnapshot> getHands() {
            return hands;
        }

        public int getActiveHandIndex() {
            return activeHandIndex;
        }

        private HandSnapshot activeHand() {
            return hands.get(activeHandIndex);
        }

        /** Convenience: the active hand's wager. */
        public double getWager() {
            return activeHand().getWager();
        }

        /** Convenience: the active hand's cards. */
        public List<Card> getHand() {
            return activeHand().getCards();
        }

        /** Convenience: whether the active hand is done. */
        public boolean isDone() {
            return activeHand().isDone();
        }

        /**
         * Whether this seat is the table's current player -- drives card
         * glow and the status-clock text, replacing the old bet-slip
         * book/paper turn presentation entirely. Stays true through card-deal
         * animations and action evaluation, so cards never stop glowing
         * mid-turn; see {@link #isActionable()} for whether buttons show.
         */
        public boolean isCurrentTurn() {
            return currentTurn;
        }

        /**
         * Whether dynamic action buttons should currently render for this
         * seat. Deliberately distinct from {@link #isCurrentTurn()}: it's
         * false while an action (hit/double-down) is processing, even
         * though the seat is still the current turn and its cards still
         * glow. Never true unless {@link #isCurrentTurn()} is also true.
         */
        public boolean isActionable() {
            return actionable;
        }

        /** Whether this seat has been dealt in and is waiting for its turn (not done, not current). */
        public boolean isAwaitingTurn() {
            return !isDone() && !currentTurn;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Seat)) {
                return false;
            }
            Seat other = (Seat) o;
            return seatSlot == other.seatSlot
                && activeHandIndex == other.activeHandIndex
                && currentTurn == other.currentTurn
                && actionable == other.actionable
                && playerId.equals(other.playerId)
                && hands.equals(other.hands);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, seatSlot, hands, activeHandIndex, currentTurn, actionable);
        }
    }

    private final Phase phase;
    private final int countdownSeconds;
    private final String statusKey;
    private final List<Object> statusPlaceholders;
    private final List<Card> dealerHand;
    private final boolean dealerHoleCardHidden;
    private final int dealerHeadSlot;
    private final List<Seat> seats;

    public BlackjackFrame(
        Phase phase,
        int countdownSeconds,
        String statusKey,
        List<Object> statusPlaceholders,
        List<Card> dealerHand,
        boolean dealerHoleCardHidden,
        List<Seat> seats
    ) {
        this(phase, countdownSeconds, statusKey, statusPlaceholders, dealerHand, dealerHoleCardHidden,
            BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, seats);
    }

    public BlackjackFrame(
        Phase phase,
        int countdownSeconds,
        String statusKey,
        List<Object> statusPlaceholders,
        List<Card> dealerHand,
        boolean dealerHoleCardHidden,
        int dealerHeadSlot,
        List<Seat> seats
    ) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.countdownSeconds = countdownSeconds;
        this.statusKey = Objects.requireNonNull(statusKey, "statusKey");
        this.statusPlaceholders = List.copyOf(statusPlaceholders);
        this.dealerHand = List.copyOf(dealerHand);
        this.dealerHoleCardHidden = dealerHoleCardHidden;
        this.dealerHeadSlot = dealerHeadSlot;
        this.seats = List.copyOf(seats);
    }

    public Phase phase() {
        return phase;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    /**
     * Localization key for the table's current status text. Not tied to a
     * single fixed slot in the 5-seat layout (see BlackjackSlotLayout) --
     * purely descriptive metadata for late-view bootstrap/logging.
     */
    public String statusKey() {
        return statusKey;
    }

    public List<Object> statusPlaceholders() {
        return statusPlaceholders;
    }

    public List<Card> dealerHand() {
        return dealerHand;
    }

    public boolean dealerHoleCardHidden() {
        return dealerHoleCardHidden;
    }

    /**
     * The dealer's current canonical head slot -- {@link BlackjackSlotLayout#DEALER_LOBBY_HEAD_SLOT}
     * (8) before the start-transition animation delivers it to
     * {@link BlackjackSlotLayout#DEALER_INPLAY_HEAD_SLOT} (53). Carried here
     * (separated from rendering) so a late viewer opening mid-animation
     * sees the dealer where it actually is, not a phase-implied constant.
     */
    public int dealerHeadSlot() {
        return dealerHeadSlot;
    }

    public List<Seat> seats() {
        return seats;
    }

    /** Returns the seat currently at {@code seatSlot}, or null if that seat is empty. */
    public Seat seatAt(int seatSlot) {
        for (Seat seat : seats) {
            if (seat.getSeatSlot() == seatSlot) {
                return seat;
            }
        }
        return null;
    }

    /**
     * Resolves a card's localized display name using the "{rank} of {suit}"
     * pattern shared with Client#setCardItem, via caller-supplied key
     * resolvers rather than a Player -- so this stays pure and testable.
     * {@code nameResolver} resolves "cards.name" with rank/suit already
     * substituted; {@code rankSuitResolver} resolves "cards.ranks.two" /
     * "cards.suits.hearts" style leaf keys.
     */
    public static String localizedCardName(Card card, BiFunction<String, Object[], String> nameResolver, Function<String, String> rankSuitResolver) {
        String rank = rankSuitResolver.apply("cards.ranks." + card.getRank().name().toLowerCase(java.util.Locale.ROOT));
        String suit = rankSuitResolver.apply("cards.suits." + card.getSuit().name().toLowerCase(java.util.Locale.ROOT));
        return nameResolver.apply("cards.name", new Object[] {"rank", rank, "suit", suit});
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlackjackFrame)) {
            return false;
        }
        BlackjackFrame other = (BlackjackFrame) o;
        return countdownSeconds == other.countdownSeconds
            && dealerHoleCardHidden == other.dealerHoleCardHidden
            && dealerHeadSlot == other.dealerHeadSlot
            && phase == other.phase
            && statusKey.equals(other.statusKey)
            && statusPlaceholders.equals(other.statusPlaceholders)
            && dealerHand.equals(other.dealerHand)
            && seats.equals(other.seats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phase, countdownSeconds, statusKey, statusPlaceholders, dealerHand, dealerHoleCardHidden, dealerHeadSlot, seats);
    }

    /** Convenience for tests/callers that don't need placeholders. */
    public static List<Object> noPlaceholders() {
        return Collections.emptyList();
    }
}
