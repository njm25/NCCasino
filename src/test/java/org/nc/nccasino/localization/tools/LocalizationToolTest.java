package org.nc.nccasino.localization.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nc.nccasino.localization.LocaleRegistry.LocaleSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationToolTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsOnlyPlaceholderWordJoinsIntroducedByTranslation() {
        assertEquals(
            List.of("{player}ist", "{currency}gewonnen"),
            SyntaxTokens.introducedPlaceholderWordJoins(
                "{player}'s turn; you won {currency}! Time: {seconds}s",
                "{player}ist dran; {currency}gewonnen! Zeit: {seconds}s"
            )
        );
        assertTrue(
            SyntaxTokens.introducedPlaceholderWordJoins(
                "{name}'s betting circle",
                "{name}s Wettkreis"
            ).isEmpty()
        );
    }

    @Test
    void catalogLoadsQuotedYamlValues() throws IOException {
        Path catalogPath = temporaryDirectory.resolve("fr_FR.yml");
        Files.writeString(
            catalogPath,
            "_meta:\n"
                + "  locale: fr_FR\n"
                + "  name: Français\n"
                + "common:\n"
                + "  message: \"&aBonjour {player}\\n\"\n",
            StandardCharsets.UTF_8
        );

        Catalog catalog = Catalog.load(catalogPath);

        assertEquals("fr_FR", catalog.value("_meta.locale"));
        assertEquals("Français", catalog.value("_meta.name"));
        assertEquals("&aBonjour {player}\n", catalog.value("common.message"));
    }

    @Test
    void protectedLiteralCountsAreCaseSensitiveAndSubstringBased() {
        assertEquals(
            Map.of("Vault", 2, "NCCasino", 1),
            SyntaxTokens.protectedLiteralCounts(
                "&cVault not found. Install Vault to use with NCCasino."
            )
        );
        assertEquals(
            Map.of("NCCASINO", 1),
            SyntaxTokens.protectedLiteralCounts("&aNCCASINO configuration reloaded successfully.")
        );
        assertTrue(SyntaxTokens.protectedLiteralCounts("&aOrdinary text.").isEmpty());
    }

    @Test
    void protectedLiteralDetectionSurvivesFormattingCodeAdjacency() {
        assertEquals(Map.of("Vault", 1), SyntaxTokens.protectedLiteralCounts("&cVault not found."));
    }

    @Test
    void formattingCodeFrequenciesCountRegardlessOfPosition() {
        assertEquals(
            Map.of("&o", 2),
            SyntaxTokens.formattingCodeFrequencies("&o{player}&o's turn")
        );
        assertEquals(
            Map.of("&o", 1),
            SyntaxTokens.formattingCodeFrequencies("&oTurno de {player}")
        );
    }

    @Test
    void ensureSyntaxMatchesRejectsAnAlteredProtectedLiteral() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> LocalizationCli.ensureSyntaxMatches(
                "Edit Max Chain Rounds (PvE)",
                "Modifier le Nombre Maximal de Manches en Chaîne (JcE)",
                "fr_FR:coin-flip-settings.edit-max-chain"
            )
        );
        assertTrue(exception.getMessage().contains("protected literal"));
    }

    @Test
    void residueHeuristicFlagsLongByteIdenticalValuesOnly() {
        assertTrue(
            LocalizationCli.looksLikeUntranslatedResidue(
                "&aNCCASINO configuration reloaded successfully.",
                "&aNCCASINO configuration reloaded successfully."
            )
        );
        assertFalse(LocalizationCli.looksLikeUntranslatedResidue("Blackjack", "Blackjack"));
        assertFalse(LocalizationCli.looksLikeUntranslatedResidue("&aWager: {amount}", "&aWager: {amount}"));
    }

    @Test
    void mechanicalWarningsSurfaceFormattingDropsAndResidueWithoutFailing() throws IOException {
        Path englishPath = temporaryDirectory.resolve("en_US.yml");
        Files.writeString(
            englishPath,
            "_meta:\n  locale: en_US\n  name: English\n"
                + "coin-flip:\n  player-turn: \"&o{player}&o's turn\"\n"
                + "commands:\n  reload-success: \"&aNCCASINO configuration reloaded successfully.\"\n",
            StandardCharsets.UTF_8
        );
        Path targetPath = temporaryDirectory.resolve("fr_FR.yml");
        Files.writeString(
            targetPath,
            "_meta:\n  locale: fr_FR\n  name: Français\n"
                + "coin-flip:\n  player-turn: \"&oTour de {player}\"\n"
                + "commands:\n  reload-success: \"&aNCCASINO configuration reloaded successfully.\"\n",
            StandardCharsets.UTF_8
        );

        List<String> warnings = LocalizationCli.mechanicalWarnings(
            Catalog.load(englishPath),
            Catalog.load(targetPath),
            "fr_FR"
        );

        assertEquals(2, warnings.size());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("coin-flip.player-turn")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("commands.reload-success")));
    }

    @Test
    void commandTokensAndParserSentinelsAreProtected() {
        IllegalStateException command = assertThrows(
            IllegalStateException.class,
            () -> LocalizationCli.ensureSyntaxMatches(
                "&bUsage: /ncc create &e<name>",
                "&bUso: /ncc crear &e<nombre>",
                "es_ES:commands.usage-create"
            )
        );
        IllegalStateException sentinel = assertThrows(
            IllegalStateException.class,
            () -> LocalizationCli.ensureSyntaxMatches(
                "&aType -1 for unlimited.",
                "&aEscribe un número para ilimitado.",
                "es_ES:blackjack-settings.prompt-max-hands"
            )
        );
        assertTrue(command.getMessage().contains("protected syntax"));
        assertTrue(sentinel.getMessage().contains("protected syntax"));
    }

    @Test
    void syntaxOrderChecksRejectReorderedTokensAndFormatting() {
        assertThrows(
            IllegalStateException.class,
            () -> LocalizationCli.ensureSyntaxMatches(
                "&a{player} used {amount}",
                "&a{amount} used {player}",
                "es_ES:example.reordered"
            )
        );
        assertThrows(
            IllegalStateException.class,
            () -> LocalizationCli.ensureNewSyntaxOrderMatches(
                "&a{player} &ewon",
                "&e{player} &awon",
                "es_ES:example.reordered-formatting"
            )
        );
    }

    @Test
    void productionLocaleSelectionIncludesCanonicalEnglish() {
        Map<String, LocaleSpec> registry = new LinkedHashMap<>();
        registry.put("en_US", new LocaleSpec("en_US", "English"));
        registry.put("de_DE", new LocaleSpec("de_DE", "Deutsch"));

        assertEquals(
            List.of(registry.get("en_US")),
            LocalizationCli.selectLocales(registry, Set.of("en_US"))
        );
        assertEquals(2, LocalizationCli.selectLocales(registry, Set.of()).size());
    }

    @Test
    void strictCandidateValidationRejectsFormattingReordering() throws IOException {
        Path englishPath = temporaryDirectory.resolve("en_US.yml");
        Files.writeString(
            englishPath,
            "_meta:\n  locale: en_US\n  name: English\n"
                + "example:\n  message: \"&a{player} &ewon\"\n",
            StandardCharsets.UTF_8
        );
        Path candidatePath = temporaryDirectory.resolve("es_ES.yml");
        Files.writeString(
            candidatePath,
            "_meta:\n  locale: es_ES\n  name: Español\n"
                + "example:\n  message: \"&e{player} &aganó\"\n",
            StandardCharsets.UTF_8
        );

        Catalog english = Catalog.load(englishPath);
        Catalog candidate = Catalog.load(candidatePath);
        LocaleSpec spanish = new LocaleSpec("es_ES", "Español");

        assertTrue(LocalizationCli.validate(english, candidate, spanish, false).isEmpty());
        assertTrue(
            LocalizationCli.validate(english, candidate, spanish, true).stream()
                .anyMatch(problem -> problem.contains("including formatting codes"))
        );
    }
}
