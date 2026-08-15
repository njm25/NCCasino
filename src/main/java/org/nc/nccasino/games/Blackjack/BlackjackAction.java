package org.nc.nccasino.games.Blackjack;

/**
 * A dynamic action a player can take during their turn. Ordinal order is
 * the canonical display order (Hit, Stand, Double Down) that
 * {@link BlackjackActionLayout} places into the action row.
 */
public enum BlackjackAction {
    HIT,
    STAND,
    DOUBLE_DOWN
}
