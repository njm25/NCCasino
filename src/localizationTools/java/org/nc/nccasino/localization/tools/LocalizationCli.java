package org.nc.nccasino.localization.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nc.nccasino.localization.LocaleRegistry;
import org.nc.nccasino.localization.LocaleRegistry.LocaleSpec;
import org.nc.nccasino.localization.LocalizationService;

/** Offline, read-only validator for bundled localization catalogs. */
public final class LocalizationCli {
    private static final Path LANG_DIRECTORY = Path.of("src/main/resources/lang");
    private static final Path REGISTRY = LANG_DIRECTORY.resolve("locales.yml");

    private LocalizationCli() {
    }

    public static void main(String[] arguments) throws Exception {
        Options options = Options.parse(arguments);
        Catalog english = Catalog.load(LANG_DIRECTORY.resolve(LocalizationService.ENGLISH + ".yml"));
        if (options.command().equals("check-candidate")) {
            checkCandidate(english, options.candidate(), options.candidateLocale());
            return;
        }
        Map<String, LocaleSpec> registry;
        try (InputStream stream = Files.newInputStream(REGISTRY)) {
            registry = LocaleRegistry.load(stream);
        }
        List<LocaleSpec> locales = selectLocales(registry, options.locales());
        check(english, locales);
    }

    private static void checkCandidate(Catalog english, Path candidatePath, String locale) throws IOException {
        Catalog candidate = Catalog.load(candidatePath);
        String nativeName = candidate.value("_meta.name");
        LocaleSpec candidateSpec = new LocaleSpec(locale, nativeName == null ? "" : nativeName);
        List<String> problems = validate(english, candidate, candidateSpec, true);
        if (nativeName == null || nativeName.isBlank()) {
            problems.add(locale + " is missing _meta.name");
        }
        if (!problems.isEmpty()) {
            problems.forEach(problem -> System.err.println("ERROR: " + problem));
            throw new IllegalStateException(
                "Candidate localization validation failed with " + problems.size() + " problem(s)."
            );
        }
        List<String> warnings = locale.equals(LocalizationService.ENGLISH)
            ? List.of()
            : mechanicalWarnings(english, candidate, locale);
        System.out.println(locale + ": CANDIDATE OK (" + english.translatableValues().size() + " entries)");
        warnings.forEach(warning -> System.out.println("WARNING: " + warning));
    }

    private static void check(Catalog english, List<LocaleSpec> locales) throws IOException {
        int failures = 0;
        int warnings = 0;
        for (LocaleSpec locale : locales) {
            Catalog catalog = Catalog.load(LANG_DIRECTORY.resolve(locale.id() + ".yml"));
            List<String> problems = validate(english, catalog, locale, false);
            List<String> catalogWarnings = locale.id().equals(LocalizationService.ENGLISH)
                ? List.of()
                : mechanicalWarnings(english, catalog, locale.id());
            if (problems.isEmpty()) {
                System.out.println(locale.id() + ": OK (" + english.translatableValues().size() + " entries)");
            } else {
                failures += problems.size();
                problems.forEach(problem -> System.err.println("ERROR: " + problem));
            }
            warnings += catalogWarnings.size();
            catalogWarnings.forEach(warning -> System.out.println("WARNING: " + warning));
        }
        if (warnings > 0) {
            System.out.println(
                warnings + " non-fatal warning(s) -- see TRANSLATION_GUIDE.md sections E/F. "
                    + "These do not fail the build; review and fix them deliberately."
            );
        }
        if (failures > 0) {
            throw new IllegalStateException("Localization validation failed with " + failures + " problem(s).");
        }
    }

