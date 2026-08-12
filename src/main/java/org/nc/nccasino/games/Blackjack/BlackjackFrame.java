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

    public enum Phase { LOBBY, COUNTDOWN, ACTIVE }

    /**
     * What a seat's bet-slot paper/book is currently presenting. Canonical
     * and explicit -- BlackjackInventory sets this at the exact same
     * transitions that fan the rendered item out to every view, never
     * derived from done/currentTurn, so it stays correct through quirks
     * like hit-to-21 and double-down where the slot visibly stays
     * YOUR_TURN until the *next* turn's transition overwrites it.
     */
    public enum BetPresentation { CLICK_BET, YOUR_TURN, TURN_OVER }

    /** The material/localization-key pairing a BetPresentation renders as. */
    public static final class BetSlotRender {
        private final boolean enchanted;
        private final String key;

        public BetSlotRender(boolean enchanted, String key) {
            this.enchanted = enchanted;
            this.key = key;
        }

        public boolean isEnchanted() {
            return enchanted;
        }

        public String getKey() {
            return key;
        }
    }

    /** Pure mapping from presentation state to how BlackjackInventory actually renders it. */
    public static BetSlotRender betSlotRenderFor(BetPresentation presentation) {
        switch (presentation) {
            case YOUR_TURN:
                return new BetSlotRender(true, "blackjack.your-turn");
            case TURN_OVER:
                return new BetSlotRender(false, "blackjack.turn-over");
            case CLICK_BET:
            default:
                return new BetSlotRender(false, "blackjack.click-bet");
        }
    }

    /** One seated player's render-relevant state. */
    public static final class Seat {
        private final UUID playerId;
        private final int seatSlot;
        private final double wager;
        private final List<Card> hand;
        private final boolean done;
        private final boolean currentTurn;
        private final BetPresentation presentation;

        public Seat(UUID playerId, int seatSlot, double wager, List<Card> hand, boolean done, boolean currentTurn, BetPresentation presentation) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.seatSlot = seatSlot;
            this.wager = wager;
            this.hand = List.copyOf(hand);
            this.done = done;
            this.currentTurn = currentTurn;
            this.presentation = Objects.requireNonNull(presentation, "presentation");
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public int getSeatSlot() {
            return seatSlot;
        }

        public double getWager() {
            return wager;
        }

        public List<Card> getHand() {
            return hand;
        }

        public boolean isDone() {
            return done;
        }

        public boolean isCurrentTurn() {
            return currentTurn;
        }

        public BetPresentation getPresentation() {
            return presentation;
        }

        /** Whether this seat has been dealt in and is waiting for its turn (not done, not current). */
        public boolean isAwaitingTurn() {
            return !done && !currentTurn;
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
                && Double.compare(wager, other.wager) == 0
                && done == other.done
                && currentTurn == other.currentTurn
                && playerId.equals(other.playerId)
                && hand.equals(other.hand)
                && presentation == other.presentation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, seatSlot, wager, hand, done, currentTurn, presentation);
        }
    }

    private final Phase phase;
    private final int countdownSeconds;
    private final String leverKey;
    private final List<Object> leverPlaceholders;
    private final List<Card> dealerHand;
    private final boolean dealerHoleCardHidden;
    private final List<Seat> seats;

    public BlackjackFrame(
        Phase phase,
        int countdownSeconds,
        String leverKey,
        List<Object> leverPlaceholders,
        List<Card> dealerHand,
        boolean dealerHoleCardHidden,
        List<Seat> seats
    ) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.countdownSeconds = countdownSeconds;
        this.leverKey = Objects.requireNonNull(leverKey, "leverKey");
        this.leverPlaceholders = List.copyOf(leverPlaceholders);
        this.dealerHand = List.copyOf(dealerHand);
        this.dealerHoleCardHidden = dealerHoleCardHidden;
        this.seats = List.copyOf(seats);
    }

    public Phase phase() {
        return phase;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public String leverKey() {
        return leverKey;
    }

    public List<Object> leverPlaceholders() {
        return leverPlaceholders;
    }

    public List<Card> dealerHand() {
        return dealerHand;
    }

    public boolean dealerHoleCardHidden() {
        return dealerHoleCardHidden;
    }

    public List<Seat> seats() {
        return seats;
    }

    /** Returns the seat currently at {@code chairSlot}, or null if that chair is empty. */
    public Seat seatAt(int chairSlot) {
        for (Seat seat : seats) {
            if (seat.getSeatSlot() == chairSlot) {
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
            && phase == other.phase
            && leverKey.equals(other.leverKey)
            && leverPlaceholders.equals(other.leverPlaceholders)
            && dealerHand.equals(other.dealerHand)
            && seats.equals(other.seats);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phase, countdownSeconds, leverKey, leverPlaceholders, dealerHand, dealerHoleCardHidden, seats);
    }

    /** Convenience for tests/callers that don't need placeholders. */
    public static List<Object> noPlaceholders() {
        return Collections.emptyList();
    }
}
