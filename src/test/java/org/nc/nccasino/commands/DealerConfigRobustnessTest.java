package org.nc.nccasino.commands;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.Nccasino;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two config hazards: a dealer name with a dot becomes nested YAML paths
 * (dealers.a.b), and a hand-edited currency material that doesn't exist made
 * every payout that builds an item from it fail.
 */
class DealerConfigRobustnessTest {

    @Test
    void aDealerNameWithADotIsRefused() {
        assertFalse(CreateCommand.isValidDealerName("vip.table"));
        assertFalse(CreateCommand.isValidDealerName(" "));
        assertTrue(CreateCommand.isValidDealerName("vip_table-2"));
    }

    @Test
    void anInvalidCurrencyMaterialFallsBackToEmerald() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("dealers.bad.currency.material", "NOT_A_THING");
        config.set("dealers.good.currency.material", "diamond");
        Nccasino plugin = mock(Nccasino.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DealerConfigRobustnessTest"));
        doCallRealMethod().when(plugin).getCurrency(anyString());
        setWarnedSet(plugin);

        assertEquals(Material.EMERALD, plugin.getCurrency("bad"));
        assertEquals(Material.DIAMOND, plugin.getCurrency("good"));
        assertEquals(Material.EMERALD, plugin.getCurrency("unset"));
    }

    /** A mock skips field initializers; give it the warn-once set the real plugin has. */
    private static void setWarnedSet(Nccasino plugin) {
        try {
            java.lang.reflect.Field field = Nccasino.class.getDeclaredField("invalidCurrencyWarned");
            field.setAccessible(true);
            field.set(plugin, java.util.concurrent.ConcurrentHashMap.newKeySet());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