    static List<String> mechanicalWarnings(Catalog english, Catalog translated, String locale) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> source = english.translatableValues();
        Map<String, String> target = translated.translatableValues();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey();
            String englishValue = entry.getValue();
            String targetValue = target.get(key);
            if (targetValue == null || targetValue.isBlank()) {
                continue;
            }
            Map<String, Integer> expectedCodes = SyntaxTokens.formattingCodeFrequencies(englishValue);
            Map<String, Integer> actualCodes = SyntaxTokens.formattingCodeFrequencies(targetValue);
            if (!expectedCodes.equals(actualCodes)) {
                warnings.add(
                    locale + ":" + key + " has a different formatting-code count than English; expected "
                        + expectedCodes + " but found " + actualCodes
                );
            }
            if (looksLikeUntranslatedResidue(englishValue, targetValue)) {
                warnings.add(
                    locale + ":" + key + " is byte-identical to the English source and long enough that "
                        + "this may be untranslated residue rather than an intentionally shared value"
                );
            }
        }
        return warnings;
    }

    static boolean looksLikeUntranslatedResidue(String englishValue, String targetValue) {
        if (!englishValue.equals(targetValue)) {
            return false;
        }
        String withoutProtectedSyntax = englishValue.replaceAll(
            "\\{[A-Za-z][A-Za-z0-9_-]*}|(?i:&[0-9A-FK-OR])",
            " "
        );
        return withoutProtectedSyntax.trim().split("\\s+").length >= 4;
    }

    static List<String> validate(
        Catalog english,
        Catalog translated,
        LocaleSpec localeSpec,
        boolean strictFormattingOrder
    ) {
        String locale = localeSpec.id();
        List<String> problems = new ArrayList<>();
        Map<String, String> source = english.translatableValues();
        Map<String, String> target = translated.translatableValues();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String value = target.get(entry.getKey());
            if (value == null || value.isBlank()) {
                problems.add(locale + " is missing " + entry.getKey());
                continue;
            }
            try {
                ensureSyntaxMatches(entry.getValue(), value, locale + ":" + entry.getKey());
                if (strictFormattingOrder) {
                    ensureNewSyntaxOrderMatches(entry.getValue(), value, locale + ":" + entry.getKey());
                }
            } catch (IllegalStateException exception) {
                problems.add(exception.getMessage());
            }
        }
        for (String key : target.keySet()) {
            if (!source.containsKey(key)) {
                problems.add(locale + " contains obsolete key " + key);
            }
        }
        if (!locale.equals(translated.value("_meta.locale"))) {
            problems.add(locale + " has incorrect _meta.locale");
        }
        if (!localeSpec.name().equals(translated.value("_meta.name"))) {
            problems.add(locale + " has incorrect _meta.name");
        }
        return problems;
    }

    static void ensureSyntaxMatches(String source, String translated, String context) {
        List<String> expectedOrder = SyntaxTokens.structural(source);
        List<String> actualOrder = SyntaxTokens.structural(translated);
        Map<String, Integer> expected = frequencies(expectedOrder);
        Map<String, Integer> actual = frequencies(actualOrder);
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                context + " changed protected syntax; expected " + expected + " but found " + actual
            );
        }
        if (!expectedOrder.equals(actualOrder)) {
            throw new IllegalStateException(
                context + " reordered protected syntax (translation-guide §E) relative to the English "
                    + "source; expected " + expectedOrder + " but found " + actualOrder
            );
        }
        List<String> joinedWords = SyntaxTokens.introducedPlaceholderWordJoins(source, translated);
        if (!joinedWords.isEmpty()) {
            throw new IllegalStateException(
                context + " joined placeholders to translated words: " + joinedWords
            );
        }
        Map<String, Integer> expectedLiterals = SyntaxTokens.protectedLiteralCounts(source);
        Map<String, Integer> actualLiterals = SyntaxTokens.protectedLiteralCounts(translated);
        if (!expectedLiterals.equals(actualLiterals)) {
            throw new IllegalStateException(
                context + " changed a protected literal (translation-guide §D, Class B); expected "
                    + expectedLiterals + " but found " + actualLiterals
            );
        }
    }

    static void ensureNewSyntaxOrderMatches(String source, String translated, String context) {
        List<String> expectedOrder = SyntaxTokens.find(source);
        List<String> actualOrder = SyntaxTokens.find(translated);
        if (!expectedOrder.equals(actualOrder)) {
            throw new IllegalStateException(
                context + " changed the order or count of protected syntax including formatting codes "
                    + "(translation-guide §D/§E); expected " + expectedOrder + " but found " + actualOrder
            );
        }
    }

    private static Map<String, Integer> frequencies(List<String> values) {
        Map<String, Integer> result = new HashMap<>();
        for (String value : values) {
            result.merge(value, 1, Integer::sum);
        }
        return result;
    }

    static List<LocaleSpec> selectLocales(
        Map<String, LocaleSpec> registry,
        Set<String> selected
    ) {
        for (String id : selected) {
            if (!registry.containsKey(id)) {
                throw new IllegalArgumentException("Unknown locale: " + id);
            }
        }
        return registry.values().stream()
            .filter(locale -> selected.isEmpty() || selected.contains(locale.id()))
            .toList();
    }

    private record Options(String command, Set<String> locales, String candidateLocale, Path candidate) {
        static Options parse(String[] arguments) {
            if (arguments.length == 0) {
                throw new IllegalArgumentException("Expected check or check-candidate command.");
            }
            String command = arguments[0].toLowerCase();
            if (!command.equals("check") && !command.equals("check-candidate")) {
                throw new IllegalArgumentException("Expected check or check-candidate command.");
            }
            Set<String> locales = new LinkedHashSet<>();
            String candidateLocale = null;
            Path candidate = null;
            for (int index = 1; index < arguments.length; index++) {
                if (index + 1 >= arguments.length) {
                    throw new IllegalArgumentException("Unknown or incomplete option " + arguments[index]);
                }
                String option = arguments[index];
                String value = arguments[++index];
                switch (option) {
                    case "--locales" -> {
                        for (String locale : value.split(",")) {
                            if (!locale.isBlank()) {
                                locales.add(locale.trim());
                            }
                        }
                    }
                    case "--locale" -> candidateLocale = value;
                    case "--candidate" -> candidate = Path.of(value);
                    default -> throw new IllegalArgumentException("Unknown option " + option);
                }
            }
            if (command.equals("check") && (candidateLocale != null || candidate != null)) {
                throw new IllegalArgumentException("Candidate options require check-candidate.");
            }
            if (command.equals("check-candidate")
                && (!locales.isEmpty() || candidateLocale == null || candidateLocale.isBlank() || candidate == null)) {
                throw new IllegalArgumentException(
                    "check-candidate requires --locale <id> and --candidate <path>."
                );
            }
            return new Options(command, Set.copyOf(locales), candidateLocale, candidate);
        }
    }
}
