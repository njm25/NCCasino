package org.nc.nccasino.integrations;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.components.AdminMenu;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.JockeyManager;
import org.nc.nccasino.entities.JockeyNode;
import org.nc.nccasino.listeners.DealerEventListener;

import java.io.File;
import java.io.IOException;
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

    /**
     * The Bukkit entity each bound NPC was last seen wearing, keyed by NPC id.
     *
     * <p>Citizens builds a brand-new entity -- with a brand-new UUID -- every
     * time an NPC respawns, and {@link Dealer}'s live-instance map is keyed by
     * entity UUID. Without this, every respawn would add an entry that nothing
     * ever removes, pinning a dead entity in memory for the life of the server.
     */
    private static final Map<UUID, UUID> lastKnownBody = new HashMap<>();

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
        lastKnownBody.remove(npcId);
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
        adoptBody(npc, living);
        Dealer.updateGameType(living, gameType, timer, animationMessage, displayName,
            chipSizes, currencyMaterial, currencyName);
        // The dealer now lives wherever the NPC stands. data/dealers.yaml drives
        // the chunk-loading that '/ncc delete *' and startup rely on, so it has
        // to follow the dealer to its new body.
        saveDealerLocation(internalName, living.getLocation());

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
            removeMobBody(existing);
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
        adoptBody(npc, living);
        Dealer.updateGameType(living, gameType, timer, animationMessage, displayName,
            readChipSizes(path), currencyMaterial, currencyName);
    }

    /**
     * Registers {@code living} as the dealer's live body, retiring whatever
     * entity the NPC was wearing before.
     *
     * <p>Citizens hands out a new entity UUID on every respawn, so without the
     * eviction here {@link Dealer}'s instance map would accumulate one dead
     * entry per respawn for as long as the server runs.
     */
    private static void adoptBody(NPC npc, LivingEntity living) {
        UUID previous = lastKnownBody.put(npc.getUniqueId(), living.getUniqueId());
        if (previous != null && !previous.equals(living.getUniqueId())) {
            Dealer.removeDealerFromMap(previous);
        }
        new Dealer(living);
    }

    /**
     * Records where a dealer now stands in {@code data/dealers.yaml}, which is
     * kept separate from the main config and is what the plugin uses to find and
     * load a dealer's chunk.
     */
    private static void saveDealerLocation(String internalName, Location location) {
        File dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");
        if (!dealersFile.getParentFile().exists()) {
            dealersFile.getParentFile().mkdirs();
        }
        FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);
        String path = "dealers." + internalName;
        dealersConfig.set(path + ".world", location.getWorld().getName());
        dealersConfig.set(path + ".X", location.getX());
        dealersConfig.set(path + ".Y", location.getY());
        dealersConfig.set(path + ".Z", location.getZ());
        try {
            dealersConfig.save(dealersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save dealer location to " + dealersFile.getPath());
        }
    }

    /**
     * Unbinds a dealer whose NPC an admin deleted through Citizens, so no stale
     * body or open inventory is left behind.
     *
     * <p>Deliberately keeps the dealer's configuration. {@code /npc remove} is
     * Citizens' command, not ours, and an admin running it -- or
     * {@code /npc remove --all} -- is saying something about their NPCs, not
     * asking NCCasino to destroy a casino's worth of game settings, chip sizes
     * and currency. The configured dealer survives, ready to be bound to another
     * NPC; removing it for good is still {@code /ncc delete <name>}.
     */
    static void forgetRemovedNpc(NPC npc) {
        String internalName = readNpcString(npc, META_INTERNAL_NAME);
        if (internalName == null) {
            return;
        }
        npc.data().remove(META_DEALER_ID);
        npc.data().remove(META_INTERNAL_NAME);

        UUID lastBody = lastKnownBody.remove(npc.getUniqueId());
        Entity entity = npc.getEntity();
        if (entity instanceof LivingEntity living && Dealer.isDealer(living)) {
            UUID dealerId = Dealer.getUniqueId(living);
            AdminMenu.clearAllEditModes(living);
            plugin.deleteAssociatedInventories(living);
            Dealer.clearCitizensTags(living);
            if (dealerId != null) {
                Dealer.removeDealerFromMap(dealerId);
            }
        }
        if (lastBody != null) {
            Dealer.removeDealerFromMap(lastBody);
        }
    }

    /**
     * Removes a vanilla mob dealer along with the jockey stack and name-tag
     * armor stand riding it.
     *
     * <p>Removing only the dealer would strand those extra entities in the world
     * with nothing left referencing them; this mirrors what {@code /ncc delete}
     * does.
     */
    private static void removeMobBody(LivingEntity existing) {
        if (existing instanceof Mob mobEntity) {
            JockeyManager jockeyManager = new JockeyManager(mobEntity);
            jockeyManager.cleanup();

            List<JockeyNode> jockeys = jockeyManager.getJockeys();
            for (int i = jockeys.size() - 1; i > 0; i--) {
                JockeyNode jockey = jockeys.get(i);
                jockey.unmount();
                jockey.getMob().remove();
            }
            for (Entity passenger : new ArrayList<>(mobEntity.getPassengers())) {
                if (passenger instanceof ArmorStand) {
                    passenger.remove();
                }
            }
            DealerEventListener.clearJockeyManagerCache(mobEntity.getUniqueId());
        }
        existing.remove();
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
