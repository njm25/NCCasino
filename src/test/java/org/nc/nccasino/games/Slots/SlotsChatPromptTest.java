package org.nc.nccasino.games.Slots;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One in-flight chat prompt's own state: its deadline, its return view, and
 * the duplicate-name overwrite sub-state that deliberately does <em>not</em>
 * restart that deadline.
 *
 * <p>The scheduling half of the engine ({@link SlotsChatPromptService}) needs
 * a live Bukkit server, so what is pinned here is the part every prompt type
 * shares and that a rejected, retried, or superseded answer must not disturb.
 */
class SlotsChatPromptTest {

    /** Records every callback so a test can assert what a sequence actually did. */
    private static final class RecordingHandler implements SlotsChatPrompt.Handler {
        final List<String> calls = new ArrayList<>();
        SlotsChatPrompt.Outcome next = SlotsChatPrompt.Outcome.ACCEPTED;
        boolean valid = true;

        @Override
        public boolean isSessionValid() {
            return valid;
        }

        @Override
        public SlotsChatPrompt.Outcome submit(String input) {
            calls.add("submit:" + input);
            return next;
        }

        @Override
        public void accepted() {
            calls.add("accepted");
        }

        @Override
        public void cancelled() {
            calls.add("cancelled");
        }

        @Override
        public void timedOut() {
            calls.add("timedOut");
        }

        @Override
        public void ended(SlotsChatPrompt.EndReason reason) {
            calls.add("ended:" + reason);
        }
    }

    private static SlotsChatPrompt prompt(SlotsChatPrompt.Type type, SlotsUiView returnView, long deadline) {
        return new SlotsChatPrompt(
            UUID.randomUUID(), type, deadline, returnView, null, 1L, new RecordingHandler());
    }

    @Test
    void everyPromptTypeHasItsOwnInstructionKey() {
        assertEquals("slots.prompt-profile-name",
            prompt(SlotsChatPrompt.Type.PROFILE_NAME, SlotsUiView.GAME, 0L).instructionKey());
        assertEquals("slots.prompt-spin-limit",
            prompt(SlotsChatPrompt.Type.SPIN_LIMIT, SlotsUiView.AUTO_SETTINGS, 0L).instructionKey());
        assertEquals("slots.prompt-big-win-multiplier",
            prompt(SlotsChatPrompt.Type.BIG_WIN_MULTIPLIER, SlotsUiView.AUTO_SETTINGS, 0L).instructionKey());
        assertEquals("slots.prompt-profit-target",
            prompt(SlotsChatPrompt.Type.PROFIT_TARGET, SlotsUiView.AUTO_SETTINGS, 0L).instructionKey());
        assertEquals("slots.prompt-loss-limit",
            prompt(SlotsChatPrompt.Type.LOSS_LIMIT, SlotsUiView.AUTO_SETTINGS, 0L).instructionKey());
    }

    @Test
    void instructionKeysAreDistinctAndHyphenated() {
        List<String> seen = new ArrayList<>();
        for (SlotsChatPrompt.Type type : SlotsChatPrompt.Type.values()) {
            String key = prompt(type, SlotsUiView.GAME, 0L).instructionKey();
            assertTrue(key.startsWith("slots.prompt-"), key);
            assertFalse(key.contains("_"), key);
            assertFalse(seen.contains(key), "duplicate instruction key " + key);
            seen.add(key);
        }
        assertEquals(SlotsChatPrompt.Type.values().length, seen.size());
    }

    @Test
    void profileNamingReturnsToTheGameViewAndAutoSettingsReturnsToItsMenu() {
        assertEquals(SlotsUiView.GAME,
            prompt(SlotsChatPrompt.Type.PROFILE_NAME, SlotsUiView.GAME, 0L).returnView());
        assertEquals(SlotsUiView.AUTO_SETTINGS,
            prompt(SlotsChatPrompt.Type.LOSS_LIMIT, SlotsUiView.AUTO_SETTINGS, 0L).returnView());
    }

    @Test
    void aMissingReturnViewFallsBackToTheGameView() {
        assertEquals(SlotsUiView.GAME,
            prompt(SlotsChatPrompt.Type.SPIN_LIMIT, null, 0L).returnView());
    }

    @Test
    void theDeadlineIsSixtySecondsFromWhenThePromptOpens() {
        long before = System.currentTimeMillis();
        long deadline = SlotsChatPromptService.deadlineFromNow();
        long after = System.currentTimeMillis();
        assertEquals(60L, SlotsChatPromptService.TIMEOUT_SECONDS);
        assertTrue(deadline >= before + 60_000L, "deadline must be at least 60s out");
        assertTrue(deadline <= after + 60_000L, "deadline must not be more than 60s out");
    }

    @Test
    void remainingSecondsCountsDownAndNeverGoesNegative() {
        long now = 1_000_000L;
        SlotsChatPrompt open = prompt(SlotsChatPrompt.Type.SPIN_LIMIT, SlotsUiView.AUTO_SETTINGS, now + 60_000L);
        assertEquals(60L, open.remainingSeconds(now));
        assertEquals(45L, open.remainingSeconds(now + 15_000L));
        assertEquals(1L, open.remainingSeconds(now + 59_500L));
        assertEquals(0L, open.remainingSeconds(now + 60_000L));
        assertEquals(0L, open.remainingSeconds(now + 600_000L));
    }

