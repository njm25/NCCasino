package org.nc.nccasino.entities;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the backend distinction that makes Citizens NPC dealers safe to
 * attach to an NPC someone else owns.
 *
 * <p>These are the invariants that protect an admin's NPC from NCCasino:
 * an untagged entity must still read as a plain mob dealer (so worlds that
 * predate Citizens support are unaffected), and a Citizens-backed dealer must
 * never have its visible name rewritten, because on a player-type NPC the name
 * is also what Citizens resolves the skin from.
 */
class DealerCitizensBackendTest {

    private MockedStatic<JavaPlugin> javaPluginStatic;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("NCCasino");
        javaPluginStatic = Mockito.mockStatic(JavaPlugin.class);
        javaPluginStatic.when(() -> JavaPlugin.getProvidingPlugin(any())).thenReturn(plugin);
    }

    @AfterEach
    void tearDown() {
        javaPluginStatic.close();
    }

    @Test
    @DisplayName("an entity with no backend tag reads as a mob dealer")
    void untaggedEntityDefaultsToMobBackend() {
        LivingEntity entity = entityWithContainer();

        // Every dealer that existed before Citizens support was added is
        // untagged; they must keep behaving exactly as they did.
        assertEquals(Dealer.Backend.MOB, Dealer.getBackend(entity));
        assertNull(Dealer.getCitizensNpcId(entity));
    }

    @Test
    @DisplayName("a corrupt backend tag falls back to MOB rather than throwing")
    void unparseableBackendFallsBackToMob() {
        LivingEntity entity = entityWithContainer();
        Dealer.tagCitizensDealer(entity, UUID.randomUUID(), "vip", "VIP Dealer", "Blackjack", UUID.randomUUID());

        NamespacedKey backendKey = new NamespacedKey(JavaPlugin.getProvidingPlugin(Dealer.class), "dealer_backend");
        entity.getPersistentDataContainer().set(backendKey, PersistentDataType.STRING, "NOT_A_BACKEND");

        assertEquals(Dealer.Backend.MOB, Dealer.getBackend(entity));
    }

    @Test
    @DisplayName("tagging a Citizens dealer records the NPC identity, not the entity's")
    void tagCitizensDealerStoresNpcIdentity() {
        LivingEntity entity = entityWithContainer();
        UUID dealerId = UUID.randomUUID();
        UUID npcId = UUID.randomUUID();

        Dealer.tagCitizensDealer(entity, dealerId, "highroller", "High Roller", "Roulette", npcId);

        assertTrue(Dealer.isDealer(entity));
        assertEquals(Dealer.Backend.CITIZENS, Dealer.getBackend(entity));
        assertEquals(dealerId, Dealer.getUniqueId(entity));
        // The NPC id is the durable handle: Citizens rebuilds the Bukkit entity
        // (and its UUID) on every respawn, but the NPC id survives.
        assertEquals(npcId, Dealer.getCitizensNpcId(entity));
        assertEquals("highroller", Dealer.getInternalName(entity));
        assertEquals("High Roller", Dealer.getName(entity));
    }

    @Test
    @DisplayName("clearing Citizens tags leaves the entity looking like a non-dealer")
    void clearCitizensTagsRemovesEverything() {
        LivingEntity entity = entityWithContainer();
        Dealer.tagCitizensDealer(entity, UUID.randomUUID(), "vip", "VIP", "Mines", UUID.randomUUID());

        Dealer.clearCitizensTags(entity);

        // Unbinding must hand the NPC back with nothing of ours left on it.
        assertFalse(Dealer.isDealer(entity));
        assertNull(Dealer.getUniqueId(entity));
        assertNull(Dealer.getCitizensNpcId(entity));
        assertEquals(Dealer.Backend.MOB, Dealer.getBackend(entity));
    }

    @Test
    @DisplayName("renaming a Citizens dealer never touches the NPC's visible name")
    void setNameLeavesCitizensNpcNameAlone() {
        LivingEntity npcBody = entityWithContainer();
        Dealer.tagCitizensDealer(npcBody, UUID.randomUUID(), "vip", "VIP", "Blackjack", UUID.randomUUID());

        Dealer.setName(npcBody, "Renamed Dealer");

        // The admin owns the NPC's name and skin. Overwriting the custom name
        // here would visibly change an NPC they had already styled -- and on a
        // player-type NPC, the name is what the default skin is fetched from.
        verify(npcBody, never()).setCustomName(anyString());
        assertEquals("Renamed Dealer", Dealer.getName(npcBody));
    }

    @Test
    @DisplayName("renaming a mob dealer still updates its name tag")
    void setNameStillRenamesMobDealers() {
        Mob mobDealer = mock(Mob.class);
        wireContainer(mobDealer);

        Dealer.setName(mobDealer, "Blackjack Dealer");

        verify(mobDealer, times(1)).setCustomName("Blackjack Dealer");
        assertEquals("Blackjack Dealer", Dealer.getName(mobDealer));
    }

    private static LivingEntity entityWithContainer() {
        LivingEntity entity = mock(LivingEntity.class);
        wireContainer(entity);
        return entity;
    }

    /**
     * Gives {@code entity} a persistent-data container backed by a real map, so
     * the round-trip through Bukkit's PDC API behaves like the server's.
     */
    private static void wireContainer(LivingEntity entity) {
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        Map<NamespacedKey, Object> values = new HashMap<>();

        Mockito.doAnswer(inv -> {
            values.put(inv.getArgument(0), inv.getArgument(2));
            return null;
        }).when(container).set(any(), any(), any());
        when(container.get(any(), any())).thenAnswer(inv -> values.get(inv.getArgument(0)));
        when(container.has(any(), any())).thenAnswer(inv -> values.containsKey(inv.getArgument(0)));
        when(container.has(any())).thenAnswer(inv -> values.containsKey(inv.getArgument(0)));
        // A copy: deleteAllPersistentData removes while iterating this set.
        when(container.getKeys()).thenAnswer(inv -> {
            Set<NamespacedKey> keys = new HashSet<>(values.keySet());
            return keys;
        });
        Mockito.doAnswer(inv -> {
            values.remove(inv.getArgument(0));
            return null;
        }).when(container).remove(any());

        when(entity.getPersistentDataContainer()).thenReturn(container);
    }
}
