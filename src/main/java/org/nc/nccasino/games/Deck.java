package org.nc.nccasino.games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {

    private List<Card> cards; // The list of cards in the deck
    private final int numberOfDecks; // The number of decks this deck contains

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

    // Reset the deck with the initial number of decks
    public void resetDeck() {
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
}
