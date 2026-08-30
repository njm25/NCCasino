package org.nc.nccasino.localization.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SyntaxTokens {
    /**
     * Class A executable command/subcommand tokens (translation-guide §D):
     * the `/ncc` prefix and the exact subcommand words the parser matches on
     * (`CreateCommand`/`ListCommand`/`DeleteCommand`/`ReloadCommand`/help).
     * Matched case-sensitively since these are parsed literals, not prose.
     */
    private static final Pattern COMMAND_TOKEN = Pattern.compile(
        "/ncc(?:\\s+(?:help|create|delete|list|reload))?"
    );
    /**
     * Class A parser sentinel (translation-guide §D/§J): the standalone
     * {@code -1} "unlimited" value read back by input parsers. Bounded so it
     * doesn't swallow part of a different number (e.g. {@code -10}) or an
     * unrelated hyphenated token.
     */
    private static final Pattern PARSER_SENTINEL = Pattern.compile("(?<![\\w-])-1(?!\\d)");
    private static final Pattern FORMATTING_CODE = Pattern.compile("(?i:&[0-9A-FK-OR])");
    private static final Pattern PROTECTED = Pattern.compile(
        "\\{[A-Za-z][A-Za-z0-9_-]*}|" + FORMATTING_CODE.pattern() + "|" + COMMAND_TOKEN.pattern() + "|"
            + PARSER_SENTINEL.pattern() + "|\\n"
    );
    /**
     * Placeholder/command/sentinel/newline order, deliberately excluding
     * formatting codes. Used for the hard order check that also runs against
     * every existing production value (via {@code check}/{@code validate}):
     * unlike placeholders/commands/the sentinel, existing catalogs are known
     * to already carry pre-existing formatting-code count drops (translation-
     * guide §E/§F, e.g. {@code coin-flip.player-turn}), which the guide
     * deliberately keeps as a non-fatal warning rather than a build failure.
     * {@link #find(String)} (the full {@link #PROTECTED} pattern, formatting
     * codes included) supports strict review of newly edited values through
     * {@code LocalizationCli.ensureNewSyntaxOrderMatches}.
     */
    private static final Pattern STRUCTURAL = Pattern.compile(
        "\\{[A-Za-z][A-Za-z0-9_-]*}|" + COMMAND_TOKEN.pattern() + "|" + PARSER_SENTINEL.pattern() + "|\\n"
    );
    private static final Pattern PLACEHOLDER_WORD_JOIN = Pattern.compile(
        "(\\{[A-Za-z][A-Za-z0-9_-]*})(\\p{L}+)"
    );

    /**
     * Class B protected literals (translation-guide §D): kept byte-exact in
     * every locale even though they read as ordinary English words. Casing
     * variants ({@code NCCasino} vs {@code NCCASINO}) are distinct literals
     * on purpose -- the guide requires preserving the source's exact casing.
     */
    private static final List<String> PROTECTED_LITERALS = List.of(
        "NCCasino", "NCCASINO", "Vault", "VAULT", "PvP", "PvE"
    );

    private SyntaxTokens() {
    }

    static List<String> find(String value) {
        return find(value, PROTECTED);
    }

    static List<String> structural(String value) {
        return find(value, STRUCTURAL);
    }

    private static List<String> find(String value, Pattern pattern) {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    static List<String> introducedPlaceholderWordJoins(String source, String translated) {
        List<String> result = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_WORD_JOIN.matcher(translated);
        while (matcher.find()) {
            String joined = matcher.group();
            if (source.contains(joined)) {
                continue;
            }
            if (matcher.group(2).equals("s") && source.contains(matcher.group(1) + "'s")) {
                continue;
            }
            result.add(joined);
        }
        return result;
    }

    /**
     * Counts of each Minecraft legacy formatting code ({@code &a}, {@code &o},
     * ...) present in {@code value}, ignoring where in the string they sit.
     * This is a frequency check, not an order check: a translation is free to
     * move a formatting code relative to surrounding text for grammar, but
     * must not drop or add an occurrence of it. Used as a non-fatal warning
     * signal (translation-guide §E) rather than a hard failure, since
     * existing catalogs may already carry pre-existing drops that are out of
     * scope for a given task to fix.
     */
    static Map<String, Integer> formattingCodeFrequencies(String value) {
        return frequencies(find(value, FORMATTING_CODE));
    }

    /**
     * Counts of each Class B protected literal (translation-guide §D) present
     * in {@code value}, case-sensitively, as a plain substring count (not a
     * word-boundary regex -- a Minecraft formatting code like {@code &c}
     * immediately preceding the first word, e.g. {@code "&cVault not
     * found..."}, leaves no word boundary between the code's letter and the
     * literal, which would otherwise cause a real occurrence to go
     * undetected).
     */
    static Map<String, Integer> protectedLiteralCounts(String value) {
        Map<String, Integer> counts = new HashMap<>();
        for (String literal : PROTECTED_LITERALS) {
            int occurrences = countOccurrences(value, literal);
            if (occurrences > 0) {
                counts.put(literal, occurrences);
            }
        }
        return counts;
    }

    private static int countOccurrences(String value, String literal) {
        int count = 0;
        int index = value.indexOf(literal);
        while (index >= 0) {
            count++;
            index = value.indexOf(literal, index + literal.length());
        }
        return count;
    }

    static Map<String, Integer> frequencies(List<String> values) {
        Map<String, Integer> result = new HashMap<>();
        for (String value : values) {
            result.merge(value, 1, Integer::sum);
        }
        return result;
    }

}
