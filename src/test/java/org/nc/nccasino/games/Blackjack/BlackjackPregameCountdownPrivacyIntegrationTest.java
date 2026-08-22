package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.currency.ChipSlots;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Controller-level regression coverage for making the shared pregame
 * countdown ("game starts in") private per viewer, like the turn-timer and
 * insurance countdowns already are: there remains exactly one canonical
 * countdown/task ({@code countdownSecondsRemaining}), but each seat's clock
 * renders only into that seat's own owner's view -- never into any other
 * viewer's copy of that row, and never at all for an unseated spectator.
 */
class BlackjackPregameCountdownPrivacyIntegrationTest {

    private static final Material CLOCK = Material.CLOCK;
    private static final Material BACKGROUND = Material.GREEN_STAINED_GLASS_PANE;

    private static ItemStack countdownSlotItem(BlackjackControllerTestSupport.Harness h, Player viewer, int seatSlot) {
        return h.inventory.getOrCreateView(viewer).getItem(BlackjackSlotLayout.pregameCountdownSlot(seatSlot));
    }

    /**
     * {@code plugin.getTimer(...)} is never stubbed by the shared harness
     * (Mockito's implicit default is {@code 0}), so without this every
     * table's countdown would start already-expired and
     * {@code startCountdownTimer}'s very first tick would immediately fall
     * through to {@code beginStartTransition} instead of ever actually
     * showing a countdown -- exactly the phase these tests need to observe.
     */
    private static void stubCountdownDuration(BlackjackControllerTestSupport.Harness h, int seconds) {
        when(h.plugin.getTimer(anyString())).thenReturn(seconds);
    }

    /**
     * Commits a real 10.0 wager through the actual chip-select + bet-spot
     * click path -- unlike {@code commitWagerForTest} (which calls the
     * underlying transaction directly), only this real click path also
     * starts the shared countdown task ({@code startCountdownTimer}, gated
     * behind the click handlers, not {@code commitWager} itself).
     */
    private static void placeRealBet(BlackjackControllerTestSupport.Harness h, Player player, int seatSlot) {
        h.click(player, ChipSlots.FIRST_SLOT + 2); // the fixed 10.0 chip
        h.click(player, BlackjackSlotLayout.betSlipSlot(seatSlot));
    }

    @Test
    void eachSeatedViewerSeesTheClockOnlyInTheirOwnRow() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            stubCountdownDuration(h, 30);
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            int bobSeat = BlackjackSlotLayout.SEAT_SLOTS[1];
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, aliceSeat);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, bobSeat);

            placeRealBet(h, alice, aliceSeat); // starts the shared countdown
            placeRealBet(h, bob, bobSeat);
            h.scheduler.advance(20); // one countdown tick

            assertEquals(CLOCK, countdownSlotItem(h, alice, aliceSeat).getType(), "Alice must see the clock in her own row");
            assertEquals(BACKGROUND, countdownSlotItem(h, alice, bobSeat).getType(), "Alice must never see Bob's countdown");

            assertEquals(CLOCK, countdownSlotItem(h, bob, bobSeat).getType(), "Bob must see the clock in his own row");
            assertEquals(BACKGROUND, countdownSlotItem(h, bob, aliceSeat).getType(), "Bob must never see Alice's countdown");
        }
    }

    @Test
    void aSpectatorSeesNoPlayerRowCountdownClockAtAll() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            stubCountdownDuration(h, 30);
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            int bobSeat = BlackjackSlotLayout.SEAT_SLOTS[1];
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, aliceSeat);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, bobSeat);
            placeRealBet(h, alice, aliceSeat);
            placeRealBet(h, bob, bobSeat);
            h.scheduler.advance(20);

            Player spectator = h.seatOnlinePlayer(UUID.randomUUID(), "Spectator"); // opens the table but never sits

            assertEquals(BACKGROUND, countdownSlotItem(h, spectator, aliceSeat).getType());
            assertEquals(BACKGROUND, countdownSlotItem(h, spectator, bobSeat).getType());
        }
    }

    @Test
    void everyOwnersClockAgreesWithTheSingleCanonicalCountdownValue() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            stubCountdownDuration(h, 30);
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            int bobSeat = BlackjackSlotLayout.SEAT_SLOTS[1];
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, aliceSeat);
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, bobSeat);
            placeRealBet(h, alice, aliceSeat);
            placeRealBet(h, bob, bobSeat);
            h.scheduler.advance(20);
            h.scheduler.advance(20);

            int canonical = h.inventory.countdownSecondsRemainingForTest();
            assertTrue(canonical > 0, "test setup: countdown must still be running");
            assertEquals(canonical, countdownSlotItem(h, alice, aliceSeat).getAmount(), "no separate per-player deadline -- one canonical value everywhere");
            assertEquals(canonical, countdownSlotItem(h, bob, bobSeat).getAmount());
        }
    }

    @Test
    void reopeningOrFreshlyBootstrappingDuringCountdownShowsThePrivateClockImmediately() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            stubCountdownDuration(h, 30);
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            int bobSeat = BlackjackSlotLayout.SEAT_SLOTS[1];
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, aliceSeat);
            placeRealBet(h, alice, aliceSeat);
            h.scheduler.advance(20);

            // Bob registers online and sits/bets *without* ever having
            // opened the table yet (no view exists for him at all) -- his
            // first-ever bootstrap, via getOrCreateView below, must show
            // his private entrance first, then restore the clock straight
            // from live canonical state on its completion (not wait for a
            // later one-second countdown tick to repair the view).
            Player bob = h.registerOnlinePlayer(UUID.randomUUID(), "Bob");
            h.click(bob, bobSeat);
            placeRealBet(h, bob, bobSeat);

            countdownSlotItem(h, bob, bobSeat); // first bootstrap starts Bob's entrance
            assertTrue(h.inventory.isTableEntranceActiveForTest(bob.getUniqueId()));
            h.scheduler.advance(h.tableEntranceMaxDurationTicksForTest());

            assertEquals(CLOCK, countdownSlotItem(h, bob, bobSeat).getType(), "finishing a fresh mid-countdown entrance must restore the owner's live clock immediately");
            assertEquals(BACKGROUND, countdownSlotItem(h, bob, aliceSeat).getType(), "still never Alice's countdown, even on first bootstrap");
        }
    }

    @Test
    void countdownCleanupRestoresBackgroundForEveryone() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            stubCountdownDuration(h, 30);
            int aliceSeat = BlackjackSlotLayout.SEAT_SLOTS[0];
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            h.click(alice, aliceSeat);
            placeRealBet(h, alice, aliceSeat);
            h.scheduler.advance(20);
            assertEquals(CLOCK, countdownSlotItem(h, alice, aliceSeat).getType(), "test setup: countdown must actually be showing first");

            // Pulling the only bet back out cancels the countdown entirely (stopCountdownTimer).
            h.click(alice, BlackjackSlotLayout.UNDO_ALL_SLOT);

            assertEquals(BACKGROUND, countdownSlotItem(h, alice, aliceSeat).getType(), "cancelling the countdown must restore the felt background");
        }
    }
}
