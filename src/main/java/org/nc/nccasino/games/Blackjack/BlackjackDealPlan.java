package org.nc.nccasino.games.Blackjack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Locale-neutral description of the initial-deal sequence: who receives
 * each card, which slot it lands in, and how many ticks after the deal
 * starts it's scheduled -- with no Card selection or Bukkit scheduling
 * baked in, so the order/timing can be characterized without a running
 * server.
 *
 * Mirrors BlackjackInventory#dealInitialCards exactly: two rounds of one
 * card per seated, bet-holding player (in seat-iteration order), each round
 * followed by one card to the dealer. The dealer's second-round step is
 * marked {@link Step#isHidden()} -- callers must not draw a Card for it,
 * only reveal one later at showdown, preserving the "hidden card isn't
 * drawn from the shoe until reveal" behavior.
 *
 * Player cards begin two slots after each seat's head (head+2, head+3),
 * matching {@link BlackjackSlotLayout#playerCardSlot}; the dealer's cards
 * land wherever the caller passes as {@code dealerFirstCardSlot}/{@code
 * dealerHiddenCardSlot} (production passes
 * {@link BlackjackSlotLayout#DEALER_UP_CARD_SLOT}/{@link
 * BlackjackSlotLayout#DEALER_HOLE_CARD_SLOT}).
 */
public final class BlackjackDealPlan {

    private BlackjackDealPlan() {
    }

    /** One scheduled deal action. {@code playerId == null} means the dealer. */
    public static final class Step {
        private final UUID playerId;
        private final int slot;
        private final int delayTicks;
        private final boolean hidden;

        Step(UUID playerId, int slot, int delayTicks, boolean hidden) {
            this.playerId = playerId;
            this.slot = slot;
            this.delayTicks = delayTicks;
            this.hidden = hidden;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public boolean isDealer() {
            return playerId == null;
        }

        public int getSlot() {
            return slot;
        }

        public int getDelayTicks() {
            return delayTicks;
        }

        /** True only for the dealer's second card: a placeholder, no Card drawn yet. */
        public boolean isHidden() {
            return hidden;
        }
    }

    public static final class Plan {
        private final List<Step> steps;
        private final int nextDelayTicks;
        private final int stepDelayTicks;

        Plan(List<Step> steps, int nextDelayTicks, int stepDelayTicks) {
            this.steps = steps;
            this.nextDelayTicks = nextDelayTicks;
            this.stepDelayTicks = stepDelayTicks;
        }

        public List<Step> getSteps() {
            return steps;
        }

        /**
         * Delay, in ticks from deal start, at which it's safe to check for
         * an initial blackjack -- one extra step-delay beyond the last
         * scheduled card, matching the original's {@code delay + 20L}.
         */
        public long initialBlackjackCheckDelayTicks() {
            return nextDelayTicks + stepDelayTicks;
        }
    }

    /**
     * Builds the initial-deal plan.
     *
     * @param bettingPlayerOrder seated, bet-holding players in the order they should be dealt to
     * @param seatSlots          seat slot for each player (only entries for players in bettingPlayerOrder are read)
     * @param dealerFirstCardSlot slot for the dealer's face-up first card
     * @param dealerHiddenCardSlot slot for the dealer's hidden placeholder second card
     * @param stepDelayTicks     ticks between successive steps (production passes BlackjackTiming.CARD_DEAL_DELAY_TICKS)
     */
    public static Plan initialDeal(
            List<UUID> bettingPlayerOrder,
            Map<UUID, Integer> seatSlots,
            int dealerFirstCardSlot,
            int dealerHiddenCardSlot,
            int stepDelayTicks
    ) {
        List<Step> steps = new ArrayList<>();
        int delay = 0;

        for (int round = 0; round < 2; round++) {
            for (UUID playerId : bettingPlayerOrder) {
                int seatSlot = seatSlots.get(playerId);
                steps.add(new Step(playerId, BlackjackSlotLayout.playerCardSlot(seatSlot, round), delay, false));
                // (playerCardSlot bounds-checks round < SEAT_CARD_CAPACITY; the initial deal only ever uses round 0/1)
                delay += stepDelayTicks;
            }
            if (round == 0) {
                steps.add(new Step(null, dealerFirstCardSlot, delay, false));
            } else {
                steps.add(new Step(null, dealerHiddenCardSlot, delay, true));
            }
            delay += stepDelayTicks;
        }

        return new Plan(steps, delay, stepDelayTicks);
    }
}
