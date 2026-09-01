package org.nc.nccasino.integrations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Dealer;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.LookClose;

/**
 * Optional integration letting an admin bind a Citizens-managed NPC as a
 * dealer's physical form instead of a plain Bukkit mob. Every entry point
 * here is only ever reached from a call site that has already checked
 * {@link #isAvailable()}, so Citizens classes are never touched -- and
 * never even loaded by the JVM -- on a server that doesn't have Citizens
 * installed.
 *
 * A bound NPC is tagged with the exact same {@code PersistentDataContainer}
 * scheme a plain mob dealer uses (see {@link Dealer#tagCitizensDealer}), so
 * every existing lookup (Dealer.findDealer/isDealer/getInternalName/...)
 * and every per-game admin settings menu works unchanged. Game construction
 * itself (building the actual Blackjack/Roulette/etc. Server instance) is
 * left to the existing {@link Dealer#updateGameType}, not duplicated here.
 */
public class CitizensDealerSupport implements Listener {

    private static final String META_DEALER_ID = "nccasino-dealer-id";
    private static final String META_INTERNAL_NAME = "nccasino-internal-name";
    private static final long BIND_TIMEOUT_TICKS = 20L * 30; // 30 seconds

    private record PendingBind(String internalName, BukkitTask timeout) {}

