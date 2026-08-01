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
    void unregisteringOneSessionPreservesOtherConcurrentSession() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        TerminableSession first = (id, reason) -> firstCalls.incrementAndGet();
        TerminableSession second = (id, reason) -> secondCalls.incrementAndGet();

        SessionRegistry.register(playerId, first);
        SessionRegistry.register(playerId, second);
        SessionRegistry.unregister(playerId, first);

        assertTrue(SessionRegistry.hasActiveSession(playerId));
        SessionRegistry.terminatePlayerSession(playerId, ExitReason.GAME_COMPLETED);
        assertEquals(0, firstCalls.get());
        assertEquals(1, secondCalls.get());
    }

    @Test
    void terminationResolvesEveryConcurrentSessionExactlyOnce() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        TerminableSession first = (id, reason) -> calls.incrementAndGet();
        TerminableSession second = (id, reason) -> calls.incrementAndGet();

        SessionRegistry.register(playerId, first);
        SessionRegistry.register(playerId, second);
        SessionRegistry.register(playerId, first);

        SessionRegistry.terminatePlayerSession(playerId, ExitReason.PLUGIN_DISABLE);
        SessionRegistry.terminatePlayerSession(playerId, ExitReason.PLUGIN_DISABLE);

        assertEquals(2, calls.get());
        assertFalse(SessionRegistry.hasActiveSession(playerId));
    }

    @Test
    void targetedTerminationLeavesOtherConcurrentSessionRegistered() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        TerminableSession first = (id, reason) -> firstCalls.incrementAndGet();
        TerminableSession second = (id, reason) -> secondCalls.incrementAndGet();

        SessionRegistry.register(playerId, first);
        SessionRegistry.register(playerId, second);
        SessionRegistry.terminateSession(playerId, first, ExitReason.DISCONNECTED);

        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get());
        assertTrue(SessionRegistry.hasActiveSession(playerId));

        SessionRegistry.terminatePlayerSession(playerId, ExitReason.PLUGIN_DISABLE);
        assertEquals(1, secondCalls.get());
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
