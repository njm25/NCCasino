package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;

/**
 * Characterizes BlackjackFrame: an immutable, locale-free snapshot with no
 * Bukkit/Player/localized-string dependencies, and the pure functions
 * BlackjackInventory actually calls through to render every card (see
 * BlackjackInventory#localizedCardName). Turn state is expressed solely via
 * Seat#isCurrentTurn -- there is no bet-slip/book presentation model to
 * reconstruct anymore; the current player's cards glow instead (see
 * BlackjackInventory's card-glow rendering).
 *
 * A Seat now carries a hand queue (List<HandSnapshot> + activeHandIndex)
 * instead of a single hand/wager/done triple, laying the state shape real
 * splitting will use later -- this phase, every seat has exactly one hand.
 *
 * Card has no equals/hashCode (see CardTest) -- tests that need two equal
 * frames/hands reuse the same Card instances rather than relying on value
 * equality, per Card's identity semantics.
 */
class BlackjackFrameTest {

    private static final Card ACE_SPADES = new Card(Suit.SPADES, Rank.ACE);
    private static final Card KING_HEARTS = new Card(Suit.HEARTS, Rank.KING);
    private static final Card TEN_CLUBS = new Card(Suit.CLUBS, Rank.TEN);

    private static BlackjackFrame.HandSnapshot handSnapshot(List<Card> hand, double wager, boolean done) {
        return new BlackjackFrame.HandSnapshot(hand, wager, done, true);
    }

    private static BlackjackFrame.Seat seat(
        UUID player, int slot, double wager, List<Card> hand, boolean done, boolean currentTurn
    ) {
        return new BlackjackFrame.Seat(player, slot, List.of(handSnapshot(hand, wager, done)), 0, currentTurn, currentTurn);
    }

    private static BlackjackFrame.Seat seat(
        UUID player, int slot, double wager, List<Card> hand, boolean done, boolean currentTurn, boolean actionable
    ) {
        return new BlackjackFrame.Seat(player, slot, List.of(handSnapshot(hand, wager, done)), 0, currentTurn, actionable);
    }

    // --- no Bukkit types, no Player references, no localized strings ---

