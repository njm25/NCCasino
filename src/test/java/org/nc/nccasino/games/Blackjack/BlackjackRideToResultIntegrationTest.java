package org.nc.nccasino.games.Blackjack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.nc.nccasino.helpers.Preferences;
import org.nc.nccasino.objects.Card;
import org.nc.nccasino.objects.Rank;
import org.nc.nccasino.objects.Suit;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.SessionRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller-level regression coverage for the mandatory-Action-Timer
 * "ride to result" redesign: closing the Blackjack GUI (while still
 * connected) or genuinely disconnecting mid-round no longer forfeits --
 * {@code GameTerminationPolicy.blackjack} now returns {@code RIDE_TO_RESULT}
 * whenever {@code gameActive}, and {@code onSessionTerminated}'s handling of
 * it leaves seat/hand/wager/turn state completely untouched, re-registers
 * with {@link SessionRegistry}, and (only if still online) sends a
 * contextual warning. The Action Timer -- now always running, since it can
 * no longer be disabled -- is what actually resolves an absent player's
 * decision (auto-Stand), exactly as it already does for an idle-but-present
 * player; nothing here duplicates that mechanism. Only a moderation kick
 * still forfeits instantly.
 */
class BlackjackRideToResultIntegrationTest {

    private static List<Card> flatStack(Rank rank, int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(new Card(Suit.SPADES, rank));
        }
        return cards;
    }

    /** Two seated players, no Ace up-card (no insurance), reaches an actionable turn for exactly one of them. */
    private static UUID seatTwoAndReachActionableTurn(BlackjackControllerTestSupport.Harness h, Player alice, Player bob) {
        h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
        h.currencyProvider.setBalance(1000);
        h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
        h.inventory.commitWagerForTest(alice, 10.0);
        h.click(bob, BlackjackSlotLayout.SEAT_SLOTS[1]);
        h.inventory.commitWagerForTest(bob, 10.0);
        h.inventory.beginStartTransitionForTest();
        h.advanceToActionableTurn(20, 40);
        UUID currentPlayerId = h.inventory.currentPlayerIdForTest();
        assertNotNull(currentPlayerId, "test setup must actually reach an actionable turn");
        return currentPlayerId;
    }

    /** Closes {@code player}'s view exactly as a real InventoryCloseEvent would, then flushes handlePlayerClose's own next-tick scheduled decision. */
    private static void closeView(BlackjackControllerTestSupport.Harness h, Player player) {
        BlackjackView view = h.inventory.viewForTest(player.getUniqueId());
        assertNotNull(view, "test setup must have actually opened a view for this player first");
        h.inventory.onViewClosed(player, view);
        h.scheduler.advance(1);
    }

    @Test
    void closingWhileOnlineDuringOwnTurnRidesToResultInsteadOfForfeiting() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID actingId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player acting = actingId.equals(alice.getUniqueId()) ? alice : bob;

            double wagerBefore = h.inventory.totalRoundRefundForPlayerForTest(actingId);
            assertTrue(wagerBefore > 0, "test setup must have an actual wager at stake");

            closeView(h, acting);

            assertTrue(h.inventory.isSeatedForTest(actingId), "RIDE_TO_RESULT must never remove the seat");
            assertEquals(wagerBefore, h.inventory.totalRoundRefundForPlayerForTest(actingId), "the wager must be completely untouched, neither refunded nor forfeited");
            assertTrue(SessionRegistry.isRegistered(actingId, h.inventory), "must stay (re-)registered so a later PLUGIN_DISABLE while riding still resolves correctly");
            assertEquals(actingId, h.inventory.currentPlayerIdForTest(), "closing must never force-advance the turn -- only the Action Timer resolves it");
            assertTrue(h.inventory.turnTimerSecondsRemainingForTest() > 0, "the in-flight deadline must keep running exactly as it already was");
        }
    }

    @Test
    void closingWhileOnlineBeforeOwnTurnAlsoRidesToResult() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID actingId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player nonActing = actingId.equals(alice.getUniqueId()) ? bob : alice;
            UUID nonActingId = nonActing.getUniqueId();

            double wagerBefore = h.inventory.totalRoundRefundForPlayerForTest(nonActingId);

            closeView(h, nonActing);

            assertTrue(h.inventory.isSeatedForTest(nonActingId));
            assertEquals(wagerBefore, h.inventory.totalRoundRefundForPlayerForTest(nonActingId));
            assertTrue(SessionRegistry.isRegistered(nonActingId, h.inventory));
            assertEquals(actingId, h.inventory.currentPlayerIdForTest(), "the still-in-progress turn must be completely unaffected by an unrelated player's own close");
        }
    }

    @Test
    void closedActingPlayersHandStillAutoStandsViaTheMandatoryTimerWithoutRemovingTheSeat() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID actingId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player acting = actingId.equals(alice.getUniqueId()) ? alice : bob;
            UUID otherId = actingId.equals(alice.getUniqueId()) ? bob.getUniqueId() : alice.getUniqueId();

            closeView(h, acting);
            assertEquals(actingId, h.inventory.currentPlayerIdForTest());

            // Drive the already-running deadline (untouched by the close) to
            // expiry -- the Action Timer is mandatory now, so this always
            // exists regardless of anything the closed player did or didn't do.
            for (int i = 0; i < 60 && actingId.equals(h.inventory.currentPlayerIdForTest()); i++) {
                h.scheduler.advance(20);
            }

            assertEquals(otherId, h.inventory.currentPlayerIdForTest(), "the timeout must auto-Stand the closed player's hand and advance the turn normally");
            assertTrue(h.inventory.isSeatedForTest(actingId), "auto-Stand resolves the decision only -- it must never remove the seat or the wager");
        }
    }

    @Test
    void aKickStillForfeitsInstantlyDuringAnActiveRound() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID actingId = seatTwoAndReachActionableTurn(h, alice, bob);

            int depositsBefore = h.currencyProvider.depositAttempts.size();

            h.inventory.onSessionTerminated(actingId, ExitReason.KICKED);

            assertFalse(h.inventory.isSeatedForTest(actingId), "a kick must still remove the seat immediately, unlike an ordinary disconnect/close");
            assertEquals(depositsBefore, h.currencyProvider.depositAttempts.size(), "a kick forfeits -- it must never refund");
        }
    }

    @Test
    void spareMessageFiresOnlyWhenOnlineAndRespectsMessageSetting() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            when(h.preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.STANDARD);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID actingId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player acting = actingId.equals(alice.getUniqueId()) ? alice : bob;

            closeView(h, acting);

            verify(acting).sendMessage("blackjack.closed-during-turn");
        }
    }

    @Test
    void spareMessageIsSuppressedUnderNoneMessageSetting() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            // Harness default is already NONE, but assert it explicitly so this test survives a harness default change.
            when(h.preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.NONE);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID actingId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player acting = actingId.equals(alice.getUniqueId()) ? alice : bob;

            closeView(h, acting);

            verify(acting, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
        }
    }

    /**
     * Once a player's own turn has fully resolved (stood, busted, etc.) --
     * {@code playerDone}, not merely "not currently my turn" -- there's no
     * timer running against them and nothing left to return "before".
     * Closing at that point must send no ride-to-result warning at all,
     * unlike {@link #closingWhileOnlineBeforeOwnTurnAlsoRidesToResult}
     * (a player who hasn't played yet, where the warning is still correct).
     */
    @Test
    void spareMessageIsSuppressedOnceThePlayersOwnTurnHasAlreadyResolved() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            when(h.preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.STANDARD);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID actingId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player acting = actingId.equals(alice.getUniqueId()) ? alice : bob;
            Player other = actingId.equals(alice.getUniqueId()) ? bob : alice;

            h.click(acting, BlackjackSlotLayout.ACTION_STAND_SLOT);
            h.scheduler.advance(BlackjackTiming.TURN_ADVANCE_DELAY_TICKS);
            assertEquals(other.getUniqueId(), h.inventory.currentPlayerIdForTest(), "test setup: standing must advance the turn to the other player");

            org.mockito.Mockito.clearInvocations(acting); // discard Stand's own confirmation message -- only the ride-to-result warning (or its absence) matters here
            closeView(h, acting);

            verify(acting, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Test
    void spareMessageIsNeverSentToAGenuinelyOfflinePlayer() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            when(h.preferences.getMessageSetting()).thenReturn(Preferences.MessageSetting.STANDARD);

            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID actingId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player acting = actingId.equals(alice.getUniqueId()) ? alice : bob;

            h.setOnline(acting, false);
            h.inventory.onSessionTerminated(actingId, ExitReason.DISCONNECTED);

            verify(acting, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
            assertTrue(h.inventory.isSeatedForTest(actingId), "still rides to result even while offline -- only messaging is skipped");
            assertTrue(SessionRegistry.isRegistered(actingId, h.inventory));

            Player spectator = h.registerOnlinePlayer(UUID.randomUUID(), "Spectator");
            int retainedSeat = actingId.equals(alice.getUniqueId())
                ? BlackjackSlotLayout.SEAT_SLOTS[0]
                : BlackjackSlotLayout.SEAT_SLOTS[1];
            ItemStack retainedHead = h.inventory.getOrCreateView(spectator).getItem(retainedSeat);
            assertNotNull(retainedHead);
            assertEquals(Material.PLAYER_HEAD, retainedHead.getType(),
                "an offline RIDE_TO_RESULT seat must render as occupied, never as an empty chair");
        }
    }

    @Test
    void reopeningAfterBeingRiddenBootstrapsTheActionRowCorrectly() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            Player alice = h.seatOnlinePlayer(UUID.randomUUID(), "Alice");
            Player bob = h.seatOnlinePlayer(UUID.randomUUID(), "Bob");
            UUID actingId = seatTwoAndReachActionableTurn(h, alice, bob);
            Player acting = actingId.equals(alice.getUniqueId()) ? alice : bob;

            closeView(h, acting);
            assertEquals(actingId, h.inventory.currentPlayerIdForTest());

            org.bukkit.inventory.Inventory reopened = h.inventory.getOrCreateView(acting);
            ItemStack standItem = reopened.getItem(BlackjackSlotLayout.ACTION_STAND_SLOT);
            assertNotNull(standItem);
            assertEquals(Material.SHIELD, standItem.getType(), "reopening must reconstruct the still-actionable turn's own action row from canonical state");
        }
    }

    @Test
    void insurancePhaseSpareCloseLetsTheInsuranceTimerAutoResolveWithoutRemovingTheSeat() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.currencyProvider.setBalance(1000);
            List<Card> stack = new ArrayList<>();
            stack.add(new Card(Suit.SPADES, Rank.SEVEN));  // player 1 card 1
            stack.add(new Card(Suit.CLUBS, Rank.SEVEN));   // player 2 card 1
            stack.add(new Card(Suit.HEARTS, Rank.ACE));    // dealer up-card
            stack.add(new Card(Suit.SPADES, Rank.SEVEN));  // player 1 card 2
            stack.add(new Card(Suit.CLUBS, Rank.SEVEN));   // player 2 card 2
            stack.add(new Card(Suit.HEARTS, Rank.SEVEN));  // dealer hole card -- no dealer blackjack
            stack.addAll(flatStack(Rank.SEVEN, 40));
            h.inventory.stackDeckForTest(stack);

            UUID player1Id = UUID.randomUUID();
            Player player1 = h.seatOnlinePlayer(player1Id, "Player1");
            h.click(player1, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(player1, 10.0);
            Player player2 = h.seatOnlinePlayer(UUID.randomUUID(), "Player2");
            h.click(player2, BlackjackSlotLayout.SEAT_SLOTS[1]);
            h.inventory.commitWagerForTest(player2, 10.0);
            h.inventory.beginStartTransitionForTest();

            for (int i = 0; i < 40 && h.inventory.capturePhaseForTest() != BlackjackFrame.Phase.INSURANCE; i++) {
                h.scheduler.advance(20);
            }
            assertEquals(BlackjackFrame.Phase.INSURANCE, h.inventory.capturePhaseForTest(), "test setup must actually reach the insurance phase");

            double wagerBefore = h.inventory.totalRoundRefundForPlayerForTest(player1Id);

            closeView(h, player1);

            assertTrue(h.inventory.isSeatedForTest(player1Id), "a spared close during insurance must never forfeit the seat");
            assertEquals(wagerBefore, h.inventory.totalRoundRefundForPlayerForTest(player1Id));
            assertTrue(SessionRegistry.isRegistered(player1Id, h.inventory));

            // The insurance timer, already running independently, resolves
            // the closed player's decision (auto-No) on its own schedule --
            // player2 (still present, silent) keeps the phase from resolving
            // early via checkInsuranceAllDecided.
            for (int i = 0; i < 40 && h.inventory.capturePhaseForTest() == BlackjackFrame.Phase.INSURANCE; i++) {
                h.scheduler.advance(20);
            }

            assertTrue(h.inventory.isSeatedForTest(player1Id), "resolving the insurance decision by timeout must never remove the seat");
        }
    }

    /**
     * {@code gameActive} only flips true inside {@code activateGame()},
     * which runs after the entire start-transition window (door-conceal +
     * shared dealer slide animation + the readiness gate) already
     * completes -- see {@code beginStartTransition}/{@code activateGame}.
     * A player who already committed a wager and closes during exactly that
     * window -- after paying, but before cards are actually dealt -- must
     * still ride into the round rather than being refunded-and-removed, via
     * {@code GameTerminationPolicy.blackjack}'s {@code hasCommittedWager}
     * flag (independent of {@code gameActive}). The door-conceal animation
     * itself doesn't require their view to be open to complete on schedule
     * (see {@code renderPrivateItem}'s null-view no-op), so the
     * readiness gate is never stalled by this.
     */
    @Test
    void closingDuringStartTransitionWithACommittedWagerRidesToResultInsteadOfRefunding() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);

            h.inventory.beginStartTransitionForTest();
            assertTrue(h.inventory.isStartTransitionActiveForTest(), "test setup must actually be mid start-transition");
            assertFalse(h.inventory.isGameActiveForTest(), "gameActive must not flip true until the transition's readiness gate finishes");

            double wagerBefore = h.inventory.totalRoundRefundForPlayerForTest(aliceId);
            int depositsBefore = h.currencyProvider.depositAttempts.size();
            closeView(h, alice);

            assertTrue(h.inventory.isSeatedForTest(aliceId), "a committed wager must ride into the round, not be refunded-and-removed");
            assertEquals(wagerBefore, h.inventory.totalRoundRefundForPlayerForTest(aliceId), "the wager must be completely untouched");
            assertEquals(depositsBefore, h.currencyProvider.depositAttempts.size(), "must not be refunded");
            assertTrue(SessionRegistry.isRegistered(aliceId, h.inventory));

            // The start-transition must still complete normally and deal
            // Alice in, exactly as if she'd stayed online and present.
            for (int i = 0; i < 40 && !h.inventory.isGameActiveForTest(); i++) {
                h.scheduler.advance(20);
            }
            h.scheduler.advance(20); // let the first card's own deck-flight + flip actually land -- gameActive flips before any card data does
            assertTrue(h.inventory.isGameActiveForTest(), "the start-transition must still complete on schedule despite the closed view");
            assertTrue(h.inventory.isSeatedForTest(aliceId));
            assertTrue(h.inventory.activeHandCardCountForTest(aliceId) > 0, "a player riding a committed wager through start-transition must still actually get dealt cards");
        }
    }

    /**
     * The other half of the same fix: a merely-seated player who never
     * committed a wager must still free their seat immediately on close --
     * {@code hasCommittedWager} must not make every seated close ride
     * forever, only one with real money already at stake.
     */
    @Test
    void closingDuringCountdownWithNoCommittedWagerStillRefundsAndFreesTheSeat() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);

            // A bystander with a real wager keeps the table's countdown
            // genuinely running so this scenario isn't trivially moot.
            Player bystander = h.seatOnlinePlayer(UUID.randomUUID(), "Bystander");
            h.click(bystander, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(bystander, 10.0);

            UUID spectatorId = UUID.randomUUID();
            Player spectator = h.seatOnlinePlayer(spectatorId, "Spectator");
            h.click(spectator, BlackjackSlotLayout.SEAT_SLOTS[1]);
            assertEquals(0.0, h.inventory.totalRoundRefundForPlayerForTest(spectatorId), "test setup: spectator must not have bet anything");

            closeView(h, spectator);

            assertFalse(h.inventory.isSeatedForTest(spectatorId), "a never-bet spectator must still free the seat on close, not ride forever");
        }
    }

    /** Confirms the fix also covers the plain pregame/countdown phase (before start-transition even begins), not just start-transition itself. */
    @Test
    void closingDuringCountdownWithACommittedWagerRidesToResultInsteadOfRefunding() {
        try (BlackjackControllerTestSupport.Harness h = BlackjackControllerTestSupport.newHarness()) {
            h.inventory.stackDeckForTest(flatStack(Rank.SEVEN, 60));
            h.currencyProvider.setBalance(1000);

            UUID aliceId = UUID.randomUUID();
            Player alice = h.seatOnlinePlayer(aliceId, "Alice");
            h.click(alice, BlackjackSlotLayout.SEAT_SLOTS[0]);
            h.inventory.commitWagerForTest(alice, 10.0);
            assertFalse(h.inventory.isStartTransitionActiveForTest(), "test setup: still plain countdown, not start-transition yet");

            double wagerBefore = h.inventory.totalRoundRefundForPlayerForTest(aliceId);
            int depositsBefore = h.currencyProvider.depositAttempts.size();
            closeView(h, alice);

            assertTrue(h.inventory.isSeatedForTest(aliceId), "right after bets are accepted, closing must ride to result, not refund");
            assertEquals(wagerBefore, h.inventory.totalRoundRefundForPlayerForTest(aliceId));
            assertEquals(depositsBefore, h.currencyProvider.depositAttempts.size());
        }
    }
}
