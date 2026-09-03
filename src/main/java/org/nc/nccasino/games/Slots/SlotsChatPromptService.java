package org.nc.nccasino.games.Slots;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one and only chat-prompt engine for Slots.
 *
 * <p>Profile naming and every Auto Spin Settings value share this single
 * {@code AsyncPlayerChatEvent} listener and one prompt-state map, rather than
 * each growing its own loosely-coordinated listener. That is what makes the
 * shared rules actually shared and enforceable:
 *
 * <ul>
 *   <li>at most one prompt per player at a time -- a newer prompt supersedes
 *   an older one;
 *   <li>a {@link #TIMEOUT_SECONDS}-second deadline, which a rejected attempt
 *   never restarts, and which a duplicate-name overwrite confirmation never
 *   restarts either;
 *   <li>chat input is always cancelled, so a profile name or a wager figure
 *   is never broadcast to the server;
 *   <li>opening another game cancels the prompt and closes the suspended
 *   Slots session, so a later callback can never reopen a stale inventory;
 *   <li>a timeout terminates the suspended session instead of reopening it;
 *   <li>disconnect, dealer removal, session termination and plugin shutdown
 *   all clean up through the same path;
 *   <li>every Bukkit mutation is handed back to the main thread.
 * </ul>
 */
public class SlotsChatPromptService implements Listener {

    /** Every Slots prompt gets exactly this long, from the moment it opens. */
    public static final long TIMEOUT_SECONDS = 60L;

    private static final long TIMEOUT_TICKS = TIMEOUT_SECONDS * 20L;

    private final Nccasino plugin;
    private final Map<UUID, Entry> active = new ConcurrentHashMap<>();

    private record Entry(SlotsChatPrompt prompt, BukkitTask timeoutTask) {
    }

    public SlotsChatPromptService(Nccasino plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** The absolute deadline a prompt opened now would carry. */
    public static long deadlineFromNow() {
        return System.currentTimeMillis() + (TIMEOUT_SECONDS * 1000L);
    }

    /**
     * Opens {@code prompt}, replacing any prompt this player already had.
     * Must be called on the main thread.
     */
    public void begin(SlotsChatPrompt prompt) {
        UUID playerId = prompt.playerId();
        end(playerId, SlotsChatPrompt.EndReason.SUPERSEDED);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(
            plugin, () -> expire(playerId, prompt), TIMEOUT_TICKS);
        active.put(playerId, new Entry(prompt, timeout));
    }

    public boolean hasPrompt(UUID playerId) {
        return active.containsKey(playerId);
    }

    /** The player's live prompt, or {@code null}. */
    public SlotsChatPrompt promptFor(UUID playerId) {
        Entry entry = active.get(playerId);
        return entry == null ? null : entry.prompt();
    }

    /**
     * Ends this player's prompt, if any, for a reason other than the player
     * answering it. Idempotent, and safe from a teardown path.
     */
    public void end(UUID playerId, SlotsChatPrompt.EndReason reason) {
        Entry entry = active.remove(playerId);
        if (entry == null) {
            return;
        }
        cancelTimeout(entry);
        entry.prompt().handler().ended(reason);
    }

    /**
     * Ends only a prompt owned by {@code session} -- used when one Slots
     * machine is torn down, so it can never cancel a prompt that a newer
     * session for the same player has already taken over.
     */
    public void endForSession(UUID playerId, InventoryHolder session, SlotsChatPrompt.EndReason reason) {
        Entry entry = active.get(playerId);
        if (entry == null || entry.prompt().session() != session) {
            return;
        }
        end(playerId, reason);
    }

    /** Ends every open prompt -- plugin shutdown. */
    public void shutdown() {
        List<UUID> owners = new ArrayList<>(active.keySet());
        for (UUID playerId : owners) {
            end(playerId, SlotsChatPrompt.EndReason.SESSION_ENDED);
        }
        HandlerList.unregisterAll(this);
    }

    private void cancelTimeout(Entry entry) {
        if (entry.timeoutTask() != null) {
            entry.timeoutTask().cancel();
        }
    }

    private void expire(UUID playerId, SlotsChatPrompt prompt) {
        Entry entry = active.get(playerId);
        if (entry == null || entry.prompt() != prompt) {
            return;
        }
        active.remove(playerId);
        prompt.handler().timedOut();
    }

    /**
     * Slots prompts own the player's chat completely while open: the message
     * is always cancelled (never broadcast), and the actual handling is
     * pushed to the main thread because it touches inventories, the profile
     * store and the session registry.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Entry entry = active.get(playerId);
        if (entry == null) {
            return;
        }
        event.setCancelled(true);
        event.getRecipients().clear();
        String message = event.getMessage() == null ? "" : event.getMessage().trim();
        SlotsChatPrompt prompt = entry.prompt();
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(playerId, prompt, message));
    }

    private void handleInput(UUID playerId, SlotsChatPrompt prompt, String message) {
        Entry entry = active.get(playerId);
        if (entry == null || entry.prompt() != prompt) {
            // Superseded, already answered, or already torn down between the
            // async event and this main-thread hop.
            return;
        }
        if (!prompt.handler().isSessionValid()) {
            end(playerId, SlotsChatPrompt.EndReason.SESSION_ENDED);
            return;
        }
        if (prompt.isExpired(System.currentTimeMillis())) {
            active.remove(playerId);
            cancelTimeout(entry);
            prompt.handler().timedOut();
            return;
        }

        SlotsChatPrompt.Outcome outcome = prompt.handler().submit(message);
        if (outcome == SlotsChatPrompt.Outcome.RETRY) {
            // Deliberately keeps both the prompt and its original deadline.
            return;
        }
        active.remove(playerId);
        cancelTimeout(entry);
        if (outcome == SlotsChatPrompt.Outcome.CANCELLED) {
            prompt.handler().cancelled();
        } else {
            prompt.handler().accepted();
        }
    }

    /**
     * Opening any other NCCasino game or menu while a Slots prompt is open
     * cancels the prompt and closes the suspended Slots session, so the newly
     * opened game takes over cleanly and no later callback can reopen the
     * stale Slots inventory. The prompt's own session reopening itself is
     * explicitly excluded.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Entry entry = active.get(playerId);
        if (entry == null) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null || holder == entry.prompt().session()) {
            return;
        }
        if (!(holder instanceof DealerInventory)) {
            // An ordinary chest, furnace or workbench is not "another game".
            return;
        }
        end(playerId, SlotsChatPrompt.EndReason.ANOTHER_GAME_OPENED);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        end(event.getPlayer().getUniqueId(), SlotsChatPrompt.EndReason.DISCONNECTED);
    }
}
