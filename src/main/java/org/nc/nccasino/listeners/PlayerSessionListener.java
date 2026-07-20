package org.nc.nccasino.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminMenu;
import org.nc.nccasino.components.BaccaratMenu;
import org.nc.nccasino.components.BlackjackMenu;
import org.nc.nccasino.components.CoinFlipMenu;
import org.nc.nccasino.components.DragonDescentMenu;
import org.nc.nccasino.components.MinesMenu;
import org.nc.nccasino.components.RouletteMenu;
import org.nc.nccasino.payout.DeliveryResult;
import org.nc.nccasino.payout.PayoutMessages;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.payout.PendingPayoutStore;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.SessionRegistry;

import java.util.UUID;

/**
 * The single source of "this player is gone" for gameplay purposes.
 *
 * <p>{@code PlayerQuitEvent} is the primary, guaranteed-to-fire disconnect
 * signal — NCCasino cannot and does not try to distinguish voluntary
 * disconnect, client crash, Wi-Fi loss, timeout, or a Geyser/Bedrock drop;
 * all of them surface here identically as {@link ExitReason#DISCONNECTED}.
 *
 * <p>{@code PlayerKickEvent} is observed only (at {@link EventPriority#MONITOR}, so
 * it sees the final cancellation state after every other plugin has had a
 * chance to cancel it) to classify the quit that follows it as
 * {@link ExitReason#KICKED} instead — it does not run a separate cleanup
 * path of its own.
 *
 * <p>Every quit or kick also unconditionally releases any admin edit-mode
 * lock and stale intro-animation tracking for that player, regardless of
 * {@link ExitReason} — this is a UI-lock release, not wager compensation,
 * so it must not be gated the way payout policy is.
 *
 * <p>{@code PlayerJoinEvent} settles whatever pending payouts/results
 * accumulated while the player was away. It deliberately does not attempt
 * to resume an interrupted game or repair stale cached session objects —
 * the latter is a separate, later concern (stale-client handling).
 */
public class PlayerSessionListener implements Listener {

    private final Nccasino plugin;

    public PlayerSessionListener(Nccasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        if (event.isCancelled()) {
            // Player stays connected; nothing to classify.
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        SessionRegistry.markKicked(playerId);

        // Safety net: a non-cancelled kick is always followed by a quit in
        // the same disconnect sequence, but guard against the marker
        // outliving that window and misclassifying a later, unrelated
        // disconnect as a kick — which would wrongly deny that player a
        // refund they are entitled to.
        Bukkit.getScheduler().runTaskLater(plugin,
            () -> SessionRegistry.clearKickMarker(playerId), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        ExitReason reason = SessionRegistry.consumeQuitReason(playerId);
        SessionRegistry.terminatePlayerSession(playerId, reason);

        // Admin edit-mode/occupation cleanup runs unconditionally, on every
        // quit or kick alike: it's about releasing a UI lock, not wager
        // compensation, so it must not depend on ExitReason.
        clearAdminAndInteractionState(event.getPlayer(), playerId);
    }

    /**
     * Central cleanup for the admin edit-mode/settings-menu stale-lock bug:
     * a player who starts editing something (e.g. clicks "Edit Timer",
     * which closes the menu and waits for a chat reply) and then
     * disconnects before finishing was previously locked out of every
     * NCCasino dealer forever, since nothing but successful chat
     * submission or an open inventory closing ever cleared these maps.
     * Also clears stale intro-animation tracking for the same reason.
     */
    private void clearAdminAndInteractionState(Player player, UUID playerId) {
        AdminMenu.clearPlayerEditState(playerId);
        BlackjackMenu.clearPlayerState(playerId);
        RouletteMenu.clearPlayerState(playerId);
        MinesMenu.clearPlayerState(playerId);
        BaccaratMenu.clearPlayerState(playerId);
        CoinFlipMenu.clearPlayerState(playerId);
        DragonDescentMenu.clearPlayerState(playerId);
        DealerInteractListener.clearActiveAnimation(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        PendingPayoutStore store = plugin.getPendingPayoutStore();
        if (store == null) {
            return;
        }

        Player player = event.getPlayer();
        DeliveryResult result = store.attemptDeliver(player);

        for (PendingPayout payout : result.delivered()) {
            player.sendMessage(PayoutMessages.formatDelivered(payout));
        }
        if (!result.stillPending().isEmpty()) {
            player.sendMessage(PayoutMessages.formatPendingRetryNotice(result.stillPending().size()));
        }
    }
}
