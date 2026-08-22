package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BlackjackShuffleAnimationPlanTest {

    private static final int CARD_COUNT = 16;
    private static final long HOP = 1L;
    private static final long STAGGER = 2L;

    @Test
    void leftAndRightPathsBothStartAtCenterAndEndBackAtCenter() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        for (BlackjackShuffleAnimationPlan.CardPiece card : cards) {
            List<Integer> path = card.getPath();
            assertEquals(BlackjackShuffleAnimationPlan.CENTER_SLOT, path.get(0), "every card must start at the deck's temporary center slot");
            assertEquals(BlackjackShuffleAnimationPlan.CENTER_SLOT, path.get(path.size() - 1), "every card must end back at the deck's temporary center slot");
        }
    }

    @Test
    void leftPathSweepsTheFullPerimeterInTheExpectedOrder() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        BlackjackShuffleAnimationPlan.CardPiece firstLeft = cards.stream()
            .filter(c -> c.getDirection() == BlackjackShuffleAnimationPlan.CardDirection.LEFT)
            .findFirst().orElseThrow();

        assertEquals(List.of(
            23, 32, 41,
            40, 39, 38,
            29, 20, 11, 2,
            3, 4, 5,
            14, 23
        ), firstLeft.getPath());
        assertEquals(14, firstLeft.getHopCount());
    }

    @Test
    void rightPathSweepsTheFullPerimeterInTheExpectedOrder() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        BlackjackShuffleAnimationPlan.CardPiece firstRight = cards.stream()
            .filter(c -> c.getDirection() == BlackjackShuffleAnimationPlan.CardDirection.RIGHT)
            .findFirst().orElseThrow();

        assertEquals(List.of(
            23, 32, 41,
            42, 43, 44,
            35, 26, 17, 8,
            7, 6, 5,
            14, 23
        ), firstRight.getPath());
        assertEquals(14, firstRight.getHopCount());
    }

    @Test
    void bothDirectionsShareTheSameInitialDescentToTheBottomEdge() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        for (BlackjackShuffleAnimationPlan.CardPiece card : cards) {
            assertEquals(List.of(23, 32, 41), card.getPath().subList(0, 3), "every card, regardless of direction, must descend the same first two hops before splitting");
        }
    }

    @Test
    void cardsAlternateDirectionsStartingWithLeft() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        for (int i = 0; i < cards.size(); i++) {
            BlackjackShuffleAnimationPlan.CardDirection expected = i % 2 == 0
                ? BlackjackShuffleAnimationPlan.CardDirection.LEFT
                : BlackjackShuffleAnimationPlan.CardDirection.RIGHT;
            assertEquals(expected, cards.get(i).getDirection(), "card " + i + " has the wrong direction");
        }
    }

    @Test
    void successiveCardsLaunchExactlyOneStaggerApart() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        for (int i = 1; i < cards.size(); i++) {
            long gap = cards.get(i).getLaunchTick() - cards.get(i - 1).getLaunchTick();
            assertEquals(STAGGER, gap, "card launches must be exactly one stagger apart regardless of direction");
        }
    }

    @Test
    void multipleCardsAreInFlightSimultaneously() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        long inFlightCount = cards.stream().filter(c -> c.slotAt(10L, HOP) != -1).count();
        assertTrue(inFlightCount >= 3, "several cards must be simultaneously visible mid-shuffle, not a strict one-at-a-time sweep");
    }

    @Test
    void frameAtNeverPlacesTwoDifferentCardsOnTheSameSlot() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        List<String> collisions = BlackjackShuffleAnimationPlan.findCollisions(cards, HOP);
        assertTrue(collisions.isEmpty(), "no same-tick same-slot collisions allowed: " + collisions);
    }

    @Test
    void aCardIsNoLongerOccupyingAnySlotOnceItLandsBackInTheDeck() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        BlackjackShuffleAnimationPlan.CardPiece first = cards.get(0);
        long landing = first.landingTick(HOP);
        assertEquals(-1, first.slotAt(landing, HOP), "a card must vacate (be absorbed into the deck) exactly at its own landing tick, not still claim a slot");
        assertTrue(BlackjackShuffleAnimationPlan.distinctTicks(cards, HOP).contains(landing),
            "the plan must emit the empty landing frame that actually clears the card's previous slot");
    }

    @Test
    void finalEmittedFrameContainsNoLingeringTransitCard() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        long finalTick = BlackjackShuffleAnimationPlan.totalDurationTicks(cards, HOP);
        assertTrue(BlackjackShuffleAnimationPlan.distinctTicks(cards, HOP).contains(finalTick));
        assertTrue(BlackjackShuffleAnimationPlan.frameAt(cards, finalTick, HOP).isEmpty(),
            "the last emitted shuffle frame must clear every absorbed card rather than leave the final face-down icon behind");
    }

    @Test
    void totalDurationMatchesTheSlowestCardsOwnLandingTick() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        long expectedMax = cards.stream().mapToLong(c -> c.landingTick(HOP)).max().orElseThrow();
        assertEquals(expectedMax, BlackjackShuffleAnimationPlan.totalDurationTicks(cards, HOP));
        // Both paths are 14 hops, so the last-launched card must be the one
        // that determines the total duration.
        BlackjackShuffleAnimationPlan.CardPiece slowest = cards.stream()
            .max((a, b) -> Long.compare(a.landingTick(HOP), b.landingTick(HOP)))
            .orElseThrow();
        assertEquals(cards.get(cards.size() - 1), slowest);
    }

    @Test
    void shuffleNeverTouchesSeatHeadsOrBrownBetSpotBoundary() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        for (BlackjackShuffleAnimationPlan.CardPiece card : cards) {
            for (int slot : card.getPath()) {
                int column = slot % BlackjackSlotLayout.SEAT_ROW_WIDTH;
                assertTrue(column >= 2, "shuffle path crossed the brown boundary at slot " + slot);
            }
        }
    }

    @Test
    void wholeCardPhaseFitsTheSuperFastBudget() {
        List<BlackjackShuffleAnimationPlan.CardPiece> cards = BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, STAGGER);
        long cardPhaseDuration = BlackjackShuffleAnimationPlan.totalDurationTicks(cards, HOP);
        long deckTravelTicks = (BlackjackShuffleAnimationPlan.DECK_TO_CENTER_PATH.size() - 1) * HOP
            + (BlackjackShuffleAnimationPlan.CENTER_TO_DECK_PATH.size() - 1) * HOP;
        long pauseTicks = 3L; // matches the intended "very slight pause" -- see BlackjackTiming's own constant once wired in
        long total = cardPhaseDuration + deckTravelTicks + pauseTicks;
        assertTrue(total <= 65L, "the whole deck journey (travel + pause + shuffle) must land close to the ~3-second/60-tick target, was " + total);
    }

    @Test
    void deckTravelPathsAreExactReversesOfEachOther() {
        List<Integer> toCenter = BlackjackShuffleAnimationPlan.DECK_TO_CENTER_PATH;
        List<Integer> toHome = BlackjackShuffleAnimationPlan.CENTER_TO_DECK_PATH;
        assertEquals(toCenter.size(), toHome.size());
        for (int i = 0; i < toCenter.size(); i++) {
            assertEquals(toCenter.get(i), toHome.get(toHome.size() - 1 - i), "the return path must be the exact reverse of the outbound one");
        }
        assertEquals(BlackjackSlotLayout.DECK_HOME_SLOT, toCenter.get(0));
        assertEquals(BlackjackShuffleAnimationPlan.CENTER_SLOT, toCenter.get(toCenter.size() - 1));
    }

    @Test
    void buildRejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> BlackjackShuffleAnimationPlan.build(0, HOP, STAGGER));
        assertThrows(IllegalArgumentException.class, () -> BlackjackShuffleAnimationPlan.build(CARD_COUNT, 0L, STAGGER));
        assertThrows(IllegalArgumentException.class, () -> BlackjackShuffleAnimationPlan.build(CARD_COUNT, HOP, 0L));
    }
}