    private static final Map<UUID, PendingBind> pendingBinds = new HashMap<>();

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens");
    }

    public static void register(Nccasino plugin) {
        if (!isAvailable()) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(new CitizensDealerSupport(), plugin);
    }

    /**
     * Creates a brand new Citizens NPC for an admin who doesn't already
     * have one configured with Citizens' own tools, then binds it exactly
     * like {@link #bindNpc}.
     */
    public static LivingEntity createAndBindNpc(EntityType type, String npcName, Location location, String internalName) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        NPC npc = registry.createNPC(type, npcName);
        return bindNpc(npc, location, internalName);
    }

    /**
     * Binds an existing Citizens NPC (spawned or not, already configured
     * by the admin with Citizens' own skin/equipment/name commands) as the
     * dealer for {@code internalName}. The caller is expected to follow up
     * with {@link Dealer#updateGameType} to (re)build the actual game,
     * exactly as swapping a plain dealer's mob type does.
     */
    public static LivingEntity bindNpc(NPC npc, Location fallbackLocation, String internalName) {
        if (!npc.isSpawned()) {
            npc.spawn(fallbackLocation);
        }
        if (!(npc.getEntity() instanceof LivingEntity entity)) {
            throw new IllegalStateException("Citizens NPC " + npc.getId() + " has no living entity to bind as a dealer");
        }

        UUID dealerId = npc.getUniqueId();
        Dealer.tagCitizensDealer(entity, dealerId, internalName, npc.getName(), npc.getUniqueId());

        // Citizens persists this independently of any one entity instance --
        // it's what lets onNpcSpawn re-tag a freshly respawned entity below.
        npc.data().setPersistent(META_DEALER_ID, dealerId.toString());
        npc.data().setPersistent(META_INTERNAL_NAME, internalName);

        npc.getOrAddTrait(LookClose.class).lookClose(true);

        new Dealer(entity);
        return entity;
    }

    /**
     * Starts the admin-facing binding flow: the next Citizens NPC
     * {@code admin} right-clicks within {@link #BIND_TIMEOUT_TICKS} becomes
     * the dealer for {@code internalName}, replacing whatever entity (mob
     * or a previously bound NPC) currently represents it. Call this after
     * closing the admin's inventory, mirroring the chat-prompt pattern used
     * for editing a dealer's timer/name.
     */
    public static void beginBindFlow(Nccasino plugin, Player admin, String internalName) {
        UUID adminId = admin.getUniqueId();
        cancelPendingBind(adminId);

        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingBinds.remove(adminId) != null) {
                admin.sendMessage(plugin.getLocalization().text(admin, "admin.citizens-bind-timeout"));
            }
        }, BIND_TIMEOUT_TICKS);

        pendingBinds.put(adminId, new PendingBind(internalName, timeout));
        admin.sendMessage(plugin.getLocalization().text(admin, "admin.citizens-bind-prompt"));
    }

    private static void cancelPendingBind(UUID adminId) {
        PendingBind existing = pendingBinds.remove(adminId);
        if (existing != null) {
            existing.timeout().cancel();
        }
    }

    @EventHandler
    public void onNpcRightClickForBind(NPCRightClickEvent event) {
        Player admin = event.getClicker();
        PendingBind pending = pendingBinds.remove(admin.getUniqueId());
        if (pending == null) {
            return;
        }
        pending.timeout().cancel();
        event.setCancelled(true);

        LivingEntity entity = swapToNpc(pending.internalName(), event.getNPC(), admin.getLocation());
        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(Dealer.class);
        if (entity == null) {
            admin.sendMessage(plugin.getLocalization().text(admin, "admin.citizens-bind-failed"));
            return;
        }
        admin.sendMessage(plugin.getLocalization().text(admin, "admin.citizens-bind-success", "dealer", pending.internalName()));
    }

    /**
     * Replaces whatever entity currently represents {@code internalName}
     * (a plain mob, or a previously bound NPC) with {@code npc}, preserving
     * the dealer's existing config (game type, display name, timer,
     * currency, chip sizes) exactly like swapping a plain dealer's mob type
     * does in {@code MobSelectionMenu}.
     */
    public static LivingEntity swapToNpc(String internalName, NPC npc, Location fallbackLocation) {
        Nccasino plugin = (Nccasino) JavaPlugin.getProvidingPlugin(Dealer.class);

        String gameType = plugin.getConfig().getString("dealers." + internalName + ".game", "Game Menu");
        String displayName = plugin.getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
        int timer = plugin.getConfig().getInt("dealers." + internalName + ".timer", 30);
        String anmsg = plugin.getConfig().getString("dealers." + internalName + ".animation-message", "NCCasino - " + gameType);
        List<Integer> chipSizes = new ArrayList<>();
        ConfigurationSection chipSizeSection = plugin.getConfig().getConfigurationSection("dealers." + internalName + ".chip-sizes");
        if (chipSizeSection != null) {
            for (String key : chipSizeSection.getKeys(false)) {
                chipSizes.add(plugin.getConfig().getInt("dealers." + internalName + ".chip-sizes." + key));
            }
        }
        chipSizes.sort(Integer::compareTo);
        String currencyMaterial = plugin.getConfig().getString("dealers." + internalName + ".currency.material", "EMERALD");
        String currencyName = plugin.getConfig().getString("dealers." + internalName + ".currency.name", "Emerald");

        LivingEntity oldDealer = Dealer.findDealerByInternalName(internalName);
        if (oldDealer != null) {
            plugin.deleteAssociatedInventories(oldDealer);
            org.nc.nccasino.components.AdminMenu.clearAllEditModes(oldDealer);
            Dealer.removeDealer(oldDealer);
        }

        LivingEntity entity = bindNpc(npc, fallbackLocation, internalName);
        Dealer.updateGameType(entity, gameType, timer, anmsg, displayName, chipSizes, currencyMaterial, currencyName);
        return entity;
    }

    /** Strips NCCasino's tags from a bound NPC without touching the NPC or entity itself. */
    public static void unbind(UUID npcId) {
        NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcId);
        if (npc == null) {
            return;
        }
        npc.data().remove(META_DEALER_ID);
        npc.data().remove(META_INTERNAL_NAME);
        if (npc.getEntity() instanceof LivingEntity entity) {
            Dealer.clearCitizensTags(entity);
        }
    }

    /**
     * Citizens gives a respawned NPC a brand new underlying Bukkit entity,
     * so a dealer's PersistentDataContainer tags (which live on that
     * entity, not on the NPC) need to be re-applied every time. This is
     * the Citizens-side replacement for DealerInitializeListener's
     * chunk-load rescan.
     */
    @EventHandler
    public void onNpcSpawn(NPCSpawnEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(META_DEALER_ID)) {
            return;
        }
        if (!(npc.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        String dealerIdRaw = npc.data().get(META_DEALER_ID);
        String internalName = npc.data().get(META_INTERNAL_NAME);
        if (dealerIdRaw == null || internalName == null) {
            return;
        }
        Dealer.tagCitizensDealer(entity, UUID.fromString(dealerIdRaw), internalName, npc.getName(), npc.getUniqueId());
        new Dealer(entity);
    }

    /**
     * The Citizens-side replacement for DealerDeathHandler: if an admin
     * deletes the bound NPC with /npc remove, clean up NCCasino's
     * registration and config too instead of leaving a dangling dealer.
     */
    @EventHandler
    public void onNpcRemove(NPCRemoveEvent event) {
        NPC npc = event.getNPC();
        if (!npc.data().has(META_DEALER_ID)) {
            return;
        }
        if (npc.getEntity() instanceof LivingEntity entity) {
            Dealer.removeDealer(entity);
        }
    }
}
