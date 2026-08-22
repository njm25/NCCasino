package org.nc.nccasino.games.Blackjack;

import java.util.Collection;
import java.util.List;

/**
 * Pure, locale-neutral decision for whether the dealer's own turn can be
 * skipped entirely: only when <em>every</em> wagered {@link BlackjackHand}
 * across <em>every</em> still-seated player is busted. Extracted so this
 * doesn't collapse to "the player's single active hand busted" once a
 * player can hold several simultaneously-resolved hands (splitting) --
 * e.g. a player who stood on 20 on their first split hand and busted on
 * their second must still have their first hand settled against the
 * dealer's own play, never short-circuited as if the whole player busted.
 */
public final class BlackjackTableBustCheck {

    private BlackjackTableBustCheck() {
    }

    /**
     * @param allSeatedPlayersHands every still-seated player's own hand queue (original plus any splits); a null or empty collection, or a player with no hands, contributes nothing
     * @return true only if there is no unresolved-and-not-busted hand anywhere at the table (a stood/natural/21 hand keeps this false, correctly requiring the dealer to still play)
     */
    public static boolean allHandsBusted(Collection<List<BlackjackHand>> allSeatedPlayersHands) {
        if (allSeatedPlayersHands == null) {
            return true;
        }
        for (List<BlackjackHand> hands : allSeatedPlayersHands) {
            if (hands == null) {
                continue;
            }
            for (BlackjackHand hand : hands) {
                if (hand.getWager() <= 0) {
                    continue; // never actually dealt into this round
                }
                if (!BlackjackRules.isBust(hand.getCards())) {
                    return false;
                }
            }
        }
        return true;
    }
}