    @Test
    void frameAndSeatDeclareNoBukkitOrPlayerTypes() {
        for (Class<?> type : new Class<?>[] {BlackjackFrame.class, BlackjackFrame.Seat.class, BlackjackFrame.HandSnapshot.class}) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic()) {
                    continue;
                }
                String packageName = field.getType().getPackageName();
                assertFalse(packageName.startsWith("org.bukkit"), field + " must not reference a Bukkit type");
                assertNotEquals("org.bukkit.entity.Player", field.getType().getName());
            }
        }
    }

    // --- frame independence/immutability ---

    @Test
    void seatHandIsDefensivelyCopiedAndImmutable() {
        List<Card> mutableHand = new java.util.ArrayList<>();
        mutableHand.add(ACE_SPADES);
        BlackjackFrame.Seat seat = seat(UUID.randomUUID(), 9, 50, mutableHand, false, false);

        mutableHand.add(KING_HEARTS); // mutate the original list after construction
        assertEquals(1, seat.getHand().size(), "Seat's hand must not observe later mutation of the source list");

        assertThrows(UnsupportedOperationException.class, () -> seat.getHand().add(TEN_CLUBS));
    }

    @Test
    void frameSeatsAndDealerHandAreDefensivelyCopiedAndImmutable() {
        List<BlackjackFrame.Seat> mutableSeats = new java.util.ArrayList<>();
        List<Card> dealerHand = new java.util.ArrayList<>(List.of(TEN_CLUBS));

        BlackjackFrame frame = new BlackjackFrame(
            BlackjackFrame.Phase.ACTIVE, 0, "blackjack.dealer-turn", BlackjackFrame.noPlaceholders(),
            dealerHand, false, mutableSeats
        );

        mutableSeats.add(seat(UUID.randomUUID(), 9, 10, List.of(), false, false));
        dealerHand.add(KING_HEARTS);

        assertEquals(0, frame.seats().size(), "Frame's seat list must not observe later mutation of the source list");
        assertEquals(1, frame.dealerHand().size(), "Frame's dealer hand must not observe later mutation of the source list");
        assertThrows(UnsupportedOperationException.class, () -> frame.seats().add(seat(UUID.randomUUID(), 18, 5, List.of(), false, false)));
        assertThrows(UnsupportedOperationException.class, () -> frame.dealerHand().add(ACE_SPADES));
    }

    // --- a Seat requires at least one hand snapshot, and a valid active index ---

    @Test
    void seatRejectsAnEmptyHandList() {
        assertThrows(IllegalArgumentException.class,
            () -> new BlackjackFrame.Seat(UUID.randomUUID(), 9, List.of(), 0, false, false));
    }

    @Test
    void seatRejectsAnOutOfRangeActiveHandIndex() {
        List<BlackjackFrame.HandSnapshot> hands = List.of(handSnapshot(List.of(ACE_SPADES), 10, false));
        assertThrows(IllegalArgumentException.class,
            () -> new BlackjackFrame.Seat(UUID.randomUUID(), 9, hands, 1, false, false));
        assertThrows(IllegalArgumentException.class,
            () -> new BlackjackFrame.Seat(UUID.randomUUID(), 9, hands, -1, false, false));
    }

    // --- multi-hand seat: only the active hand is exposed via the convenience getters ---

    @Test
    void seatConvenienceGettersReflectOnlyTheActiveHand() {
        BlackjackFrame.HandSnapshot first = new BlackjackFrame.HandSnapshot(List.of(ACE_SPADES, TEN_CLUBS), 10, true, false);
        BlackjackFrame.HandSnapshot second = new BlackjackFrame.HandSnapshot(List.of(KING_HEARTS), 10, false, true);
        BlackjackFrame.Seat seat = new BlackjackFrame.Seat(UUID.randomUUID(), 9, List.of(first, second), 1, true, true);

        assertEquals(2, seat.getHands().size());
        assertEquals(1, seat.getActiveHandIndex());
        assertEquals(second.getCards(), seat.getHand());
        assertEquals(second.getWager(), seat.getWager());
        assertFalse(seat.isDone());
    }

    // --- equal frames producing equal render-relevant state ---

    @Test
    void framesWithIdenticalStateAreEqual() {
        // Reuses the exact same Card/hand instances in both frames --
        // Card has no value equality, so this is what "identical state"
        // has to mean for a frame built from real dealt cards.
        UUID player = UUID.randomUUID();
        List<Card> dealerHand = List.of(KING_HEARTS);
        List<Card> playerHand = List.of(ACE_SPADES);

        BlackjackFrame a = new BlackjackFrame(
            BlackjackFrame.Phase.ACTIVE, 0, "blackjack.current-player-turn", List.of("player", "Steve"),
            dealerHand, true,
            List.of(seat(player, 9, 20, playerHand, false, true))
        );
        BlackjackFrame b = new BlackjackFrame(
            BlackjackFrame.Phase.ACTIVE, 0, "blackjack.current-player-turn", List.of("player", "Steve"),
            dealerHand, true,
            List.of(seat(player, 9, 20, playerHand, false, true))
        );

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void framesDifferingOnlyInDealerHoleCardVisibilityAreNotEqual() {
        List<Card> dealerHand = List.of(KING_HEARTS);
        BlackjackFrame hidden = new BlackjackFrame(BlackjackFrame.Phase.ACTIVE, 0, "k", BlackjackFrame.noPlaceholders(), dealerHand, true, List.of());
        BlackjackFrame revealed = new BlackjackFrame(BlackjackFrame.Phase.ACTIVE, 0, "k", BlackjackFrame.noPlaceholders(), dealerHand, false, List.of());

        assertNotEquals(hidden, revealed);
    }

    // --- hidden-card visibility is explicit canonical state, not derived from hand size ---

    @Test
    void hiddenCardVisibilityIsIndependentOfDealerHandSize() {
        // The dealer's first card can already be on the table (hand size 1)
        // before the scheduled hidden-placeholder render callback has run
        // -- dealerHoleCardHidden must be able to express "not yet visible"
        // in that window, which a derived "size < 2" boolean cannot: it
        // would report hidden=true even though nothing has been rendered.
        List<Card> oneCardDealt = List.of(KING_HEARTS);

        BlackjackFrame beforePlaceholderRendered = new BlackjackFrame(
            BlackjackFrame.Phase.ACTIVE, 0, "k", BlackjackFrame.noPlaceholders(), oneCardDealt, false, List.of()
        );
        BlackjackFrame afterPlaceholderRendered = new BlackjackFrame(
            BlackjackFrame.Phase.ACTIVE, 0, "k", BlackjackFrame.noPlaceholders(), oneCardDealt, true, List.of()
        );

        assertFalse(beforePlaceholderRendered.dealerHoleCardHidden());
        assertTrue(afterPlaceholderRendered.dealerHoleCardHidden());
        // Same dealer hand in both -- the two frames are legitimately
        // distinguishable only by the explicit flag.
        assertEquals(beforePlaceholderRendered.dealerHand(), afterPlaceholderRendered.dealerHand());
        assertNotEquals(beforePlaceholderRendered, afterPlaceholderRendered);
    }

    // --- dealer-head-slot is canonical, phase-independent state (position-as-state) ---

    @Test
    void dealerHeadSlotDefaultsToTheLobbyPositionWhenNotSpecified() {
        BlackjackFrame frame = new BlackjackFrame(
            BlackjackFrame.Phase.LOBBY, 0, "blackjack.game-info", BlackjackFrame.noPlaceholders(), List.of(), true, List.of()
        );
        assertEquals(BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, frame.dealerHeadSlot());
    }

    @Test
    void dealerHeadSlotCanBeSetToTheInPlayPositionForALateViewer() {
        BlackjackFrame frame = new BlackjackFrame(
            BlackjackFrame.Phase.ACTIVE, 0, "blackjack.game-info", BlackjackFrame.noPlaceholders(),
            List.of(), true, BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, List.of()
        );
        assertEquals(BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, frame.dealerHeadSlot());
    }

    @Test
    void framesDifferingOnlyInDealerHeadSlotAreNotEqual() {
        BlackjackFrame lobby = new BlackjackFrame(
            BlackjackFrame.Phase.START_TRANSITION, 0, "k", BlackjackFrame.noPlaceholders(),
            List.of(), true, BlackjackSlotLayout.DEALER_LOBBY_HEAD_SLOT, List.of()
        );
        BlackjackFrame inPlay = new BlackjackFrame(
            BlackjackFrame.Phase.START_TRANSITION, 0, "k", BlackjackFrame.noPlaceholders(),
            List.of(), true, BlackjackSlotLayout.DEALER_INPLAY_HEAD_SLOT, List.of()
        );
        assertNotEquals(lobby, inPlay);
    }

    // --- new phases exist for the start-transition/insurance flow ---

    @Test
    void phaseEnumIncludesStartTransitionAndInsurance() {
        assertEquals(
            List.of("LOBBY", "COUNTDOWN", "START_TRANSITION", "ACTIVE", "INSURANCE"),
            List.of(BlackjackFrame.Phase.values()).stream().map(Enum::name).toList()
        );
    }

    // --- current-turn is the sole, explicit source of glow/action eligibility ---

    @Test
    void currentTurnIsExplicitAndIndependentOfDone() {
        // A hand that hit to exactly 21 is marked done, but the turn only
        // moves to the next seat once startNextPlayerTurn runs -- until
        // then, currentTurn can still legitimately be true even though the
        // hand is finished. Nothing derives one field from the other.
        BlackjackFrame.Seat doneButStillCurrent = seat(UUID.randomUUID(), 9, 50, List.of(ACE_SPADES, TEN_CLUBS), true, true);

        assertTrue(doneButStillCurrent.isDone());
        assertTrue(doneButStillCurrent.isCurrentTurn());
        assertFalse(doneButStillCurrent.isAwaitingTurn());
    }

    @Test
    void cardGlowStaysOnWhileAnActionIsProcessingEvenThoughButtonsHide() {
        // While a hit/double-down is mid-flight (the card has been dealt
        // but its evaluation hasn't landed yet), the seat is still the
        // table's current turn -- glow must not flicker off -- but its
        // action buttons must not render, since a duplicate click must be
        // blocked until the pending action resolves.
        BlackjackFrame.Seat processing = seat(UUID.randomUUID(), 9, 50, List.of(ACE_SPADES, TEN_CLUBS), false, true, false);

        assertTrue(processing.isCurrentTurn(), "cards must keep glowing while an action is processing");
        assertFalse(processing.isActionable(), "buttons must hide while an action is processing");
    }

    @Test
    void actionableIsNeverTrueForASeatThatIsNotTheCurrentTurn() {
        BlackjackFrame.Seat notCurrent = seat(UUID.randomUUID(), 9, 50, List.of(), false, false, false);
        assertFalse(notCurrent.isCurrentTurn());
        assertFalse(notCurrent.isActionable());
    }

    @Test
    void awaitingTurnIsNeitherDoneNorCurrent() {
        BlackjackFrame.Seat waiting = seat(UUID.randomUUID(), 18, 20, List.of(), false, false);
        assertTrue(waiting.isAwaitingTurn());

        BlackjackFrame.Seat current = seat(UUID.randomUUID(), 18, 20, List.of(), false, true);
        assertFalse(current.isAwaitingTurn());

        BlackjackFrame.Seat finished = seat(UUID.randomUUID(), 18, 20, List.of(), true, false);
        assertFalse(finished.isAwaitingTurn());
    }

    // --- complete five-seat layout for a fresh/late view ---

    @Test
    void seatAtReturnsTheOccupyingSeatOrNullForAnEmptySeat() {
        UUID player = UUID.randomUUID();
        int occupiedSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
        int emptySeat = BlackjackSlotLayout.SEAT_SLOTS[1];

        BlackjackFrame frame = new BlackjackFrame(
            BlackjackFrame.Phase.LOBBY, 0, "blackjack.game-info", BlackjackFrame.noPlaceholders(),
            List.of(), true,
            List.of(seat(player, occupiedSeat, 0, List.of(), false, false))
        );

        assertEquals(player, frame.seatAt(occupiedSeat).getPlayerId());
        assertNull(frame.seatAt(emptySeat));
        assertNull(frame.seatAt(BlackjackSlotLayout.SEAT_SLOTS[2]));
        assertNull(frame.seatAt(BlackjackSlotLayout.SEAT_SLOTS[3]));
        assertNull(frame.seatAt(BlackjackSlotLayout.SEAT_SLOTS[4]));
    }

    @Test
    void allFiveSeatSlotsAreResolvableRegardlessOfHowManySeatsAreOccupied() {
        // Empty-table lobby: seatAt must return null for all five seats,
        // not throw or skip any -- this is exactly what lets bootstrapView
        // paint all five head/bet-spot pairs unconditionally.
        BlackjackFrame emptyLobby = new BlackjackFrame(
            BlackjackFrame.Phase.LOBBY, 0, "blackjack.game-info", BlackjackFrame.noPlaceholders(),
            List.of(), true, List.of()
        );
        for (int seat : BlackjackSlotLayout.SEAT_SLOTS) {
            assertNull(emptyLobby.seatAt(seat));
        }

        // Fully seated table (all five).
        Map<Integer, UUID> occupants = new java.util.LinkedHashMap<>();
        List<BlackjackFrame.Seat> seats = new java.util.ArrayList<>();
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            UUID player = UUID.randomUUID();
            occupants.put(seatSlot, player);
            seats.add(seat(player, seatSlot, 10, List.of(), false, false));
        }
        BlackjackFrame fullTable = new BlackjackFrame(
            BlackjackFrame.Phase.LOBBY, 0, "blackjack.game-info", BlackjackFrame.noPlaceholders(),
            List.of(), true, seats
        );
        assertEquals(5, fullTable.seats().size());
        for (int seatSlot : BlackjackSlotLayout.SEAT_SLOTS) {
            assertEquals(occupants.get(seatSlot), fullTable.seatAt(seatSlot).getPlayerId());
        }
    }

    // --- late-view catch-up: a frame captured mid-round renders the same
    // structure for any newly-opened viewer, differing only in resolved text ---

    @Test
    void lateViewCatchUpProducesIdenticalStructureAcrossLocalesDifferingOnlyInText() {
        UUID player = UUID.randomUUID();
        Card ace = new Card(Suit.CLUBS, Rank.ACE);
        Card nine = new Card(Suit.DIAMONDS, Rank.NINE);
        Card ten = new Card(Suit.SPADES, Rank.TEN);
        Card seven = new Card(Suit.SPADES, Rank.SEVEN);
        List<Card> dealerHand = List.of(ace, nine);
        BlackjackFrame midRound = new BlackjackFrame(
            BlackjackFrame.Phase.ACTIVE, 0, "blackjack.current-player-turn", List.of("player", "Alex"),
            dealerHand, false,
            List.of(seat(player, 18, 40, List.of(ten, seven), false, true))
        );

        // Simulate two different viewers opening a late view onto the same
        // captured frame: same cards/wager/turn structure, but resolved
        // through different "locales" (fake resolvers standing in for
        // plugin.getLocalization()).
        Map<String, String> english = Map.of(
            "cards.ranks.ace", "Ace", "cards.ranks.nine", "Nine", "cards.ranks.ten", "Ten", "cards.ranks.seven", "Seven",
            "cards.suits.clubs", "Clubs", "cards.suits.diamonds", "Diamonds", "cards.suits.spades", "Spades"
        );
        Map<String, String> spanish = Map.of(
            "cards.ranks.ace", "As", "cards.ranks.nine", "Nueve", "cards.ranks.ten", "Diez", "cards.ranks.seven", "Siete",
            "cards.suits.clubs", "Treboles", "cards.suits.diamonds", "Diamantes", "cards.suits.spades", "Picas"
        );

        List<String> englishDealerCards = renderCardNames(midRound.dealerHand(), english);
        List<String> spanishDealerCards = renderCardNames(midRound.dealerHand(), spanish);

        assertEquals(List.of("Ace of Clubs", "Nine of Diamonds"), englishDealerCards);
        assertEquals(List.of("As of Treboles", "Nueve of Diamantes"), spanishDealerCards);

        // Same underlying frame, so the same number of cards and the same
        // seat/wager/turn structure regardless of which locale rendered it.
        assertEquals(midRound.dealerHand().size(), englishDealerCards.size());
        assertEquals(midRound.dealerHand().size(), spanishDealerCards.size());
        assertEquals(1, midRound.seats().size());
        assertTrue(midRound.seats().get(0).isCurrentTurn());
        assertEquals(40, midRound.seats().get(0).getWager());
    }

    private static List<String> renderCardNames(List<Card> hand, Map<String, String> resolver) {
        List<String> names = new java.util.ArrayList<>();
        for (Card card : hand) {
            names.add(BlackjackFrame.localizedCardName(
                card,
                (key, args) -> {
                    String rank = (String) args[1];
                    String suit = (String) args[3];
                    return rank + " of " + suit;
                },
                resolver::get
            ));
        }
        return names;
    }

    // --- structural sanity: BlackjackFrame is a plain final immutable value type ---

    @Test
    void frameIsFinalAndHasNoPublicSetters() {
        assertTrue(Modifier.isFinal(BlackjackFrame.class.getModifiers()));
        for (var method : BlackjackFrame.class.getDeclaredMethods()) {
            assertFalse(method.getName().startsWith("set"), "BlackjackFrame must have no setters: " + method);
        }
    }
}
