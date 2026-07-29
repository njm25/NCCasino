package org.nc.nccasino.session;

import java.util.UUID;

/**
 * Implemented by whatever object currently owns a player's active
 * wager-bearing game state (a {@code Client}, a per-player table, etc.) so
 * {@link SessionRegistry} can delegate wager resolution and seat release
 * back to the right game without knowing that game's internals.
 *
 * <p>This is strictly about resolving the session's outcome (refund,
 * forfeit, cash-out, deferred payout) and releasing shared state (seat,
 * maps). UI teardown — unregistering listeners, closing inventories — is a
 * separate concern each game already owns and may perform independently of
 * this callback.
 */
public interface TerminableSession {

    /**
     * Resolve and release this player's session for the given reason.
     * {@link SessionRegistry} guarantees this is invoked at most once per
     * {@link SessionRegistry#register} call, but implementations should
     * still guard their own state changes defensively rather than assume
     * this is the only possible way their session ends, since a normal
     * game-completion path can run concurrently with disconnect handling.
     */
    void onSessionTerminated(UUID playerId, ExitReason reason);
}
