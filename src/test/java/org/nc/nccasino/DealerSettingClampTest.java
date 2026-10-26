package org.nc.nccasino;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hand-edited dealer settings outside the admin menu's ranges: Dragon Descent
 * floors of 0 threw on the first click and a huge value ran the server out of
 * memory; 0 vines or 1 column made every step lose; timer 0 left no betting
 * window.
 */
class DealerSettingClampTest {

    private static Nccasino pluginWith(YamlConfiguration config) {
        Nccasino plugin = mock(Nccasino.class);
        when(plugin.getConfig()).thenReturn(config);
        doCallRealMethod().when(plugin).getDragonDescentColumns(anyString());
        doCallRealMethod().when(plugin).getDragonDescentVines(anyString());
        doCallRealMethod().when(plugin).getDragonDescentFloors(anyString());
        doCallRealMethod().when(plugin).getTimer(anyString());
        return plugin;
    }

    @Test
    void dragonSettingsStayInsideTheAdminMenusRanges() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("dealers.d.default-columns", "1");
        config.set("dealers.d.default-vines", "0");
        config.set("dealers.d.default-floors", "1000000000");
        Nccasino plugin = pluginWith(config);

        assertEquals(2, plugin.getDragonDescentColumns("d"));
        assertEquals(1, plugin.getDragonDescentVines("d"));
        assertEquals(100, plugin.getDragonDescentFloors("d"));

        config.set("dealers.d.default-columns", "7");
        config.set("dealers.d.default-vines", "9");
        config.set("dealers.d.default-floors", "-3");
        assertEquals(6, plugin.getDragonDescentVines("d"), "vines stay below the columns");
        assertEquals(1, plugin.getDragonDescentFloors("d"));
    }

    @Test
    void aTimerOfZeroStillLeavesABettingWindow() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("dealers.r.timer", "0");
        assertEquals(1, pluginWith(config).getTimer("r"));
    }
}
