package org.nc.nccasino.helpers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigFileCheckTest {
    @TempDir
    Path directory;

    @Test
    void aReadableFileHasNoProblem() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "dealers:\n  table1:\n    game: Slots\n", StandardCharsets.UTF_8);
        assertNull(ConfigFileCheck.problem(file.toFile()));
    }

    @Test
    void aMissingFileHasNoProblem() {
        assertNull(ConfigFileCheck.problem(directory.resolve("config.yml").toFile()));
    }

    @Test
    void aYamlErrorIsReported() throws IOException {
        Path file = directory.resolve("config.yml");
        // A tab indent: the commonest hand-edit mistake.
        Files.writeString(file, "dealers:\n\ttable1:\n    game: Slots\n", StandardCharsets.UTF_8);
        assertNotNull(ConfigFileCheck.problem(file.toFile()));
    }
}
