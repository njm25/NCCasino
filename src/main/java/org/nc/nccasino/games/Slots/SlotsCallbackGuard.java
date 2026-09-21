package org.nc.nccasino.games.Slots;

import java.util.UUID;

/**
 * Every scheduled animation/settlement callback captures a
 * {@link SpinToken} at schedule time and must re-validate it against the
 * session's live identity and current generation before doing anything. A
 * stale callback -- superseded generation, or a session that has since
 * rebound to a different player/dealer -- must be a total no-op.
 */
public final class SlotsCallbackGuard {

    private SlotsCallbackGuard() {
    }

    public record SpinToken(UUID playerId, UUID dealerId, long generation) {
    }

    public static boolean isValid(SpinToken token, UUID expectedPlayerId, UUID expectedDealerId, long currentGeneration) {
        if (token == null || expectedPlayerId == null || expectedDealerId == null) {
            return false;
        }
        return token.playerId().equals(expectedPlayerId)
            && token.dealerId().equals(expectedDealerId)
            && token.generation() == currentGeneration;
    }
}
