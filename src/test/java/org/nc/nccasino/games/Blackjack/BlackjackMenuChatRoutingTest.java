package org.nc.nccasino.games.Blackjack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * BlackjackMenu.onPlayerChat and isLiveMenuForOwner genuinely delegate to
 * {@link BlackjackMenuChatRouting#isEligible}/{@link BlackjackMenuChatRouting#isLiveMenu}
 * -- this is the regression coverage for the audited defect: every
 * BlackjackMenu listener instance receives every player's
 * AsyncPlayerChatEvent, so an unrelated player's chat (or another
 * administrator's own valid input, from a different instance's point of
 * view) could be misrouted into a menu instance that isn't theirs.
 */
class BlackjackMenuChatRoutingTest {

    // A generic stand-in for a BlackjackMenu instance identity -- the real
    // production code passes `this`; any distinct object reference serves
    // the same purpose here since only identity (==) matters.
    private static final class FakeMenuInstance {
    }

    @Test
    void ownersChatIsEligible() {
        UUID owner = UUID.randomUUID();
        assertTrue(BlackjackMenuChatRouting.isEligible(owner, owner));
    }

    @Test
    void unrelatedPlayersChatIsNeverEligible() {
        // The exact audited scenario: an administrator is editing a
        // dealer's settings, and a completely unrelated player sends any
        // ordinary chat message -- it must have zero effect on this menu.
        UUID owner = UUID.randomUUID();
        UUID unrelatedPlayer = UUID.randomUUID();
        assertFalse(BlackjackMenuChatRouting.isEligible(unrelatedPlayer, owner));
    }

    @Test
    void twoAdministratorsEditingDifferentDealersDoNotCrossRoute() {
        // Each administrator's own BlackjackMenu instance must only ever
        // consider their own chat -- admin A's chat is ineligible for
        // admin B's instance and vice versa.
        UUID adminA = UUID.randomUUID();
        UUID adminB = UUID.randomUUID();

        assertTrue(BlackjackMenuChatRouting.isEligible(adminA, adminA));
        assertFalse(BlackjackMenuChatRouting.isEligible(adminB, adminA));

        assertTrue(BlackjackMenuChatRouting.isEligible(adminB, adminB));
        assertFalse(BlackjackMenuChatRouting.isEligible(adminA, adminB));
    }

    @Test
    void nullChattingPlayerIsNeverEligible() {
        assertFalse(BlackjackMenuChatRouting.isEligible(null, UUID.randomUUID()));
    }

    // --- isLiveMenu: menu replacement / disconnect / stale-callback scenarios ---

    @Test
    void currentlyRegisteredInstanceIsLive() {
        UUID owner = UUID.randomUUID();
        FakeMenuInstance instance = new FakeMenuInstance();
        Map<UUID, FakeMenuInstance> registered = new HashMap<>();
        registered.put(owner, instance);

        assertTrue(BlackjackMenuChatRouting.isLiveMenu(registered, owner, instance));
    }

    @Test
    void menuReplacedBetweenAsyncEventAndScheduledCallbackIsNotLive() {
        // The original instance scheduled a main-thread callback; before it
        // fires, the owner somehow ends up with a *different* instance
        // registered (e.g. reopened the settings menu) -- the stale
        // instance's own callback must see itself as no longer live.
        UUID owner = UUID.randomUUID();
        FakeMenuInstance original = new FakeMenuInstance();
        FakeMenuInstance replacement = new FakeMenuInstance();
        Map<UUID, FakeMenuInstance> registered = new HashMap<>();
        registered.put(owner, original);

        assertTrue(BlackjackMenuChatRouting.isLiveMenu(registered, owner, original));

        registered.put(owner, replacement); // menu replaced

        assertFalse(BlackjackMenuChatRouting.isLiveMenu(registered, owner, original), "the stale (original) instance must no longer be live");
        assertTrue(BlackjackMenuChatRouting.isLiveMenu(registered, owner, replacement), "the new instance is the live one");
    }

    @Test
    void ownerDisconnectsBeforeCallbackFiresIsNotLive() {
        // Disconnect cleanup removes the owner's entry entirely.
        UUID owner = UUID.randomUUID();
        FakeMenuInstance instance = new FakeMenuInstance();
        Map<UUID, FakeMenuInstance> registered = new HashMap<>();
        registered.put(owner, instance);

        registered.remove(owner); // disconnect cleanup

        assertFalse(BlackjackMenuChatRouting.isLiveMenu(registered, owner, instance));
    }

    @Test
    void staleCallbackCannotActOnANewMenuForTheSameOwner() {
        // Combines both guards: even if the chatting player IS the owner,
        // a stale callback captured against the *old* instance identity
        // must not be treated as live once a new instance has taken over.
        UUID owner = UUID.randomUUID();
        FakeMenuInstance oldInstance = new FakeMenuInstance();
        FakeMenuInstance newInstance = new FakeMenuInstance();
        Map<UUID, FakeMenuInstance> registered = new HashMap<>();
        registered.put(owner, newInstance);

        assertTrue(BlackjackMenuChatRouting.isEligible(owner, owner));
        assertFalse(BlackjackMenuChatRouting.isLiveMenu(registered, owner, oldInstance), "the stale instance's own callback must no-op");
        assertTrue(BlackjackMenuChatRouting.isLiveMenu(registered, owner, newInstance));
    }

    @Test
    void nullRegisteredMenusMapNeverThrows() {
        assertFalse(BlackjackMenuChatRouting.isLiveMenu(null, UUID.randomUUID(), new FakeMenuInstance()));
    }

    @Test
    void emptyRegistryMeansNoInstanceIsLive() {
        Map<UUID, FakeMenuInstance> registered = new HashMap<>();
        assertFalse(BlackjackMenuChatRouting.isLiveMenu(registered, UUID.randomUUID(), new FakeMenuInstance()));
    }
}
