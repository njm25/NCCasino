package org.nc.nccasino.helpers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreFileTest {
    @TempDir
    Path directory;

    @Test
    void anUnreadableFileIsCopiedAsideAndNeverOverwritten() throws IOException {
        File file = directory.resolve("overflow-bank.yml").toFile();
        String broken = "banks:\n  player: [unclosed\n";
        Files.writeString(file.toPath(), broken, StandardCharsets.UTF_8);

        StoreFile store = new StoreFile(file, null);
        FileConfiguration loaded = store.load();
        assertTrue(loaded.getKeys(false).isEmpty());
        assertTrue(store.isReadOnly());

        FileConfiguration rebuilt = new YamlConfiguration();
        rebuilt.set("banks.someone.amount", 0);
        assertFalse(store.save(rebuilt));
        assertEquals(broken, Files.readString(file.toPath(), StandardCharsets.UTF_8));
        try (var copies = Files.list(directory)) {
            assertEquals(1, copies.filter(p -> p.getFileName().toString().startsWith("overflow-bank.yml.unreadable-")).count());
        }
    }

    @Test
    void saveReplacesTheFileWholeAndLeavesNoTempFile() throws IOException {
        File file = directory.resolve("overflow-bank.yml").toFile();
        Files.writeString(file.toPath(), "banks:\n  old: 1\n", StandardCharsets.UTF_8);

        StoreFile store = new StoreFile(file, null);
        store.load();
        FileConfiguration rebuilt = new YamlConfiguration();
        rebuilt.set("banks.new", 2);
        assertTrue(store.save(rebuilt));

        FileConfiguration reread = YamlConfiguration.loadConfiguration(file);
        assertEquals(2, reread.getInt("banks.new"));
        assertFalse(reread.isSet("banks.old"));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count(), "only the store file itself remains");
        }
    }

    @Test
    void skippedRecordsSurviveEverySave() throws IOException {
        File file = directory.resolve("pending-payouts.yml").toFile();
        Files.writeString(file.toPath(), """
            payouts:
              good:
                amount: 5
              not-a-uuid:
                amount: 12.5
                currency-name: Emerald
            """, StandardCharsets.UTF_8);

        StoreFile store = new StoreFile(file, null);
        store.load();
        store.preserve("payouts.not-a-uuid");
        FileConfiguration rebuilt = new YamlConfiguration();
        rebuilt.set("payouts.good.amount", 5);
        assertTrue(store.save(rebuilt));

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file);
        assertEquals(5, saved.getInt("payouts.good.amount"));
        assertEquals(12.5, saved.getDouble("unloaded.payouts.not-a-uuid.amount"));
        assertEquals("Emerald", saved.getString("unloaded.payouts.not-a-uuid.currency-name"));

        // A later load and save, with no new skips, keeps the earlier one.
        StoreFile again = new StoreFile(file, null);
        again.load();
        assertTrue(again.save(new YamlConfiguration()));
        assertEquals(12.5, YamlConfiguration.loadConfiguration(file).getDouble("unloaded.payouts.not-a-uuid.amount"));
    }
}
