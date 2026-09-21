package org.nc.nccasino.commands;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression guard for the permission bug where {@code nccasino.use}
 * (formerly {@code default: op}) gated the entire {@code /ncc} command ahead
 * of {@code nccasino.commands.claim} ({@code default: true}) ever being
 * checked -- see {@code CommandExecution.onCommand}, which refuses on {@code
 * nccasino.use} before any subcommand-specific permission is even looked at,
 * and Bukkit's own dispatcher, which enforces the {@code commands.ncc.permission}
 * key in {@code plugin.yml} before {@code onCommand} is called at all. A
 * normal (non-op, ungranted) player could therefore never reach {@code /ncc
 * claim} despite its own node being player-accessible.
 *
 * <p>Reads {@code plugin.yml} as plain text rather than through Bukkit's
 * {@link org.bukkit.configuration.file.YamlConfiguration}, since permission
 * node names themselves contain dots ({@code nccasino.commands.claim}) that
 * a path-based getter would otherwise split into nested keys -- the same
 * reason {@code DealerBudgetStore} stores reservation ids as a list instead
 * of a map.
 */
class PluginPermissionsTest {

    @Test
    void nccasinoUseDefaultsToTrueSoNormalPlayersCanReachSubcommandDispatch() throws Exception {
        assertEquals("true", defaultFor("nccasino.use"),
            "nccasino.use must default to true, or CommandExecution's umbrella check"
                + " (and Bukkit's own commands.ncc.permission gate) blocks every /ncc"
                + " subcommand for a normal player regardless of that subcommand's own node");
    }

    @Test
    void claimStillDefaultsToTrue() throws Exception {
        assertEquals("true", defaultFor("nccasino.commands.claim"));
    }

    @Test
    void adminSubcommandsRemainOpOnlyAfterTheFix() throws Exception {
        for (String node : new String[] {
            "nccasino.commands.create", "nccasino.commands.delete",
            "nccasino.commands.reload", "nccasino.commands.list", "nccasino.commands.help"
        }) {
            assertEquals("op", defaultFor(node),
                node + " must remain op-only -- raising nccasino.use to default:true must not"
                    + " grant any create/delete/reload/admin capability to a normal player");
        }
    }

    private static String readPluginYml() throws Exception {
        try (InputStream in = PluginPermissionsTest.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            if (in == null) {
                throw new IllegalStateException("plugin.yml not found on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The {@code default:} value declared directly under the named permission node's block. */
    private static String defaultFor(String node) throws Exception {
        String yaml = readPluginYml();
        Matcher matcher = Pattern.compile(
            Pattern.quote("  " + node + ":") + "\\s*\\R(?:.*\\R)*?\\s*default:\\s*(\\S+)"
        ).matcher(yaml);
        if (!matcher.find()) {
            throw new AssertionError("No permission block with a default: line found for " + node);
        }
        return matcher.group(1);
    }
}
