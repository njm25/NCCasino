package org.nc.nccasino.helpers;

import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DealerSettingsSnapshotTest {
    private static final String PATH = "dealers.bj1";

    @Test
    void restoresEverySettingAfterTheSectionWasDeletedAndReset() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(PATH + ".game", "Blackjack");
        config.set(PATH + ".budget.mode", "LIMITED");
        config.set(PATH + ".budget.initial", 5000);
        config.set(PATH + ".currency.mode", "VAULT");
        config.set(PATH + ".currency.material", "GOLD_INGOT");
        config.set(PATH + ".insurance-enabled", false);
        config.set(PATH + ".turn-timer-seconds", 45);
        config.set(PATH + ".chip-sizes.size1", 3);
        config.set(PATH + ".some-list", List.of("a", "b"));
        config.set("dealers.other.game", "Mines");

        DealerSettingsSnapshot snapshot = DealerSettingsSnapshot.capture(config, PATH);

        // What a mob swap does in between: removeDealer deletes the section,
        // spawnDealer writes fresh defaults.
        config.set(PATH, null);
        config.set(PATH + ".game", "Blackjack");
        config.set(PATH + ".currency.material", "EMERALD");
        config.set(PATH + ".chip-sizes.size1", 1);
        config.set(PATH + ".default-only-key", 7);

        snapshot.restore(config, PATH);

        assertEquals("LIMITED", config.getString(PATH + ".budget.mode"));
        assertEquals(5000, config.getInt(PATH + ".budget.initial"));
        assertEquals("VAULT", config.getString(PATH + ".currency.mode"));
        assertEquals("GOLD_INGOT", config.getString(PATH + ".currency.material"));
        assertFalse(config.getBoolean(PATH + ".insurance-enabled", true));
        assertEquals(45, config.getInt(PATH + ".turn-timer-seconds"));
        assertEquals(3, config.getInt(PATH + ".chip-sizes.size1"));
        assertEquals(List.of("a", "b"), config.getStringList(PATH + ".some-list"));
        assertFalse(config.isSet(PATH + ".default-only-key"), "nothing the old dealer lacked is left behind");
        assertEquals("Mines", config.getString("dealers.other.game"), "other dealers are untouched");
    }

    @Test
    void aMissingSectionKeepsTheFreshDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        DealerSettingsSnapshot snapshot = DealerSettingsSnapshot.capture(config, PATH);
        config.set(PATH + ".game", "Mines");

        snapshot.restore(config, PATH);

        assertEquals("Mines", config.getString(PATH + ".game"));
    }
}
