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
 * <p>{@code PlayerJoinEvent} settles whatever pending payouts/results
 * accumulated while the player was away. It deliberately does not attempt
 * to resume an interrupted game, clear admin edit-mode state, or repair
 * stale cached session objects — those are separate, later concerns
 * (respectively: not requested, admin edit-mode cleanup, and stale-client
 * handling).
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
