package org.nc.nccasino.games.RockPaperScissors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.payout.PayoutMessages;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;

/**
 * Dispatches events to a {@link RpsMatch}. In PLAYER_VS_PLAYER there is
 * exactly one shared match (a single table, same as Coin Flip). In
 * PLAYER_VS_DEALER every player who interacts with this dealer gets their
 * own private match against the house, created lazily and kept independent
 * of every other player's -- mirroring how Mines hands out a private
 * MinesTable per player rather than sharing one table.
 */
public class RockPaperScissorsServer extends Server {

    /** Stands in for a real player UUID in a match's {@code picks} when the opponent is the house. */
    private static final UUID DEALER_ID = new UUID(0L, 0L);
    private static final long PRE_REVEAL_DELAY_TICKS = 10L;
    private static final long REVEAL_WINDOW_TICKS = 70L;

    /**
     * The single shared table -- always live, exactly like Coin Flip.
     * Independent of any player's individually-chosen view below.
     */
    private final RpsMatch sharedMatch = new RpsMatch(null);

    /** One private match per player, created on first interaction, independent of the shared table. */
    private final Map<UUID, RpsMatch> pveMatches = new HashMap<>();

    /**
     * Each player's own currently-selected view of this dealer -- PvP (the
     * shared table) or PvE (their own private match). Seeded lazily from
     * the admin's configured default on first interaction, then mutable
     * per player via the in-game toggle button, independent of every other
     * player's choice.
     */
    private final Map<UUID, RpsMode> playerView = new HashMap<>();

