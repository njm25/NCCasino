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
 * BlackjackInventory actually calls through to render every card and every
 * bet-slot presentation (see BlackjackInventory#localizedCardName,
 * #setBetPresentation, #bootstrapView).
 *
 * Card has no equals/hashCode (see CardTest) -- tests that need two equal
 * frames/hands reuse the same Card instances rather than relying on value
 * equality, per Card's identity semantics.
 */
class BlackjackFrameTest {

    private static final Card ACE_SPADES = new Card(Suit.SPADES, Rank.ACE);
    private static final Card KING_HEARTS = new Card(Suit.HEARTS, Rank.KING);
    private static final Card TEN_CLUBS = new Card(Suit.CLUBS, Rank.TEN);

    private static BlackjackFrame.Seat seat(
        UUID player, int slot, double wager, List<Card> hand, boolean done, boolean currentTurn,
        BlackjackFrame.BetPresentation presentation
    ) {
        return new BlackjackFrame.Seat(player, slot, wager, hand, done, currentTurn, presentation);
    }

    // --- no Bukkit types, no Player references, no localized strings ---

    @Test
    void frameAndSeatDeclareNoBukkitOrPlayerTypes() {
        for (Class<?> type : new Class<?>[] {BlackjackFrame.class, BlackjackFrame.Seat.class}) {
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
        BlackjackFrame.Seat seat = seat(UUID.randomUUID(), 9, 50, mutableHand, false, false, BlackjackFrame.BetPresentation.CLICK_BET);

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

        mutableSeats.add(seat(UUID.randomUUID(), 9, 10, List.of(), false, false, BlackjackFrame.BetPresentation.CLICK_BET));
        dealerHand.add(KING_HEARTS);

        assertEquals(0, frame.seats().size(), "Frame's seat list must not observe later mutation of the source list");
        assertEquals(1, frame.dealerHand().size(), "Frame's dealer hand must not observe later mutation of the source list");
        assertThrows(UnsupportedOperationException.class, () -> frame.seats().add(seat(UUID.randomUUID(), 18, 5, List.of(), false, false, BlackjackFrame.BetPresentation.CLICK_BET)));
        assertThrows(UnsupportedOperationException.class, () -> frame.dealerHand().add(ACE_SPADES));
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
            List.of(seat(player, 9, 20, playerHand, false, true, BlackjackFrame.BetPresentation.YOUR_TURN))
        );
        BlackjackFrame b = new BlackjackFrame(
            BlackjackFrame.Phase.ACTIVE, 0, "blackjack.current-player-turn", List.of("player", "Steve"),
            dealerHand, true,
            List.of(seat(player, 9, 20, playerHand, false, true, BlackjackFrame.BetPresentation.YOUR_TURN))
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

    // --- explicit YOUR_TURN/TURN_OVER/CLICK_BET reconstruction ---

    @Test
    void betSlotRenderForMapsEachPresentationToItsProductionRendering() {
        BlackjackFrame.BetSlotRender yourTurn = BlackjackFrame.betSlotRenderFor(BlackjackFrame.BetPresentation.YOUR_TURN);
        assertTrue(yourTurn.isEnchanted());
        assertEquals("blackjack.your-turn", yourTurn.getKey());

        BlackjackFrame.BetSlotRender turnOver = BlackjackFrame.betSlotRenderFor(BlackjackFrame.BetPresentation.TURN_OVER);
        assertFalse(turnOver.isEnchanted());
        assertEquals("blackjack.turn-over", turnOver.getKey());

        BlackjackFrame.BetSlotRender clickBet = BlackjackFrame.betSlotRenderFor(BlackjackFrame.BetPresentation.CLICK_BET);
        assertFalse(clickBet.isEnchanted());
        assertEquals("blackjack.click-bet", clickBet.getKey());
    }

    @Test
    void seatPresentationIsExplicitAndNotDerivedFromDoneOrCurrentTurn() {
        // Preserved quirk: after a hit lands on exactly 21, the player is
        // marked done and their turn deactivated, but the bet slot stays
        // rendered as YOUR_TURN (an enchanted book) until the *next*
        // startNextPlayerTurn call explicitly relabels it TURN_OVER. A
        // presentation inferred from done/currentTurn could never represent
        // this "done, not current, but still showing YOUR_TURN" state --
        // the explicit field can, and must.
        BlackjackFrame.Seat frozenAtTwentyOne = seat(
            UUID.randomUUID(), 9, 50, List.of(ACE_SPADES, TEN_CLUBS), true, false,
            BlackjackFrame.BetPresentation.YOUR_TURN
        );

        assertTrue(frozenAtTwentyOne.isDone());
        assertFalse(frozenAtTwentyOne.isCurrentTurn());
        assertEquals(BlackjackFrame.BetPresentation.YOUR_TURN, frozenAtTwentyOne.getPresentation());
    }

    // --- complete chair/bet layout for a fresh/late view ---

    @Test
    void seatAtReturnsTheOccupyingSeatOrNullForAnEmptyChair() {
        UUID player = UUID.randomUUID();
        int occupiedChair = BlackjackSlotLayout.CHAIR_SLOTS[0];
        int emptyChair = BlackjackSlotLayout.CHAIR_SLOTS[1];

        BlackjackFrame frame = new BlackjackFrame(
            BlackjackFrame.Phase.LOBBY, 0, "blackjack.game-info", BlackjackFrame.noPlaceholders(),
            List.of(), true,
            List.of(seat(player, occupiedChair, 0, List.of(), false, false, BlackjackFrame.BetPresentation.CLICK_BET))
        );

        assertEquals(player, frame.seatAt(occupiedChair).getPlayerId());
        assertNull(frame.seatAt(emptyChair));
        assertNull(frame.seatAt(BlackjackSlotLayout.CHAIR_SLOTS[2]));
    }

    @Test
    void everyChairSlotIsResolvableRegardlessOfHowManySeatsAreOccupied() {
        // Empty-table lobby: seatAt must return null for all three chairs,
        // not throw or skip any -- this is exactly what lets bootstrapView
        // paint all three chair/bet-paper pairs unconditionally.
        BlackjackFrame emptyLobby = new BlackjackFrame(
            BlackjackFrame.Phase.LOBBY, 0, "blackjack.game-info", BlackjackFrame.noPlaceholders(),
            List.of(), true, List.of()
        );
        for (int chair : BlackjackSlotLayout.CHAIR_SLOTS) {
            assertNull(emptyLobby.seatAt(chair));
        }

        // Fully seated table.
        Map<Integer, UUID> occupants = new java.util.LinkedHashMap<>();
        List<BlackjackFrame.Seat> seats = new java.util.ArrayList<>();
        for (int chair : BlackjackSlotLayout.CHAIR_SLOTS) {
            UUID player = UUID.randomUUID();
            occupants.put(chair, player);
            seats.add(seat(player, chair, 10, List.of(), false, false, BlackjackFrame.BetPresentation.CLICK_BET));
        }
        BlackjackFrame fullTable = new BlackjackFrame(
            BlackjackFrame.Phase.LOBBY, 0, "blackjack.game-info", BlackjackFrame.noPlaceholders(),
            List.of(), true, seats
        );
        for (int chair : BlackjackSlotLayout.CHAIR_SLOTS) {
            assertEquals(occupants.get(chair), fullTable.seatAt(chair).getPlayerId());
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
            List.of(seat(player, 18, 40, List.of(ten, seven), false, true, BlackjackFrame.BetPresentation.YOUR_TURN))
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
        // seat/wager/turn/presentation structure regardless of which locale
        // rendered it.
        assertEquals(midRound.dealerHand().size(), englishDealerCards.size());
        assertEquals(midRound.dealerHand().size(), spanishDealerCards.size());
        assertEquals(1, midRound.seats().size());
        assertTrue(midRound.seats().get(0).isCurrentTurn());
        assertEquals(BlackjackFrame.BetPresentation.YOUR_TURN, midRound.seats().get(0).getPresentation());
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
