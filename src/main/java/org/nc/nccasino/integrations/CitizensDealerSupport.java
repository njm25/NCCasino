package org.nc.nccasino.integrations;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminMenu;
import org.nc.nccasino.entities.Dealer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Attaches NCCasino dealers to NPCs owned by the Citizens plugin.
 *
 * <h2>Why this exists</h2>
 * NCCasino dealers are normally vanilla mobs that the plugin spawns and fully
 * owns. That works, but it forces a casino to be staffed by villagers and
 * tadpoles. Citizens NPCs -- particularly player-type NPCs with custom skins --
 * look like actual dealers, which is what server owners ask for.
 *
 * <h2>Division of ownership</h2>
 * The NPC belongs to the admin who created it; NCCasino only borrows it to host
 * a game. Concretely, this class never changes an NPC's name, skin, equipment,
 * position, protection or any other trait, and unbinding leaves the NPC exactly
 * as it found it. Deleting a bound dealer detaches the game and leaves the NPC
 * standing.
 *
 * <h2>Surviving respawns</h2>
 * Citizens destroys and re-creates the underlying Bukkit entity whenever an NPC
 * despawns and respawns, which throws away the persistent-data tags NCCasino
 * writes on the entity. The durable record therefore lives on the NPC itself
 * (Citizens persists it to its own saves file), and the entity tags are re-applied
 * from it on every spawn.
 *
 * <h2>Loading safely without Citizens</h2>
 * Citizens is a soft-dependency. This class holds no Citizens types in its fields
 * or static initialiser, and every method that touches one returns early unless
 * {@link #isAvailable()} is true, so the class loads and links cleanly on a server
 * that has never heard of Citizens. The event handlers -- whose signatures do
 * mention Citizens types -- live in {@link CitizensDealerListener}, which is only
 * ever instantiated when the plugin is present.
 */
public final class CitizensDealerSupport {

    /** Key under which the NCCasino dealer id is persisted on the NPC. */
    static final String META_DEALER_ID = "nccasino-dealer-id";
    /** Key under which the configured dealer name is persisted on the NPC. */
    static final String META_INTERNAL_NAME = "nccasino-internal-name";

    /** How long an admin has to right-click the NPC they want to bind. */
    private static final long BIND_TIMEOUT_TICKS = 30 * 20L;

    private static Nccasino plugin;
    private static boolean available;

    /** Admins currently being asked to right-click an NPC, keyed by player id. */
    private static final Map<UUID, String> pendingBinds = new HashMap<>();

    private CitizensDealerSupport() {
    }

    /** Whether Citizens is installed and enabled on this server. */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Wires up Citizens support if the plugin is present. Safe -- and a no-op --
     * to call on a server without Citizens.
     */
    public static void register(Nccasino nccasino) {
        plugin = nccasino;
        available = Bukkit.getPluginManager().isPluginEnabled("Citizens");
        if (!available) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(new CitizensDealerListener(), nccasino);
        nccasino.getLogger().info("Citizens detected -- NPC dealers are available.");
    }

    /**
     * Detaches NCCasino from an NPC without deleting it.
     *
     * <p>Called when a bound dealer is removed. Clears both the entity tags and
     * the NPC-side record, so a later respawn does not resurrect the dealer.
     */
    public static void releaseNpc(UUID npcId) {
        if (!available || npcId == null) {
            return;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcId);
        if (npc == null) {
            return;
        }
        npc.data().remove(META_DEALER_ID);
        npc.data().remove(META_INTERNAL_NAME);
        Entity entity = npc.getEntity();
        if (entity instanceof LivingEntity living) {
            Dealer.clearCitizensTags(living);
        }
    }

    /** Whether {@code entity} is the body of a Citizens NPC. */
    public static boolean isCitizensNpc(Entity entity) {
        if (!available || entity == null) {
            return false;
        }
        return CitizensAPI.getNPCRegistry().isNPC(entity);
    }

    /**
     * Asks {@code admin} to right-click the NPC that should host the dealer
     * configured as {@code internalName}. The request expires on its own.
     */
    public static void beginBindFlow(Player admin, String internalName) {
        if (!available) {
            return;
        }
        UUID adminId = admin.getUniqueId();
        pendingBinds.put(adminId, internalName);
        admin.closeInventory();
        admin.sendMessage(plugin.getLocalization().text(admin, "admin.citizens-bind-prompt"));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Only expire the request we created: if the admin already bound an
            // NPC and started a second bind, that newer request must survive.
            if (internalName.equals(pendingBinds.get(adminId))) {
                pendingBinds.remove(adminId);
                if (admin.isOnline()) {
                    admin.sendMessage(plugin.getLocalization().text(admin, "admin.citizens-bind-timeout"));
                }
            }
        }, BIND_TIMEOUT_TICKS);
    }

    /**
     * Handles a right-click on an NPC by an admin who is part-way through a bind.
     *
     * @return true if the click was consumed by the bind flow
     */
    static boolean consumeBindClick(NPC npc, Player clicker) {
        String internalName = pendingBinds.get(clicker.getUniqueId());
        if (internalName == null) {
            return false;
        }
        pendingBinds.remove(clicker.getUniqueId());
        bind(npc, internalName, clicker);
        return true;
    }

    /**
     * Moves the dealer configured as {@code internalName} onto {@code npc},
     * preserving its game, display name, timer, chip sizes and currency.
     */
    private static void bind(NPC npc, String internalName, Player admin) {
        Entity entity = npc.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            // Item, block and marker NPCs have no living body to host a dealer.
            admin.sendMessage(plugin.getLocalization().text(admin, "admin.citizens-bind-failed"));
            return;
        }

        // Refuse to steal an NPC that is already hosting a different dealer.
        String boundTo = readNpcString(npc, META_INTERNAL_NAME);
        if (boundTo != null && !boundTo.equals(internalName)) {
            admin.sendMessage(plugin.getLocalization().text(
                admin, "admin.citizens-bind-occupied", "dealer", boundTo));
            return;
        }

        String path = "dealers." + internalName;
        if (!plugin.getConfig().contains(path + ".game")) {
            admin.sendMessage(plugin.getLocalization().text(admin, "admin.citizens-bind-failed"));
            return;
        }

        String gameType = plugin.getConfig().getString(path + ".game", "Blackjack");
        String displayName = plugin.getConfig().getString(path + ".display-name", "Dealer");
        int timer = plugin.getConfig().getInt(path + ".timer", 30);
        String animationMessage = plugin.getConfig().getString(path + ".animation-message", "NCCasino");
        String currencyMaterial = plugin.getConfig().getString(path + ".currency.material", "EMERALD");
        String currencyName = plugin.getConfig().getString(path + ".currency.name", "Emerald");
        List<Integer> chipSizes = readChipSizes(path);

        // Retire whatever body the dealer is using now -- the vanilla mob it was
        // spawned as, or a previously bound NPC -- but keep its configuration.
        UUID dealerId = detachExistingBody(internalName);
        if (dealerId == null) {
            dealerId = UUID.randomUUID();
        }

        npc.data().setPersistent(META_DEALER_ID, dealerId.toString());
        npc.data().setPersistent(META_INTERNAL_NAME, internalName);

        Dealer.tagCitizensDealer(living, dealerId, internalName, displayName, gameType, npc.getUniqueId());
        new Dealer(living);
        Dealer.updateGameType(living, gameType, timer, animationMessage, displayName,
            chipSizes, currencyMaterial, currencyName);

        admin.sendMessage(plugin.getLocalization().text(
            admin, "admin.citizens-bind-success", "dealer", internalName, "npc", npc.getName()));
    }

    /**
     * Removes the dealer's current entity while leaving its configuration in
     * place, returning the dealer id to carry over to the new body.
     */
    private static UUID detachExistingBody(String internalName) {
        LivingEntity existing = Dealer.findDealerByInternalName(internalName);
        if (existing == null) {
            return null;
        }
        UUID dealerId = Dealer.getUniqueId(existing);

        AdminMenu.clearAllEditModes(existing);
        plugin.deleteAssociatedInventories(existing);

        Dealer.Backend backend = Dealer.getBackend(existing);
        UUID previousNpcId = Dealer.getCitizensNpcId(existing);
        Dealer.clearCitizensTags(existing);
        if (backend == Dealer.Backend.CITIZENS) {
            // Re-binding away from another NPC: give that one back untouched.
            if (previousNpcId != null) {
                releaseNpc(previousNpcId);
            }
        } else {
            existing.remove();
        }
        if (dealerId != null) {
            Dealer.removeDealerFromMap(dealerId);
        }
        return dealerId;
    }

    /**
     * Re-applies the dealer tags Citizens discarded when it rebuilt the NPC's
     * entity, and rebuilds the dealer's inventory if the server restarted.
     */
    static void restoreOnSpawn(NPC npc) {
        String dealerIdRaw = readNpcString(npc, META_DEALER_ID);
        String internalName = readNpcString(npc, META_INTERNAL_NAME);
        if (dealerIdRaw == null || internalName == null) {
            return;
        }
        if (!(npc.getEntity() instanceof LivingEntity living)) {
            return;
        }

        UUID dealerId;
        try {
            dealerId = UUID.fromString(dealerIdRaw);
        } catch (IllegalArgumentException e) {
            return;
        }

        String path = "dealers." + internalName;
        if (!plugin.getConfig().contains(path + ".game")) {
            // The dealer was deleted from config while the NPC was despawned.
            // Leave the NPC alone rather than resurrecting a dead dealer.
            npc.data().remove(META_DEALER_ID);
            npc.data().remove(META_INTERNAL_NAME);
            return;
        }

        String gameType = plugin.getConfig().getString(path + ".game", "Blackjack");
        String displayName = plugin.getConfig().getString(path + ".display-name", "Dealer");
        int timer = plugin.getConfig().getInt(path + ".timer", 30);
        String animationMessage = plugin.getConfig().getString(path + ".animation-message", "NCCasino");
        String currencyMaterial = plugin.getConfig().getString(path + ".currency.material", "EMERALD");
        String currencyName = plugin.getConfig().getString(path + ".currency.name", "Emerald");

        Dealer.tagCitizensDealer(living, dealerId, internalName, displayName, gameType, npc.getUniqueId());
        new Dealer(living);
        Dealer.updateGameType(living, gameType, timer, animationMessage, displayName,
            readChipSizes(path), currencyMaterial, currencyName);
    }

    /**
     * Drops NCCasino's record of a dealer whose NPC an admin deleted through
     * Citizens, so no stale dealer or inventory is left behind.
     */
    static void forgetRemovedNpc(NPC npc) {
        String internalName = readNpcString(npc, META_INTERNAL_NAME);
        if (internalName == null) {
            return;
        }
        Entity entity = npc.getEntity();
        if (entity instanceof LivingEntity living && Dealer.isDealer(living)) {
            // Deletes the dealer and its config; the NPC is already going away,
            // and removeDealer's Citizens branch will not try to delete it again.
            Dealer.removeDealer(living);
            return;
        }
        // Despawned NPC: no entity to read tags from, so clear config directly.
        plugin.getConfig().set("dealers." + internalName, null);
        plugin.saveConfig();
    }

    private static List<Integer> readChipSizes(String path) {
        List<Integer> chipSizes = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path + ".chip-sizes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                chipSizes.add(plugin.getConfig().getInt(path + ".chip-sizes." + key));
            }
        }
        chipSizes.sort(Integer::compareTo);
        return chipSizes;
    }

    private static String readNpcString(NPC npc, String key) {
        if (!npc.data().has(key)) {
            return null;
        }
        Object value = npc.data().get(key);
        return (value == null) ? null : value.toString();
    }

    static Nccasino plugin() {
        return plugin;
    }
}
