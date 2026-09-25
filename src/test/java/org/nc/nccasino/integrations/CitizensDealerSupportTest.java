package org.nc.nccasino.integrations;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Dealer;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the three read-only-audit findings fixed here:
 * the missing startup sweep of already-spawned bound NPCs (P0), the
 * inventory/session/budget cleanup gap when a Citizens NPC is removed with no
 * live Bukkit entity (P1), and the {@link Dealer#dealers} registry leak on
 * detach/removal (P2).
 *
 * <p>These exercise {@link CitizensDealerSupport}'s package-private methods
 * directly rather than through {@link CitizensDealerSupport#register}, so
 * each test controls exactly the static state ({@code plugin}, {@code
 * available}, the body-tracking map) it needs via reflection, without paying
 * for the unrelated {@code Bukkit.getPluginManager()} wiring {@code register}
 * would otherwise require.
 */
class CitizensDealerSupportTest {

    private Nccasino plugin;
    private MockedStatic<JavaPlugin> javaPluginStatic;

    @BeforeEach
    void setUp() throws Exception {
        // Dealer's static initializer calls JavaPlugin.getProvidingPlugin(),
        // which throws outside a real plugin classloader -- this must be
        // mocked before anything in this test touches the Dealer class,
        // exactly like DealerCitizensBackendTest, or Dealer's <clinit> fails
        // once for the whole JVM fork and poisons every other test that
        // shares it.
        JavaPlugin providingPlugin = mock(JavaPlugin.class);
        when(providingPlugin.getName()).thenReturn("NCCasino");
        javaPluginStatic = Mockito.mockStatic(JavaPlugin.class);
        javaPluginStatic.when(() -> JavaPlugin.getProvidingPlugin(any())).thenReturn(providingPlugin);

        plugin = mock(Nccasino.class);
        setPrivateStatic("plugin", plugin);
        setPrivateStatic("available", true);
        lastKnownBodyMap().clear();
        Dealer.dealers.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        setPrivateStatic("plugin", null);
        setPrivateStatic("available", false);
        lastKnownBodyMap().clear();
        Dealer.dealers.clear();
        javaPluginStatic.close();
    }

    @Test
    @DisplayName("releaseNpc evicts the entity-UUID-keyed Dealer.dealers entry left by adoptBody")
    void releaseNpcEvictsDealerRegistryEntry() throws Exception {
        UUID npcId = UUID.randomUUID();
        UUID bodyId = UUID.randomUUID();

        // Simulate what adoptBody does on bind/respawn: register the live body
        // and remember it against the NPC id.
        LivingEntity body = mock(LivingEntity.class);
        when(body.getUniqueId()).thenReturn(bodyId);
        new Dealer(body);
        lastKnownBodyMap().put(npcId, bodyId);

        assertTrue(Dealer.dealers.containsKey(bodyId), "test setup should have populated the registry");

        NPC npc = mock(NPC.class);
        when(npc.getUniqueId()).thenReturn(npcId);
        when(npc.getEntity()).thenReturn(null);
        MetadataStore store = wireStore(new HashMap<>());
        when(npc.data()).thenReturn(store);

        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.getByUniqueId(npcId)).thenReturn(npc);

        try (MockedStatic<CitizensAPI> citizensApi = Mockito.mockStatic(CitizensAPI.class)) {
            citizensApi.when(CitizensAPI::getNPCRegistry).thenReturn(registry);

            CitizensDealerSupport.releaseNpc(npcId);
        }

        // Before this fix, releaseNpc discarded the tracked body id without
        // ever evicting it from Dealer.dealers -- every rebind-away or
        // /ncc delete of a Citizens dealer leaked one entry for the life of
        // the server.
        assertFalse(Dealer.dealers.containsKey(bodyId),
            "releaseNpc must evict the entity-UUID-keyed registry entry, not just forget the tracked body");
        assertFalse(lastKnownBodyMap().containsKey(npcId));
    }

    @Test
    @DisplayName("forgetRemovedNpc with no live entity still tears down inventories/sessions by id")
    void forgetRemovedNpcWithNullEntityStillTearsDownState() {
        UUID dealerId = UUID.randomUUID();
        String internalName = "highroller";

        Map<String, Object> data = new HashMap<>();
        data.put("nccasino-dealer-id", dealerId.toString());
        data.put("nccasino-internal-name", internalName);

        NPC npc = mock(NPC.class);
        when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
        // The realistic failure sequence: /npc despawn then /npc remove, or
        // removal of an NPC standing in an unloaded chunk.
        when(npc.getEntity()).thenReturn((Entity) null);
        MetadataStore store = wireStore(data);
        when(npc.data()).thenReturn(store);

        CitizensDealerSupport.forgetRemovedNpc(npc);

        // Before this fix, a null entity meant dealerId could only be read
        // from the (now-gone) entity's own PDC, so this branch silently did
        // nothing: no inventory/session teardown, no budget-reservation
        // release path invoked at all.
        verify(plugin).deleteAssociatedInventories(dealerId, internalName);
        assertFalse(data.containsKey("nccasino-dealer-id"), "NPC-side record must still be cleared");
        assertFalse(data.containsKey("nccasino-internal-name"));
    }

    @Test
    @DisplayName("forgetRemovedNpc ignores an NPC with no NCCasino binding")
    void forgetRemovedNpcIgnoresUnboundNpc() {
        NPC npc = mock(NPC.class);
        MetadataStore store = wireStore(new HashMap<>());
        when(npc.data()).thenReturn(store);

        CitizensDealerSupport.forgetRemovedNpc(npc);

        verify(plugin, never()).deleteAssociatedInventories(any(), anyString());
        verify(plugin, never()).deleteAssociatedInventories(any(LivingEntity.class));
    }

    @Test
    @DisplayName("sweepAlreadySpawned restores a spawned, bound NPC instead of waiting for a future spawn event")
    void sweepAlreadySpawnedRestoresSpawnedBoundNpc() {
        UUID dealerId = UUID.randomUUID();
        String internalName = "highroller";

        Map<String, Object> data = new HashMap<>();
        data.put("nccasino-dealer-id", dealerId.toString());
        data.put("nccasino-internal-name", internalName);

        NPC bound = mock(NPC.class);
        when(bound.isSpawned()).thenReturn(true);
        // restoreOnSpawn checks the entity before the config guard, so this
        // must be a live LivingEntity to reach that guard at all.
        when(bound.getEntity()).thenReturn(mock(LivingEntity.class));
        MetadataStore store = wireStore(data);
        when(bound.data()).thenReturn(store);

        // The dealer was deleted from config while this NPC was spawned --
        // restoreOnSpawn's own "do not resurrect" guard, exercised here only
        // to prove the sweep actually reaches restoreOnSpawn for an
        // already-spawned NPC without needing a full game-construction stack.
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.contains("dealers." + internalName + ".game")).thenReturn(false);
        when(plugin.getConfig()).thenReturn(config);

        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.iterator()).thenReturn(Arrays.asList(bound).iterator());

        try (MockedStatic<CitizensAPI> citizensApi = Mockito.mockStatic(CitizensAPI.class)) {
            citizensApi.when(CitizensAPI::getNPCRegistry).thenReturn(registry);

            CitizensDealerSupport.sweepAlreadySpawned();
        }

        // Before this fix, nothing ever iterated CitizensAPI.getNPCRegistry():
        // restoration only happened on a future NPCSpawnEvent, which Citizens
        // fires (if at all) before NCCasino's own listener is registered.
        // restoreOnSpawn reaching its config-guard branch below is only
        // possible if the sweep actually invoked it for this NPC.
        assertFalse(data.containsKey("nccasino-dealer-id"));
        assertFalse(data.containsKey("nccasino-internal-name"));
    }

    @Test
    @DisplayName("CitizensEnableEvent triggers the sweep, not just the immediate best-effort call in register()")
    void citizensEnableEventTriggersSweep() {
        // Citizens' own documentation says its NPC registry is not reliably
        // populated merely because its onEnable has returned -- the
        // synchronous call inside register() can run before Citizens has
        // finished loading NPCs from disk. CitizensEnableEvent is Citizens'
        // documented "safe to use the API now" signal, so it must reach the
        // exact same restoration path as the immediate call, not a
        // best-effort duplicate of only part of it.
        UUID dealerId = UUID.randomUUID();
        String internalName = "highroller";

        Map<String, Object> data = new HashMap<>();
        data.put("nccasino-dealer-id", dealerId.toString());
        data.put("nccasino-internal-name", internalName);

        NPC bound = mock(NPC.class);
        when(bound.isSpawned()).thenReturn(true);
        when(bound.getEntity()).thenReturn(mock(LivingEntity.class));
        MetadataStore store = wireStore(data);
        when(bound.data()).thenReturn(store);

        FileConfiguration config = mock(FileConfiguration.class);
        when(config.contains("dealers." + internalName + ".game")).thenReturn(false);
        when(plugin.getConfig()).thenReturn(config);

        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.iterator()).thenReturn(Arrays.asList(bound).iterator());

        try (MockedStatic<CitizensAPI> citizensApi = Mockito.mockStatic(CitizensAPI.class)) {
            citizensApi.when(CitizensAPI::getNPCRegistry).thenReturn(registry);

            new CitizensDealerListener().onCitizensEnable(mock(net.citizensnpcs.api.event.CitizensEnableEvent.class));
        }

        assertFalse(data.containsKey("nccasino-dealer-id"));
        assertFalse(data.containsKey("nccasino-internal-name"));
    }

    @Test
    @DisplayName("sweepAlreadySpawned skips an NPC that Citizens has not spawned yet")
    void sweepAlreadySpawnedSkipsUnspawnedNpc() {
        NPC notYetSpawned = mock(NPC.class);
        when(notYetSpawned.isSpawned()).thenReturn(false);

        NPCRegistry registry = mock(NPCRegistry.class);
        when(registry.iterator()).thenReturn(Arrays.asList(notYetSpawned).iterator());

        try (MockedStatic<CitizensAPI> citizensApi = Mockito.mockStatic(CitizensAPI.class)) {
            citizensApi.when(CitizensAPI::getNPCRegistry).thenReturn(registry);

            CitizensDealerSupport.sweepAlreadySpawned();
        }

        // restoreOnSpawn's first action is always npc.data() -- never called
        // proves the isSpawned() filter, not just an accident of stubbing.
        verify(notYetSpawned, never()).data();
    }

    @Test
    @DisplayName("sweepAlreadySpawned is a no-op without Citizens installed")
    void sweepAlreadySpawnedNoOpWithoutCitizens() throws Exception {
        setPrivateStatic("available", false);

        // No CitizensAPI mocking at all: if the availability guard were
        // missing, this would throw (or worse, attempt to talk to a Citizens
        // that was never initialized) instead of returning immediately.
        CitizensDealerSupport.sweepAlreadySpawned();
    }

    private static MetadataStore wireStore(Map<String, Object> backing) {
        MetadataStore store = mock(MetadataStore.class);
        when(store.has(anyString())).thenAnswer(inv -> backing.containsKey(inv.getArgument(0)));
        when(store.get(anyString())).thenAnswer(inv -> backing.get(inv.getArgument(0)));
        doAnswer(inv -> {
            backing.remove((String) inv.getArgument(0));
            return null;
        }).when(store).remove(anyString());
        doAnswer(inv -> {
            backing.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(store).setPersistent(anyString(), any());
        return store;
    }

    @Test
    @DisplayName("onNpcDespawn ignores routine world-streaming/respawn/reload despawn reasons")
    void onNpcDespawnIgnoresTransientReasons() {
        // CHUNK_UNLOAD, WORLD_UNLOAD, PENDING_RESPAWN and RELOAD are all
        // routine churn (confirmed against Citizens' own CitizensNPC.despawn())
        // -- a player simply walking away can trigger CHUNK_UNLOAD. None of
        // these should tear down an active session; each is already handled
        // correctly elsewhere (respawn re-adopts the body, reload is paired
        // with CitizensEnableEvent's sweep).
        NPC npc = mock(NPC.class);
        CitizensDealerListener listener = new CitizensDealerListener();

        for (DespawnReason reason : new DespawnReason[] {
            DespawnReason.CHUNK_UNLOAD, DespawnReason.WORLD_UNLOAD,
            DespawnReason.PENDING_RESPAWN, DespawnReason.RELOAD
        }) {
            listener.onNpcDespawn(new NPCDespawnEvent(npc, reason));
        }

        // detachOnDespawn's only externally-visible effect (once past its
        // metadata reads) is this call -- verifying it was never reached
        // proves the reason filter, not merely that the metadata was absent.
        verify(plugin, never()).deleteAssociatedInventories(any(UUID.class), anyString());
        verify(npc, never()).data();
    }

    @Test
    @DisplayName("onNpcDespawn still detaches for a real despawn reason")
    void onNpcDespawnDetachesForRealReasons() {
        UUID dealerId = UUID.randomUUID();
        String internalName = "highroller";

        Map<String, Object> data = new HashMap<>();
        data.put("nccasino-dealer-id", dealerId.toString());
        data.put("nccasino-internal-name", internalName);

        NPC npc = mock(NPC.class);
        when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
        when(npc.getEntity()).thenReturn(null);
        MetadataStore store = wireStore(data);
        when(npc.data()).thenReturn(store);

        new CitizensDealerListener().onNpcDespawn(new NPCDespawnEvent(npc, DespawnReason.PLUGIN));

        verify(plugin).deleteAssociatedInventories(dealerId, internalName);
    }

    private static void setPrivateStatic(String field, Object value) throws Exception {
        Field f = CitizensDealerSupport.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, value);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UUID> lastKnownBodyMap() throws Exception {
        Field f = CitizensDealerSupport.class.getDeclaredField("lastKnownBody");
        f.setAccessible(true);
        return (Map<UUID, UUID>) f.get(null);
    }
}
