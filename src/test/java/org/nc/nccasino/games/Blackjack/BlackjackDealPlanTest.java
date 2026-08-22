package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BlackjackDealPlanTest {

    // Production always calls BlackjackDealPlan.initialDeal with this delay
    // (see BlackjackInventory#dealInitialCards); tests derive their
    // expected tick offsets from it instead of repeating raw literals.
    private static final int STEP_DELAY = (int) BlackjackTiming.CARD_DEAL_DELAY_TICKS;

    @Test
    void singlePlayerDealOrderAndDelaysMatchOriginalLoop() {
        UUID player = UUID.randomUUID();
        Map<UUID, Integer> seatSlots = Map.of(player, 9); // seat slot 9 -> bet spot 10, cards at 11/12

        BlackjackDealPlan.Plan plan = BlackjackDealPlan.initialDeal(List.of(player), seatSlots, 2, 3, STEP_DELAY);
        List<BlackjackDealPlan.Step> steps = plan.getSteps();

        assertEquals(4, steps.size());

        // Round 0: player's first card, then dealer's face-up first card.
        assertEquals(player, steps.get(0).getPlayerId());
        assertEquals(11, steps.get(0).getSlot()); // seatSlot + 2 + round(0)
        assertEquals(0, steps.get(0).getDelayTicks());
        assertFalse(steps.get(0).isHidden());

        assertTrue(steps.get(1).isDealer());
        assertEquals(2, steps.get(1).getSlot());
        assertEquals(STEP_DELAY, steps.get(1).getDelayTicks());
        assertFalse(steps.get(1).isHidden());

        // Round 1: player's second card, then dealer's hidden placeholder.
        assertEquals(player, steps.get(2).getPlayerId());
        assertEquals(12, steps.get(2).getSlot()); // seatSlot + 2 + round(1)
        assertEquals(2 * STEP_DELAY, steps.get(2).getDelayTicks());
        assertFalse(steps.get(2).isHidden());

        assertTrue(steps.get(3).isDealer());
        assertEquals(3, steps.get(3).getSlot());
        assertEquals(3 * STEP_DELAY, steps.get(3).getDelayTicks());
        assertTrue(steps.get(3).isHidden());

        // Original: delay is 4*STEP_DELAY by the time the loop ends
        // (3*STEP_DELAY + STEP_DELAY for the final increment after the
        // hidden card), then one more STEP_DELAY for the initial-blackjack
        // check.
        assertEquals(5L * STEP_DELAY, plan.initialBlackjackCheckDelayTicks());
    }

    @Test
    void multiplePlayersAreDealtInOrderBeforeEachDealerCard() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Map<UUID, Integer> seatSlots = new LinkedHashMap<>();
        seatSlots.put(first, 9);
        seatSlots.put(second, 18);

        BlackjackDealPlan.Plan plan = BlackjackDealPlan.initialDeal(List.of(first, second), seatSlots, 2, 3, STEP_DELAY);
        List<BlackjackDealPlan.Step> steps = plan.getSteps();

        assertEquals(6, steps.size());
        // Round 0: first, second, then dealer.
        assertEquals(first, steps.get(0).getPlayerId());
        assertEquals(second, steps.get(1).getPlayerId());
        assertTrue(steps.get(2).isDealer());
        assertFalse(steps.get(2).isHidden());
        // Round 1: first, second, then dealer hidden.
        assertEquals(first, steps.get(3).getPlayerId());
        assertEquals(second, steps.get(4).getPlayerId());
        assertTrue(steps.get(5).isDealer());
        assertTrue(steps.get(5).isHidden());

        // Strictly increasing delays, one stepDelayTicks apart.
        for (int i = 1; i < steps.size(); i++) {
            assertEquals(steps.get(i - 1).getDelayTicks() + STEP_DELAY, steps.get(i).getDelayTicks());
        }
    }

    @Test
    void exactlyOneStepIsHiddenAndItIsTheDealersSecondCard() {
        // The plan's own type system is the seam: only the dealer's
        // second-round step can be marked hidden, and a hidden step
        // carries no Card -- BlackjackInventory must not (and structurally
        // cannot, via this API) draw one for it before reveal time.
        UUID player = UUID.randomUUID();
        Map<UUID, Integer> seatSlots = Map.of(player, 9);
        BlackjackDealPlan.Plan plan = BlackjackDealPlan.initialDeal(List.of(player), seatSlots, 2, 3, STEP_DELAY);

        long hiddenCount = plan.getSteps().stream().filter(BlackjackDealPlan.Step::isHidden).count();
        assertEquals(1, hiddenCount);

        BlackjackDealPlan.Step hiddenStep = plan.getSteps().stream()
            .filter(BlackjackDealPlan.Step::isHidden)
            .findFirst()
            .orElseThrow();
        assertTrue(hiddenStep.isDealer());
        assertEquals(3, hiddenStep.getSlot());
    }

    @Test
    void noBettingPlayersStillDealsOnlyTheDealerTwoCards() {
        BlackjackDealPlan.Plan plan = BlackjackDealPlan.initialDeal(List.of(), Map.of(), 2, 3, STEP_DELAY);
        List<BlackjackDealPlan.Step> steps = plan.getSteps();

        assertEquals(2, steps.size());
        assertTrue(steps.get(0).isDealer());
        assertFalse(steps.get(0).isHidden());
        assertEquals(0, steps.get(0).getDelayTicks());
        assertTrue(steps.get(1).isDealer());
        assertTrue(steps.get(1).isHidden());
        assertEquals(STEP_DELAY, steps.get(1).getDelayTicks());
        assertEquals(3L * STEP_DELAY, plan.initialBlackjackCheckDelayTicks());
    }

    @Test
    void initialBlackjackCheckDelayUsesTheSuppliedStepDelayNotAHardcodedOne() {
        // Guards Plan#initialBlackjackCheckDelayTicks actually using the
        // stepDelayTicks it was built with, rather than a baked-in 20L.
        UUID player = UUID.randomUUID();
        Map<UUID, Integer> seatSlots = Map.of(player, 9);
        int oddStepDelay = 7;

        BlackjackDealPlan.Plan plan = BlackjackDealPlan.initialDeal(List.of(player), seatSlots, 2, 3, oddStepDelay);

        assertEquals(5L * oddStepDelay, plan.initialBlackjackCheckDelayTicks());
    }

    @Test
    void playerCardSlotsMatchBlackjackSlotLayoutForEverySeat() {
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            UUID player = UUID.randomUUID();
            Map<UUID, Integer> seatSlots = Map.of(player, seatSlot);
            BlackjackDealPlan.Plan plan = BlackjackDealPlan.initialDeal(
                List.of(player), seatSlots,
                BlackjackSlotLayout.DEALER_UP_CARD_SLOT, BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT,
                STEP_DELAY
            );
            List<BlackjackDealPlan.Step> playerSteps = plan.getSteps().stream().filter(s -> !s.isDealer()).toList();
            assertEquals(BlackjackSlotLayout.playerCardSlot(seatSlot, 0), playerSteps.get(0).getSlot());
            assertEquals(BlackjackSlotLayout.playerCardSlot(seatSlot, 1), playerSteps.get(1).getSlot());
        }
    }

    @Test
    void dealerCardsUseTheDealerRowSlotsFromBlackjackSlotLayout() {
        UUID player = UUID.randomUUID();
        Map<UUID, Integer> seatSlots = Map.of(player, 9);
        BlackjackDealPlan.Plan plan = BlackjackDealPlan.initialDeal(
            List.of(player), seatSlots,
            BlackjackSlotLayout.DEALER_UP_CARD_SLOT, BlackjackSlotLayout.DEALER_HOLE_CARD_SLOT,
            STEP_DELAY
        );
        List<BlackjackDealPlan.Step> dealerSteps = plan.getSteps().stream().filter(BlackjackDealPlan.Step::isDealer).toList();
        assertEquals(BlackjackSlotLayout.dealerCardSlot(0), dealerSteps.get(0).getSlot());
        assertEquals(BlackjackSlotLayout.dealerCardSlot(1), dealerSteps.get(1).getSlot());
    }
}
