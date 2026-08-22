package org.nc.nccasino.games.Blackjack;

import java.util.Map;
import java.util.UUID;

/**
 * Pure ownership/liveness routing decisions for {@code BlackjackMenu}'s
 * chat-driven admin edit flow. {@code BlackjackMenu.onPlayerChat} and
 * {@code isLiveMenuForOwner} genuinely delegate to these (not a parallel
 * simulation) -- kept here, outside the Bukkit-coupled menu class, so they
 * stay directly unit-testable without constructing a real menu/inventory.
 *
 * <p>{@code BlackjackMenu}'s chat listener is registered per-instance (see
 * the {@code Menu} constructor), so it receives <em>every</em> player's
 * chat on the server, not just its own owner's -- two administrators
 * editing different dealers simultaneously each have their own instance,
 * and each instance's own listener fires for the other's chat too. Without
 * {@link #isEligible}, an unrelated player's message (including another
 * administrator's own valid edit input, from a different instance's point
 * of view) could be misrouted into this instance's own dealer/edit state.
 *
 * <p>{@link #isLiveMenu} additionally guards a queued main-thread callback
 * scheduled from the async chat event: by the time it fires, the menu may
 * have been replaced (a newer instance now owns the registration), the
 * edit session cancelled, or the owner may have disconnected -- a stale
 * callback must be a complete no-op rather than mutate state that now
 * belongs to whatever superseded it.
 */
public final class BlackjackMenuChatRouting {

    private BlackjackMenuChatRouting() {
    }

    /**
     * Whether a chat event is even eligible to be routed to a
     * {@code BlackjackMenu} instance owned by {@code ownerId} -- true only
     * when the chatting player <em>is</em> that owner.
     */
    public static boolean isEligible(UUID chattingPlayerId, UUID ownerId) {
        return chattingPlayerId != null && chattingPlayerId.equals(ownerId);
    }

    /**
     * Whether {@code menuInstance} is still the live, registered menu for
     * {@code ownerId} in {@code registeredMenus} (the controller's own
     * {@code BAInventories} map). A stale/superseded instance (or a stale
     * scheduled callback captured from one) must never act once this is
     * false, even against its own owner's chat -- doing so could tear down
     * or edit state that now belongs to a newer, different instance.
     */
    public static <T> boolean isLiveMenu(Map<UUID, T> registeredMenus, UUID ownerId, T menuInstance) {
        return registeredMenus != null && registeredMenus.get(ownerId) == menuInstance;
    }
}
