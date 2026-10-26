package org.nc.nccasino.objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {

    private List<Card> cards; // The list of cards in the deck
    private final int numberOfDecks; // The number of decks this deck contains
    /** Most cards one hand (a player's, with splits and hits, or the dealer's) is assumed to need. */
    static final int CARDS_PER_HAND_ALLOWANCE = 12;
    private boolean stackedForTest;

    // Constructor that initializes the deck with a specific number of decks (default 1)
    public Deck(int numberOfDecks) {
        this.numberOfDecks = numberOfDecks;
        this.cards = new ArrayList<>();
        resetDeck(); // Initialize and shuffle the deck upon creation
    }

    // Add a standard deck of 52 cards
    private void addStandardDeck() {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    // Shuffle the deck
    public void shuffle() {
        Collections.shuffle(cards);
    }

    // Deal a card from the top of the deck
    public Card dealCard() {
        if (cards.isEmpty()) {
            // Automatically reshuffle the deck when it's empty
            resetDeck(); // Reshuffle the deck
        }
        return cards.remove(0);
    }

    // Get the number of cards remaining in the deck
    public int remainingCards() {
        return cards.size();
    }

    // Check if the deck has any cards left
    public boolean hasCards() {
        return !cards.isEmpty();
    }

    /**
     * Whether the shoe has reached its cut card and must be reshuffled before
     * the next round is dealt: below a quarter of the shoe, or too few cards
     * left for {@code handsToDeal} hands. Dealing to the last card let players
     * count the end of the shoe, and a shoe that ran dry mid-round refunded the
     * round. A deck a test stacked keeps its exact order.
     */
    public boolean shouldReshuffleBeforeRound(int handsToDeal) {
        if (stackedForTest) {
            return false;
        }
        int shoe = numberOfDecks * 52;
        int cut = Math.min(shoe, Math.max(shoe / 4, handsToDeal * CARDS_PER_HAND_ALLOWANCE));
        return cards.size() < cut;
    }

    // Reset the deck with the initial number of decks
    public void resetDeck() {
        stackedForTest = false;
        cards.clear();
        for (int i = 0; i < numberOfDecks; i++) {
            addStandardDeck();
        }
        shuffle();
    }

    // Optionally reset the deck with a new number of decks
    public void resetDeck(int newNumberOfDecks) {
        this.cards.clear();
        for (int i = 0; i < newNumberOfDecks; i++) {
            addStandardDeck();
        }
        shuffle();
    }

    /**
     * Test-only: replaces the live card list with an exact, unshuffled
     * sequence so integration tests can drive deterministic hands (no
     * accidental natural blackjack/insurance/bust) instead of depending on
     * {@link #shuffle}'s randomness. {@code cardsInDealOrder.get(0)} is the
     * next card {@link #dealCard()} returns. Never called from production
     * code.
     */
    public void stackForTest(List<Card> cardsInDealOrder) {
        this.stackedForTest = true;
        this.cards = new ArrayList<>(cardsInDealOrder);
    }
}
