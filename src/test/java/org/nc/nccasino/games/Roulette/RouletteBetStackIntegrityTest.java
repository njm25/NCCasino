package org.nc.nccasino.games.Roulette;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every Roulette bet on a player's stack is one they just paid for.
 *
 * <p>Undo All refunds the whole stack, so a bet pushed without a matching debit
 * is free money. 1.4.9 shipped one: a hidden 16-bet pattern pushed 200 on Red
 * and 200 on Black, which Undo All then paid out. Building a BettingTable needs a
 * live inventory, so this guards the source: the single push is the paid bet.
 */
class RouletteBetStackIntegrityTest {

    private static final Pattern PUSH = Pattern.compile("\\.push\\(([^;]*)\\);");

    @Test
    void theOnlyBetPushedIsThePaidBetType() throws IOException {
        Path root = Paths.get("src/main/java/org/nc/nccasino/games/Roulette");
        List<String> pushes;
        try (Stream<Path> files = Files.list(root)) {
            pushes = files.filter(p -> p.toString().endsWith(".java"))
                .flatMap(RouletteBetStackIntegrityTest::pushesIn)
                .toList();
        }
        assertEquals(List.of("new Pair<>(betType, (int) wagerAmount)"), pushes,
            "a Roulette bet must only ever enter the stack where its wager is taken");
    }

    @Test
    void noBetIsBuiltFromALiteralBetName() throws IOException {
        String source = Files.readString(Paths.get("src/main/java/org/nc/nccasino/games/Roulette/BettingTable.java"));
        assertFalse(source.contains("new Pair<>(\""), "a bet built from a literal name was never paid for");
    }

    private static Stream<String> pushesIn(Path file) {
        try {
            Matcher m = PUSH.matcher(Files.readString(file));
            Stream.Builder<String> found = Stream.builder();
            while (m.find()) {
                found.add(m.group(1).trim());
            }
            return found.build();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
