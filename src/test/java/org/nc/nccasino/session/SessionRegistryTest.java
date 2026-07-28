package org.nc.nccasino.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRegistryTest {

    @Test
    void terminationIsExactlyOnceAndPassesThroughReason() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ExitReason> received = new AtomicReference<>();
        SessionRegistry.register(playerId, (id, reason) -> {
            assertEquals(playerId, id);
            calls.incrementAndGet();
            received.set(reason);
        });

        SessionRegistry.terminatePlayerSession(playerId, ExitReason.DISCONNECTED);
        SessionRegistry.terminatePlayerSession(playerId, ExitReason.KICKED);

        assertEquals(1, calls.get());
        assertEquals(ExitReason.DISCONNECTED, received.get());
        assertFalse(SessionRegistry.hasActiveSession(playerId));
    }

    @Test
    void staleUnregisterCannotRemoveReplacementSession() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger replacementCalls = new AtomicInteger();
        TerminableSession stale = (id, reason) -> { };
        TerminableSession replacement = (id, reason) -> replacementCalls.incrementAndGet();

        SessionRegistry.register(playerId, stale);
        SessionRegistry.register(playerId, replacement);
        SessionRegistry.unregister(playerId, stale);

        assertTrue(SessionRegistry.hasActiveSession(playerId));
        SessionRegistry.terminatePlayerSession(playerId, ExitReason.GAME_COMPLETED);
        assertEquals(1, replacementCalls.get());
    }

    @Test
    void matchingUnregisterClearsSessionWithoutResolvingIt() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        TerminableSession session = (id, reason) -> calls.incrementAndGet();
        SessionRegistry.register(playerId, session);

        SessionRegistry.unregister(playerId, session);
        SessionRegistry.terminatePlayerSession(playerId, ExitReason.DISCONNECTED);

        assertEquals(0, calls.get());
        assertFalse(SessionRegistry.hasActiveSession(playerId));
    }

    @Test
    void kickMarkerIsConsumedOnlyOnce() {
        UUID playerId = UUID.randomUUID();
        SessionRegistry.markKicked(playerId);

        assertEquals(ExitReason.KICKED, SessionRegistry.consumeQuitReason(playerId));
        assertEquals(ExitReason.DISCONNECTED, SessionRegistry.consumeQuitReason(playerId));
    }

    @Test
    void clearingKickMarkerPreventsLaterMisclassification() {
        UUID playerId = UUID.randomUUID();
        SessionRegistry.markKicked(playerId);
        SessionRegistry.clearKickMarker(playerId);
        assertEquals(ExitReason.DISCONNECTED, SessionRegistry.consumeQuitReason(playerId));
    }

    @Test
    void terminateAllResolvesEveryRegisteredPlayer() {
        AtomicInteger calls = new AtomicInteger();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SessionRegistry.register(first, (id, reason) -> {
            assertEquals(ExitReason.PLUGIN_DISABLE, reason);
            calls.incrementAndGet();
        });
        SessionRegistry.register(second, (id, reason) -> {
            assertEquals(ExitReason.PLUGIN_DISABLE, reason);
            calls.incrementAndGet();
        });

        SessionRegistry.terminateAll(ExitReason.PLUGIN_DISABLE);

        assertEquals(2, calls.get());
        assertFalse(SessionRegistry.hasActiveSession(first));
        assertFalse(SessionRegistry.hasActiveSession(second));
    }
}
