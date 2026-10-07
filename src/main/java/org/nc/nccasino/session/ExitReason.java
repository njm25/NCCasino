package org.nc.nccasino.session;

/**
 * Why a player's active game session is being torn down. Drives per-game
 * wager policy in {@link TerminableSession#onSessionTerminated}.
 */
public enum ExitReason {
    /**
     * Player disconnected: voluntary quit, client crash, Alt+F4, Wi-Fi
     * loss, or a Geyser/Bedrock drop -- plus the server-originated kicks
     * that are not the player's fault (keep-alive timeout, AFK kick,
     * duplicate login, restart), which {@link KickClassifier} folds in here.
     */
    DISCONNECTED,

    /**
     * Player was removed by a punitive (non-cancelled) kick -- a command,
     * ban, or moderation/anti-cheat plugin. Never entitled to a refund,
     * deferred payout, or cash-out, regardless of game phase.
     */
    KICKED,

    /**
     * Player deliberately exited the game UI (e.g. clicked "Exit") while
     * still connected. Distinct from simply navigating between NCCasino
     * menus, which never reaches session termination at all.
     */
    VOLUNTARY_INVENTORY_CLOSE,

    /**
     * The round/game finished normally; the session is being released as
     * part of ordinary completion, not because the player left.
     */
    GAME_COMPLETED,

    /**
     * The plugin is disabling (server shutdown, external reload) while
     * the session was still active.
     */
    PLUGIN_DISABLE
}
