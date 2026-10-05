package org.nc.nccasino.helpers;

import java.util.List;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigMigrationTest {
    @Test
    void onlyMissingGlobalOptionsAreReportedAndExistingValuesAreLeftAlone() throws InvalidConfigurationException {
        YamlConfiguration bundled = yaml("""
            config-version: 2
            language:
              default: en_US
              detect-client: true
            payouts:
              overflow-mode: PLAYER_CHOICE
            """);
        YamlConfiguration live = yaml("""
            language:
              default: de_DE
            dealers:
              table1:
                timer: 30
            """);
        assertEquals(List.of("language.detect-client", "payouts.overflow-mode"), ConfigMigration.missingOptions(bundled, live));
    }

    @Test
    void anUpToDateConfigHasNothingMissing() throws InvalidConfigurationException {
        YamlConfiguration bundled = yaml("language:\n  default: en_US\n");
        assertEquals(List.of(), ConfigMigration.missingOptions(bundled, yaml("language:\n  default: fr_FR\n")));
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(text);
        return configuration;
    }
}