    public RockPaperScissorsServer(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, plugin, internalName);
    }

    @Override
    protected Client createClientForPlayer(Player player) {
        RockPaperScissorsClient client = new RockPaperScissorsClient(this, player, plugin, internalName);
        return client;
    }

    @Override
    public void onClientUpdate(Client client, String eventType, Object data) {
        if ("PLAYER_TOGGLE_MODE".equals(eventType)) {
            handleToggleMode(client);
            return;
        }
        matchFor(client.getPlayer().getUniqueId()).handle(client, eventType, data);
    }

    RpsMode viewFor(UUID playerId) {
        return playerView.computeIfAbsent(playerId, id -> plugin.getRockPaperScissorsMode(internalName));
    }

    private RpsMatch matchFor(UUID playerId) {
        if (viewFor(playerId) == RpsMode.PLAYER_VS_DEALER) {
            return pveMatches.computeIfAbsent(playerId, RpsMatch::new);
        }
        return sharedMatch;
    }

    /**
     * Switches the requesting player's own personal view between the
     * shared PvP table and their private PvE match, independent of every
     * other player currently at this dealer. Blocked while the requester's
     * own current match is active -- leaving mid-round would either abandon
     * a live PvP opponent or orphan their own PvE round. Cleanly exits
     * whichever seat they're in via the same eviction/promotion path a
     * normal chair-leave uses, then -- if they were seated at all -- drops
     * them straight into chair 1 of the table they just switched into,
     * rather than making them click to sit down again.
     */
    private void handleToggleMode(Client client) {
        UUID playerId = client.getPlayer().getUniqueId();
        RpsMatch currentMatch = matchFor(playerId);
        if (currentMatch.gameActive) {
            client.onServerUpdate("TOGGLE_MODE_DENIED", null);
            return;
        }
        boolean wasSeated = currentMatch.isSeated(playerId);
        forfeitPlayer(playerId);

        RpsMode next = viewFor(playerId) == RpsMode.PLAYER_VS_PLAYER
            ? RpsMode.PLAYER_VS_DEALER
            : RpsMode.PLAYER_VS_PLAYER;
        playerView.put(playerId, next);
        client.onServerUpdate("MODE_CHANGED", next);

        RpsMatch newMatch = matchFor(playerId);
        if (wasSeated && newMatch.chairOneEmpty()) {
            newMatch.handle(client, "PLAYER_SIT_ONE", null);
        }
        newMatch.sendChairSnapshotTo(client);
    }

    /**
     * Kicked players forfeit unconditionally, regardless of round phase --
     * no refund, no pending payout.
     */
    void forfeitPlayer(UUID playerId) {
        matchFor(playerId).forfeitPlayer(playerId);
    }

    /** Settles playerId's in-flight match during shutdown (refund pregame/pick-phase, pay out a committed winner). */
    void refundForShutdown(UUID playerId) {
        matchFor(playerId).refundForShutdown();
    }

    void registerRidingSession(UUID playerId) {
        matchFor(playerId).registerRidingSession(playerId);
    }

    /**
     * Frees a PvE player's private match once it's fully idle -- no-op in
     * PvP (the shared table isn't per-player and must never be removed) and
     * a no-op if the match is still riding out an active round, since that
     * round's own resolution path (resolveRound/refundForShutdown) is what
     * eventually leaves it idle. Without this, {@link #pveMatches} grows by
     * one permanent entry per unique player who ever interacts with this
     * dealer, mirroring the eviction Mines does per-player via removeTable.
     */
    void cleanupIdleMatch(UUID playerId) {
        RpsMatch match = pveMatches.get(playerId);
        if (match != null && !match.gameActive && !hasClient(playerId)) {
            pveMatches.remove(playerId, match);
        }
    }

    private void queuePendingPayout(UUID playerId, double amount) {
        queuePendingPayout(playerId, amount, PayoutMessages.disconnectedMidGameContext("Rock Paper Scissors"));
    }

    private void queuePendingPayout(UUID playerId, double amount, String context) {
        Material currencyMaterial = plugin.getCurrency(internalName);
        PendingPayout pendingPayout = PendingPayout.create(
            playerId,
            "Rock Paper Scissors",
            internalName,
            currencyMode,
            currencyMaterial != null ? currencyMaterial.name() : null,
            currencyName,
            amount,
            context
        );
        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(pendingPayout);
        if (!persisted) {
            plugin.getLogger().warning("[NCCasino] Rock Paper Scissors pending payout failed to persist for " + playerId + ".");
        }
    }

    /**
     * One table's worth of round state and logic -- two chairs, a bet, a
     * pick phase, tie-rethrow, timeout, and payout. Instantiated once as
     * the shared PvP table ({@code owningPlayerId == null}, updates go to
     * every client via the outer {@code broadcastUpdate}), or once per
     * player in PvE ({@code owningPlayerId} set, updates go only to that
     * player's own client -- chair 2 is always the house).
     */
    private final class RpsMatch {

        private final UUID owningPlayerId;

        private int countdownTaskId = -1;
        private int timeLeft = 0;

        /**
         * Bumped once per accepted bet (never on a tie's rethrow, which
         * stays the same round). Deferred resolution callbacks -- the
         * reveal's own fallback timer and the client's echoed
         * ANIMATION_FINISHED -- capture this at schedule/broadcast time and
         * compare it back before resolving, so a callback that outlives its
         * round (e.g. a delayed packet arriving after a new round already
         * started) becomes a safe no-op instead of resolving the wrong round.
         */
        private int roundToken = 0;

        private Player chairOneOccupant;
        private Player chairTwoOccupant;
        private int betAmount;
        private boolean gameActive;
        private boolean revealInProgress;
        private Integer committedWinner;
        private final Map<UUID, Throw> picks = new HashMap<>();
        private final Set<UUID> forfeited = new HashSet<>();
        private final Map<UUID, TerminableSession> ridingSessions = new HashMap<>();

        private RpsMatch(UUID owningPlayerId) {
            this.owningPlayerId = owningPlayerId;
        }

        private boolean isPve() {
            return owningPlayerId != null;
        }

        /** Broadcasts to every client of the dealer (PvP), or pushes straight to the one owning client (PvE). */
        private void send(String eventType, Object data) {
            if (owningPlayerId == null) {
                broadcastUpdate(eventType, data);
            } else {
                Client owner = clients.get(owningPlayerId);
                if (owner != null) {
                    owner.onServerUpdate(eventType, data);
                }
            }
        }

        private void handle(Client client, String eventType, Object data) {
            switch (eventType) {
                case "PLAYER_SIT_ONE":
                    if (gameActive) return;
                    chairOneOccupant = client.getPlayer();
                    send("PLAYER_SIT_ONE", chairOneOccupant);
                    break;
                case "PLAYER_SIT_TWO":
                    if (gameActive || isPve()) return;
                    chairTwoOccupant = client.getPlayer();
                    send("PLAYER_SIT_TWO", chairTwoOccupant);
                    break;
                case "PLAYER_LEAVE_ONE":
                    if (gameActive) return;
                    if (chairTwoOccupant != null) {
                        chairOneOccupant = chairTwoOccupant;
                        chairTwoOccupant = null;
                        betAmount = 0;
                        send("PLAYER_LEAVE_TWO", null);
                        send("PLAYER_LEAVE_ONE", null);
                        send("PLAYER_SIT_ONE", chairOneOccupant);
                    } else {
                        chairOneOccupant = null;
                        betAmount = 0;
                        send("PLAYER_LEAVE_ONE", null);
                    }
                    break;
                case "PLAYER_LEAVE_TWO":
                    if (gameActive || isPve()) return;
                    chairTwoOccupant = null;
                    send("PLAYER_LEAVE_TWO", null);
                    break;
                case "PLAYER_SUBMIT_BET":
                    if (gameActive) return;
                    if (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(client.getPlayer().getUniqueId())) {
                        if (betAmount == 0) {
                            betAmount = (int) data;
                            if (isPve()) {
                                // No second human to accept -- the house
                                // takes the other side of the bet immediately.
                                beginActiveRound(Boolean.TRUE);
                            } else {
                                send("PLAYER_SUBMIT_BET", data);
                            }
                        } else {
                            betAmount = 0;
                            send("PLAYER_CANCEL_BET", null);
                        }
                    }
                    break;
                case "PLAYER_ACCEPT_BET":
                    if (gameActive) return;
                    if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(client.getPlayer().getUniqueId())
                        && betAmount != 0) {
                        beginActiveRound(data);
                    } else {
                        // The accepting client already deducted the wager
                        // locally before this arrived (e.g. chair one left
                        // and chair two got promoted/cleared in the same
                        // window) -- tell that client directly so it can
                        // refund what it already took, instead of silently
                        // dropping the accept and losing the player's currency.
                        client.onServerUpdate("PLAYER_ACCEPT_REJECTED", null);
                    }
                    break;
                case "PLAYER_CHOOSE":
                    handlePlayerChoose(client, data);
                    break;
                case "ANIMATION_FINISHED":
                    if (data instanceof Object[] arr && arr.length == 2
                        && arr[0] instanceof Integer w && arr[1] instanceof Integer token) {
                        if (token == roundToken) {
                            resolveRound(committedWinner != null ? committedWinner : w);
                        }
                    }
                    break;
                case "GET_CHAIRS":
                    sendChairSnapshotTo(client);
                    break;
                default:
                    break;
            }
        }

        /** Pushes this match's current chairs/bet/round state to a single client -- shared by GET_CHAIRS and a just-completed mode switch. */
        private void sendChairSnapshotTo(Client client) {
            UUID viewerId = client.getPlayer().getUniqueId();
            UUID opponentId = opponentOf(viewerId);
            Object[] chairs = {
                chairOneOccupant,
                chairTwoOccupant,
                betAmount,
                gameActive,
                timeLeft,
                picks.containsKey(viewerId),
                opponentId != null && picks.containsKey(opponentId),
            };
            client.onServerUpdate("GET_CHAIRS", chairs);
        }

        /** Whether playerId currently occupies either chair of this match. */
        private boolean isSeated(UUID playerId) {
            return (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(playerId))
                || (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(playerId));
        }

        private boolean chairOneEmpty() {
            return chairOneOccupant == null;
        }

        /** Shared tail of both "player 2 accepted" and "the house auto-accepted" -- doubles the pot and opens the pick phase. */
        private void beginActiveRound(Object acceptPayload) {
            send("PLAYER_ACCEPT_BET", acceptPayload);
            gameActive = true;
            revealInProgress = false;
            roundToken++;
            betAmount = betAmount * 2;
            picks.clear();
            startTimer();
        }

        private void handlePlayerChoose(Client client, Object data) {
            if (!gameActive || revealInProgress) return;
            if (!(data instanceof Throw chosen)) return;

            UUID chooserId = client.getPlayer().getUniqueId();
            boolean isChairOne = chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(chooserId);
            boolean isChairTwo = chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(chooserId);
            if (!isChairOne && !isChairTwo) return;
            if (picks.containsKey(chooserId)) return;

            picks.put(chooserId, chosen);

            if (isPve() && isChairOne) {
                // The house only ever throws in direct response to the
                // player's own pick -- never ahead of time -- so there's
                // nothing to notify and no opponent client to look up.
                picks.put(DEALER_ID, randomThrow());
            } else {
                UUID opponentId = opponentOf(chooserId);
                if (opponentId != null) {
                    Client opponentClient = clients.get(opponentId);
                    if (opponentClient != null) {
                        opponentClient.onServerUpdate("OPPONENT_LOCKED_IN", null);
                    }
                }
            }

            evaluatePicks();
        }

        private UUID opponentOf(UUID playerId) {
            if (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(playerId)) {
                return chairTwoKey();
            }
            if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(playerId)) {
                return chairOneOccupant != null ? chairOneOccupant.getUniqueId() : null;
            }
            return null;
        }

        /** Chair 2's identity for {@code picks} lookups: the real occupant in PvP, the dealer sentinel in PvE. */
        private UUID chairTwoKey() {
            if (chairTwoOccupant != null) {
                return chairTwoOccupant.getUniqueId();
            }
            return isPve() ? DEALER_ID : null;
        }

        /**
         * Compares both picks once they're both in. A tie clears both picks
         * and lets the same accepted-bet round continue (pot untouched)
         * rather than resolving anything, so the disconnect policy can keep
         * treating the whole span as a single "ride to result" session.
         */
        private void evaluatePicks() {
            if (chairOneOccupant == null) return;
            UUID twoKey = chairTwoKey();
            if (twoKey == null) return;

            Throw one = picks.get(chairOneOccupant.getUniqueId());
            Throw two = picks.get(twoKey);
            if (one == null || two == null) return;
            revealInProgress = true;

            // Stop the pick timer as soon as both choices lock. Hold for a
            // short anticipation beat before beginning the reveal cadence.
            if (countdownTaskId != -1) {
                Bukkit.getScheduler().cancelTask(countdownTaskId);
                countdownTaskId = -1;
            }
            Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> beginReveal(one, two),
                PRE_REVEAL_DELAY_TICKS
            );
        }

        private void beginReveal(Throw one, Throw two) {
            if (!gameActive || !revealInProgress) return;
            if (one == two) {
                send("TIE_REVEAL", new Object[]{one, two});
                // Deliberately not resolved from a client callback the way
                // the decisive branch is: there's no payout at stake on a
                // tie, so a single fixed delay (matching the client's
                // cycle-then-settle animation length) is all that's needed
                // before the same accepted round throws again.
                Bukkit.getScheduler().runTaskLater(plugin, this::rethrow, REVEAL_WINDOW_TICKS);
                return;
            }

            int winner = one.beats(two) ? 0 : 1;
            committedWinner = winner;

            int token = roundToken;
            send("REVEAL", new Object[]{one, two, winner, token});
            // Authoritative fallback, same safety net Coin Flip uses:
            // resolves the round even if every client disconnects before
            // its local reveal animation reports back. Guarded by roundToken
            // so a fallback scheduled for a since-superseded round can't fire.
            Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (token == roundToken) {
                        resolveRound(committedWinner != null ? committedWinner : winner);
                    }
                },
                REVEAL_WINDOW_TICKS
            );
        }

        /**
         * Fires ~70 ticks after a tied REVEAL -- clears both picks and lets
         * the same accepted round throw again with a fresh pick timer. A
         * no-op if the round was already torn down (kick/shutdown) during
         * the reveal.
         */
        private void rethrow() {
            if (!gameActive) return;

            revealInProgress = false;
            picks.clear();
            send("RETHROW", null);
            timeLeft = plugin.getTimer(internalName);
            startTimer();
        }

        /**
         * Authoritative resolution for a decisive round, called either by
         * whichever seated client's local reveal animation reports back
         * first, or by the server's own fixed-delay fallback timer
         * scheduled alongside the REVEAL broadcast -- whichever fires first
         * wins, the other becomes a safe no-op via the gameActive guard.
         */
        private void resolveRound(int winner) {
            if (!gameActive) return;

            send("ANIMATION_FINISHED", winner);
            Player payoutOne = chairOneOccupant;
            Player payoutTwo = chairTwoOccupant;
            int payout = betAmount;
            gameActive = false;
            revealInProgress = false;
            committedWinner = null;
            betAmount = 0;
            timeLeft = 0;
            countdownTaskId = -1;
            picks.clear();
            handlePayout(payoutOne, payoutTwo, payout, winner);
            if (payoutOne != null) {
                clearRidingSession(payoutOne.getUniqueId());
            }
            if (payoutTwo != null) {
                clearRidingSession(payoutTwo.getUniqueId());
            }
            forfeited.clear();
            reseatDisconnectedOccupants();
            cleanupPrivateMatchIfAbandoned();
        }

        /**
         * Neither seated player locked in a choice before the pick timer
         * expired -- there's no one to award the pot to, so both stakes are
         * simply refunded and the round is void.
         */
        private void voidRound() {
            if (!gameActive) return;

            send("ROUND_VOID", null);
            Player payoutOne = chairOneOccupant;
            Player payoutTwo = chairTwoOccupant;
            int stake = betAmount / 2;
            gameActive = false;
            revealInProgress = false;
            committedWinner = null;
            betAmount = 0;
            timeLeft = 0;
            countdownTaskId = -1;
            picks.clear();

            refundStakeIfDue(payoutOne, stake);
            refundStakeIfDue(payoutTwo, stake);

            if (payoutOne != null) {
                clearRidingSession(payoutOne.getUniqueId());
            }
            if (payoutTwo != null) {
                clearRidingSession(payoutTwo.getUniqueId());
            }
            forfeited.clear();
            reseatDisconnectedOccupants();
            cleanupPrivateMatchIfAbandoned();
        }

        private void refundStakeIfDue(Player seatedPlayer, int stake) {
            if (seatedPlayer == null || stake <= 0 || forfeited.contains(seatedPlayer.getUniqueId())) {
                return;
            }
            Player onlinePlayer = Bukkit.getPlayer(seatedPlayer.getUniqueId());
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                creditPlayer(onlinePlayer, stake);
            } else {
                queuePendingPayout(seatedPlayer.getUniqueId(), stake, PayoutMessages.disconnectedMidGameContext("Rock Paper Scissors"));
            }
        }

        /** Post-round seat cleanup for whichever occupant's client already disconnected. */
        private void reseatDisconnectedOccupants() {
            if (chairOneOccupant != null && !hasClient(chairOneOccupant.getUniqueId())) {
                if (chairTwoOccupant != null) {
                    chairOneOccupant = chairTwoOccupant;
                    chairTwoOccupant = null;
                    betAmount = 0;
                    send("PLAYER_LEAVE_TWO", null);
                    send("PLAYER_LEAVE_ONE", null);
                    send("PLAYER_SIT_ONE", chairOneOccupant);
                } else {
                    chairOneOccupant = null;
                    betAmount = 0;
                    send("PLAYER_LEAVE_ONE", null);
                }
            }
            if (chairTwoOccupant != null && !hasClient(chairTwoOccupant.getUniqueId())) {
                chairTwoOccupant = null;
                send("PLAYER_LEAVE_TWO", null);
            }
        }

        private void handlePayout(Player one, Player two, int payout, int winner) {
            UUID winnerId = (winner == 0)
                ? (one != null ? one.getUniqueId() : null)
                : (two != null ? two.getUniqueId() : null);
            UUID loserId = (winner == 0)
                ? (two != null ? two.getUniqueId() : null)
                : (one != null ? one.getUniqueId() : null);

            // Resolve fresh Player references by UUID rather than trusting
            // the cached chair-occupant objects, which can go stale across
            // a reconnect while the game was active (seating is locked
            // while gameActive, so those fields can't be refreshed
            // mid-round).
            if (winnerId != null && payout > 0 && !forfeited.contains(winnerId)) {
                Player winnerPlayer = Bukkit.getPlayer(winnerId);
                if (winnerPlayer != null && winnerPlayer.isOnline()) {
                    creditPlayer(winnerPlayer, payout);
                    sendPayoutMessage(winnerPlayer, payout, true, payout / 2);
                    applyWinEffects(winnerPlayer);
                } else {
                    queuePendingPayout(winnerId, payout);
                }
            }

            if (loserId != null && !forfeited.contains(loserId)) {
                Player loserPlayer = Bukkit.getPlayer(loserId);
                if (loserPlayer != null && loserPlayer.isOnline()) {
                    sendPayoutMessage(loserPlayer, payout, false, payout / 2);
                    applyLoseEffects(loserPlayer);
                }
            }
        }

        /**
         * Settles an in-flight round during shutdown. Once a winner has
         * been committed (decisive reveal or a timeout forfeit), that
         * authoritative payout is saved; before that, each side's stake is
         * refunded -- this covers the pick phase and any tie-rethrow loop
         * the round was in.
         */
        private void refundForShutdown() {
            if (!gameActive) {
                return;
            }

            int payout = betAmount;
            int stake = payout / 2;
            Integer winner = committedWinner;
            Player payoutOne = chairOneOccupant;
            Player payoutTwo = chairTwoOccupant;
            gameActive = false;
            revealInProgress = false;
            committedWinner = null;
            betAmount = 0;
            timeLeft = 0;
            countdownTaskId = -1;
            picks.clear();

            if (winner != null) {
                Player winningPlayer = winner == 0 ? payoutOne : payoutTwo;
                if (winningPlayer != null && payout > 0
                    && !forfeited.contains(winningPlayer.getUniqueId())) {
                    queuePendingPayout(
                        winningPlayer.getUniqueId(),
                        payout,
                        "The server restarted after your Rock Paper Scissors result was determined. Your payout was saved."
                    );
                }
            } else {
                if (payoutOne != null && stake > 0 && !forfeited.contains(payoutOne.getUniqueId())) {
                    queuePendingPayout(payoutOne.getUniqueId(), stake, PayoutMessages.serverRestartRefundContext("Rock Paper Scissors"));
                }
                if (payoutTwo != null && stake > 0 && !forfeited.contains(payoutTwo.getUniqueId())) {
                    queuePendingPayout(payoutTwo.getUniqueId(), stake, PayoutMessages.serverRestartRefundContext("Rock Paper Scissors"));
                }
            }
            if (payoutOne != null) {
                clearRidingSession(payoutOne.getUniqueId());
            }
            if (payoutTwo != null) {
                clearRidingSession(payoutTwo.getUniqueId());
            }
            forfeited.clear();
            cleanupPrivateMatchIfAbandoned();
        }

        private void cleanupPrivateMatchIfAbandoned() {
            if (owningPlayerId != null) {
                cleanupIdleMatch(owningPlayerId);
            }
        }

        /**
         * Kicked players forfeit unconditionally, regardless of round phase
         * -- no refund, no pending payout. Pregame, this clears their
         * seat/bet state directly (bypassing the normal PLAYER_LEAVE
         * broadcast so their own already-removed client can't self-refund).
         * Mid-round, it just marks them so handlePayout/refund helpers deny
         * them even if they'd have won -- the round's own post-payout
         * cleanup frees the seat once it ends.
         */
        private void forfeitPlayer(UUID playerId) {
            clearRidingSession(playerId);
            if (gameActive) {
                forfeited.add(playerId);
                return;
            }

            forfeited.remove(playerId);
            picks.remove(playerId);

            if (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(playerId)) {
                if (chairTwoOccupant != null) {
                    chairOneOccupant = chairTwoOccupant;
                    chairTwoOccupant = null;
                    betAmount = 0;
                    send("PLAYER_LEAVE_TWO", null);
                    send("PLAYER_LEAVE_ONE", null);
                    send("PLAYER_SIT_ONE", chairOneOccupant);
                } else {
                    chairOneOccupant = null;
                    betAmount = 0;
                    send("PLAYER_LEAVE_ONE", null);
                }
            } else if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(playerId)) {
                chairTwoOccupant = null;
                send("PLAYER_LEAVE_TWO", null);
            }
        }

        private void registerRidingSession(UUID playerId) {
            TerminableSession session = ridingSessions.computeIfAbsent(
                playerId,
                RidingSession::new
            );
            SessionRegistry.register(playerId, session);
        }

        private void clearRidingSession(UUID playerId) {
            TerminableSession session = ridingSessions.remove(playerId);
            if (session != null) {
                SessionRegistry.unregister(playerId, session);
            }
        }

        private final class RidingSession implements TerminableSession {
            private final UUID playerId;

            private RidingSession(UUID playerId) {
                this.playerId = playerId;
            }

            @Override
            public void onSessionTerminated(UUID terminatedPlayerId, ExitReason reason) {
                if (!gameActive) {
                    ridingSessions.remove(playerId, this);
                    return;
                }
                if (reason == ExitReason.KICKED) {
                    forfeitPlayer(playerId);
                } else if (reason == ExitReason.PLUGIN_DISABLE) {
                    refundForShutdown();
                } else {
                    SessionRegistry.register(playerId, this);
                }
            }
        }

        private void startTimer() {
            if (countdownTaskId != -1) return; // Timer is already running

            gameState = GameState.WAITING;
            timeLeft = plugin.getTimer(internalName);

            countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                if (timeLeft <= 0) {
                    handleTimeout();
                    return;
                }

                send("UPDATE_TIMER", timeLeft);
                timeLeft--;

                if (timeLeft <= 3) {
                    playCountdownSoundForMatch();
                }

            }, 0L, 20L); // Run every second
        }

        private void playCountdownSoundForMatch() {
            if (!isPve()) {
                RockPaperScissorsServer.this.playCountdownSound();
                return;
            }

            Client owner = clients.get(owningPlayerId);
            Player ownerPlayer = owner != null ? owner.getPlayer() : null;
            if (ownerPlayer != null && SoundHelper.getSoundSafely("block.note_block.hat", ownerPlayer) != null) {
                ownerPlayer.playSound(ownerPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
            }
        }

        /**
         * The pick timer ran out. If exactly one seated player locked in a
         * throw, they win by forfeit; if neither did, there's no one to
         * award the pot to, so the round is voided instead.
         */
        private void handleTimeout() {
            if (countdownTaskId != -1) {
                Bukkit.getScheduler().cancelTask(countdownTaskId);
                countdownTaskId = -1;
            }

            if (isPve()) {
                // The house only ever throws in direct response to the
                // player's own pick, so reaching the timeout here always
                // means the player never chose -- forfeit to the house,
                // same as a PvP player who lets the timer run out.
                revealInProgress = true;
                committedWinner = 1;
                int pveToken = roundToken;
                send("FORFEIT_TIMEOUT", 1);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (pveToken == roundToken) {
                        resolveRound(committedWinner != null ? committedWinner : 1);
                    }
                }, 20L);
                return;
            }

            UUID oneId = chairOneOccupant != null ? chairOneOccupant.getUniqueId() : null;
            UUID twoId = chairTwoOccupant != null ? chairTwoOccupant.getUniqueId() : null;
            boolean oneChose = oneId != null && picks.containsKey(oneId);
            boolean twoChose = twoId != null && picks.containsKey(twoId);

            if (oneChose == twoChose) {
                voidRound();
                return;
            }

            // Block any pick that was still in flight when the timer hit
            // zero -- otherwise a late arrival could pair up with the
            // forfeiting side's pick, trigger its own tie/rethrow via
            // evaluatePicks, and leave this forfeit's committedWinner stale
            // for the fallback resolveRound scheduled below to wrongly act on.
            revealInProgress = true;
            int winner = oneChose ? 0 : 1;
            committedWinner = winner;
            int token = roundToken;
            send("FORFEIT_TIMEOUT", winner);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (token == roundToken) {
                    resolveRound(committedWinner != null ? committedWinner : winner);
                }
            }, 20L);
        }

        private Throw randomThrow() {
            Throw[] values = Throw.values();
            return values[ThreadLocalRandom.current().nextInt(values.length)];
        }
    }
}
