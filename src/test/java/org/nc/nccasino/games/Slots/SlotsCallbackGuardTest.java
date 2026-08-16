package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotsCallbackGuardTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID DEALER_ID = UUID.randomUUID();

    @Test
    void matchingIdentityAndGenerationIsValid() {
        SlotsCallbackGuard.SpinToken token = new SlotsCallbackGuard.SpinToken(PLAYER_ID, DEALER_ID, 3L);
        assertTrue(SlotsCallbackGuard.isValid(token, PLAYER_ID, DEALER_ID, 3L));
    }

    @Test
    void supersededGenerationIsStale() {
        SlotsCallbackGuard.SpinToken token = new SlotsCallbackGuard.SpinToken(PLAYER_ID, DEALER_ID, 1L);
        assertFalse(SlotsCallbackGuard.isValid(token, PLAYER_ID, DEALER_ID, 2L));
    }

    @Test
    void wrongPlayerIsStale() {
        SlotsCallbackGuard.SpinToken token = new SlotsCallbackGuard.SpinToken(PLAYER_ID, DEALER_ID, 1L);
        assertFalse(SlotsCallbackGuard.isValid(token, UUID.randomUUID(), DEALER_ID, 1L));
    }

    @Test
    void wrongDealerIsStale() {
        SlotsCallbackGuard.SpinToken token = new SlotsCallbackGuard.SpinToken(PLAYER_ID, DEALER_ID, 1L);
        assertFalse(SlotsCallbackGuard.isValid(token, PLAYER_ID, UUID.randomUUID(), 1L));
    }

    @Test
    void nullTokenIsAlwaysStale() {
        assertFalse(SlotsCallbackGuard.isValid(null, PLAYER_ID, DEALER_ID, 1L));
    }

    @Test
    void nullExpectedIdentityIsNeverValid() {
        SlotsCallbackGuard.SpinToken token = new SlotsCallbackGuard.SpinToken(PLAYER_ID, DEALER_ID, 1L);
        assertFalse(SlotsCallbackGuard.isValid(token, null, DEALER_ID, 1L));
        assertFalse(SlotsCallbackGuard.isValid(token, PLAYER_ID, null, 1L));
    }
}