    @Test
    void expiryIsInclusiveOfTheDeadlineItself() {
        long now = 5_000L;
        SlotsChatPrompt open = prompt(SlotsChatPrompt.Type.PROFILE_NAME, SlotsUiView.GAME, now + 100L);
        assertFalse(open.isExpired(now));
        assertFalse(open.isExpired(now + 99L));
        assertTrue(open.isExpired(now + 100L));
        assertTrue(open.isExpired(now + 5_000L));
    }

    @Test
    void aDuplicateNameConfirmationNeverRestartsTheDeadline() {
        long now = 2_000_000L;
        SlotsChatPrompt naming =
            prompt(SlotsChatPrompt.Type.PROFILE_NAME, SlotsUiView.GAME, now + 60_000L);
        assertNull(naming.pendingOverwriteName());

        // 40 seconds in, the player submits a name that already exists.
        naming.awaitOverwriteConfirmation("High Roller");
        assertEquals("High Roller", naming.pendingOverwriteName());
        assertEquals(20L, naming.remainingSeconds(now + 40_000L),
            "the original 60-second deadline must still be the one running");
        assertEquals(now + 60_000L, naming.deadlineMillis());

        naming.clearOverwriteConfirmation();
        assertNull(naming.pendingOverwriteName());
        assertEquals(20L, naming.remainingSeconds(now + 40_000L));
    }

    @Test
    void aPromptCarriesItsOwningSessionAndGenerationForStalenessChecks() {
        UUID player = UUID.randomUUID();
        RecordingHandler handler = new RecordingHandler();
        SlotsChatPrompt open = new SlotsChatPrompt(
            player, SlotsChatPrompt.Type.PROFIT_TARGET, 0L, SlotsUiView.AUTO_SETTINGS,
            null, 7L, handler);
        assertEquals(player, open.playerId());
        assertEquals(SlotsChatPrompt.Type.PROFIT_TARGET, open.type());
        assertEquals(7L, open.sessionGeneration());
        assertNull(open.session());
        assertEquals(handler, open.handler());
    }

    @Test
    void aPromptWithoutAPlayerTypeOrHandlerIsRejectedOutright() {
        RecordingHandler handler = new RecordingHandler();
        assertThrows(IllegalArgumentException.class, () -> new SlotsChatPrompt(
            null, SlotsChatPrompt.Type.SPIN_LIMIT, 0L, SlotsUiView.GAME, null, 1L, handler));
        assertThrows(IllegalArgumentException.class, () -> new SlotsChatPrompt(
            UUID.randomUUID(), null, 0L, SlotsUiView.GAME, null, 1L, handler));
        assertThrows(IllegalArgumentException.class, () -> new SlotsChatPrompt(
            UUID.randomUUID(), SlotsChatPrompt.Type.SPIN_LIMIT, 0L, SlotsUiView.GAME, null, 1L, null));
    }

    @Test
    void everyEndReasonIsDistinctSoATeardownPathCanNeverBeConfusedForAnother() {
        // Timeout terminates the session; another game cancels and closes it;
        // superseded means a newer prompt already owns the suspension. They
        // must never collapse into one another.
        assertEquals(5, SlotsChatPrompt.EndReason.values().length);
        assertEquals(SlotsChatPrompt.EndReason.TIMED_OUT,
            SlotsChatPrompt.EndReason.valueOf("TIMED_OUT"));
        assertEquals(SlotsChatPrompt.EndReason.ANOTHER_GAME_OPENED,
            SlotsChatPrompt.EndReason.valueOf("ANOTHER_GAME_OPENED"));
        assertEquals(SlotsChatPrompt.EndReason.DISCONNECTED,
            SlotsChatPrompt.EndReason.valueOf("DISCONNECTED"));
        assertEquals(SlotsChatPrompt.EndReason.SESSION_ENDED,
            SlotsChatPrompt.EndReason.valueOf("SESSION_ENDED"));
        assertEquals(SlotsChatPrompt.EndReason.SUPERSEDED,
            SlotsChatPrompt.EndReason.valueOf("SUPERSEDED"));
    }

    @Test
    void aRetryKeepsThePromptOpenWhileAcceptOrCancelFinishesIt() {
        RecordingHandler handler = new RecordingHandler();
        SlotsChatPrompt open = new SlotsChatPrompt(
            UUID.randomUUID(), SlotsChatPrompt.Type.LOSS_LIMIT, Long.MAX_VALUE,
            SlotsUiView.AUTO_SETTINGS, null, 1L, handler);

        handler.next = SlotsChatPrompt.Outcome.RETRY;
        assertEquals(SlotsChatPrompt.Outcome.RETRY, open.handler().submit("nonsense"));
        handler.next = SlotsChatPrompt.Outcome.ACCEPTED;
        assertEquals(SlotsChatPrompt.Outcome.ACCEPTED, open.handler().submit("250"));
        open.handler().accepted();
        assertEquals(List.of("submit:nonsense", "submit:250", "accepted"), handler.calls);
    }
}
