package org.nc.nccasino.games.RockPaperScissors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.payout.PayoutMessages;
import org.nc.nccasino.payout.PendingPayout;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;

public class RockPaperScissorsServer extends Server {

    private int countdownTaskId = -1;
    private int timeLeft = 0;

    protected Player chairOneOccupant;
    protected Player chairTwoOccupant;
    protected int betAmount;
    protected Boolean gameActive;
    private Integer committedWinner;
    private final Map<UUID, Throw> picks = new HashMap<>();
    private final Set<UUID> forfeited = new HashSet<>();
    private final Map<UUID, TerminableSession> ridingSessions = new HashMap<>();

    public RockPaperScissorsServer(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, plugin, internalName);

        this.chairOneOccupant = null;
        this.chairTwoOccupant = null;
        this.betAmount = 0;
        this.gameActive = false;
    }

    @Override
    protected Client createClientForPlayer(Player player) {
        RockPaperScissorsClient client = new RockPaperScissorsClient(this, player, plugin, internalName);
        return client;
    }

    @Override
    public void onClientUpdate(Client client, String eventType, Object data) {
        switch (eventType) {
            case "PLAYER_SIT_ONE":
                if (gameActive) return;
                chairOneOccupant = client.getPlayer();
                broadcastUpdate("PLAYER_SIT_ONE", chairOneOccupant);
                break;
            case "PLAYER_SIT_TWO":
                if (gameActive) return;
                chairTwoOccupant = client.getPlayer();
                broadcastUpdate("PLAYER_SIT_TWO", chairTwoOccupant);
                break;
            case "PLAYER_LEAVE_ONE":
                if (gameActive) return;
                if (chairTwoOccupant != null) {
                    chairOneOccupant = chairTwoOccupant;
                    chairTwoOccupant = null;
                    betAmount = 0;
                    broadcastUpdate("PLAYER_LEAVE_TWO", null);
                    broadcastUpdate("PLAYER_LEAVE_ONE", null);
                    broadcastUpdate("PLAYER_SIT_ONE", chairOneOccupant);
                } else {
                    chairOneOccupant = null;
                    betAmount = 0;
                    broadcastUpdate("PLAYER_LEAVE_ONE", null);
                }
                break;
            case "PLAYER_LEAVE_TWO":
                if (gameActive) return;
                chairTwoOccupant = null;
                broadcastUpdate("PLAYER_LEAVE_TWO", null);
                break;
            case "PLAYER_SUBMIT_BET":
                if (gameActive) return;
                if (chairOneOccupant != null) {
                    if (betAmount == 0) {
                        betAmount = (int) data;
                        broadcastUpdate("PLAYER_SUBMIT_BET", data);
                    } else {
                        betAmount = 0;
                        broadcastUpdate("PLAYER_CANCEL_BET", null);
                    }
                }
                break;
            case "PLAYER_ACCEPT_BET":
                if (gameActive) return;
                if (chairTwoOccupant != null && betAmount != 0) {
                    broadcastUpdate("PLAYER_ACCEPT_BET", data);
                    gameActive = true;
                    betAmount = betAmount * 2;
                    picks.clear();
                    startTimer();
                }
                break;
            case "PLAYER_CHOOSE":
                handlePlayerChoose(client, data);
                break;
            case "ANIMATION_FINISHED":
                if (data instanceof Integer w) {
                    resolveRound(committedWinner != null ? committedWinner : w);
                }
                break;
            case "GET_CHAIRS":
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
                break;
        }
    }

    private void handlePlayerChoose(Client client, Object data) {
        if (!gameActive) return;
        if (!(data instanceof Throw chosen)) return;

        UUID chooserId = client.getPlayer().getUniqueId();
        boolean isChairOne = chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(chooserId);
        boolean isChairTwo = chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(chooserId);
        if (!isChairOne && !isChairTwo) return;
        if (picks.containsKey(chooserId)) return;

        picks.put(chooserId, chosen);

        UUID opponentId = opponentOf(chooserId);
        if (opponentId != null) {
            Client opponentClient = clients.get(opponentId);
            if (opponentClient != null) {
                opponentClient.onServerUpdate("OPPONENT_LOCKED_IN", null);
            }
        }

        evaluatePicks();
    }

    private UUID opponentOf(UUID playerId) {
        if (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(playerId)) {
            return chairTwoOccupant != null ? chairTwoOccupant.getUniqueId() : null;
        }
        if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(playerId)) {
            return chairOneOccupant != null ? chairOneOccupant.getUniqueId() : null;
        }
        return null;
    }

    /**
     * Compares both picks once they're both in. A tie clears both picks and
     * lets the same accepted-bet round continue (pot untouched) rather than
     * resolving anything, so the disconnect policy can keep treating the
     * whole span as a single "ride to result" session.
     */
    private void evaluatePicks() {
        if (chairOneOccupant == null || chairTwoOccupant == null) return;

        Throw one = picks.get(chairOneOccupant.getUniqueId());
        Throw two = picks.get(chairTwoOccupant.getUniqueId());
        if (one == null || two == null) return;

        // Stop the pick-timer countdown for the ~70-tick reveal window
        // regardless of outcome -- nothing to pick during the animation,
        // and a fresh timer starts once a tie actually rethrows.
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }

        if (one == two) {
            broadcastUpdate("TIE_REVEAL", new Object[]{one, two});
            // Deliberately not resolved from a client callback the way the
            // decisive branch is: there's no payout at stake on a tie, so a
            // single fixed delay (matching the client's cycle-then-settle
            // animation length) is all that's needed before the same
            // accepted round throws again.
            Bukkit.getScheduler().runTaskLater(plugin, this::rethrow, 70L);
            return;
        }

        int winner = one.beats(two) ? 0 : 1;
        committedWinner = winner;

        broadcastUpdate("REVEAL", new Object[]{one, two, winner});
        // Authoritative fallback, same safety net Coin Flip uses: resolves
        // the round even if every client disconnects before its local
        // reveal animation reports back.
        Bukkit.getScheduler().runTaskLater(plugin, () -> resolveRound(committedWinner != null ? committedWinner : winner), 70L);
    }

    /**
     * Fires ~70 ticks after a tied REVEAL -- clears both picks and lets the
     * same accepted round throw again with a fresh pick timer. A no-op if
     * the round was already torn down (kick/shutdown) during the reveal.
     */
    private void rethrow() {
        if (!gameActive) return;

        picks.clear();
        broadcastUpdate("RETHROW", null);
        timeLeft = plugin.getTimer(internalName);
        startTimer();
    }

    /**
     * Authoritative resolution for a decisive round, called either by
     * whichever seated client's local reveal animation reports back first,
     * or by the server's own fixed-delay fallback timer scheduled alongside
     * the REVEAL broadcast -- whichever fires first wins, the other becomes
     * a safe no-op via the gameActive guard.
     */
    private void resolveRound(int winner) {
        if (!gameActive) return;

        broadcastUpdate("ANIMATION_FINISHED", winner);
        Player payoutOne = chairOneOccupant;
        Player payoutTwo = chairTwoOccupant;
        int payout = betAmount;
        gameActive = false;
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
    }

    /**
     * Neither seated player locked in a choice before the pick timer
     * expired -- there's no one to award the pot to, so both stakes are
     * simply refunded and the round is void.
     */
    private void voidRound() {
        if (!gameActive) return;

        broadcastUpdate("ROUND_VOID", null);
        Player payoutOne = chairOneOccupant;
        Player payoutTwo = chairTwoOccupant;
        int stake = betAmount / 2;
        gameActive = false;
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

    /** Shared post-round seat cleanup for whichever occupant's client already disconnected. */
    private void reseatDisconnectedOccupants() {
        if (chairOneOccupant != null && !hasClient(chairOneOccupant.getUniqueId())) {
            if (chairTwoOccupant != null) {
                chairOneOccupant = chairTwoOccupant;
                chairTwoOccupant = null;
                betAmount = 0;
                broadcastUpdate("PLAYER_LEAVE_TWO", null);
                broadcastUpdate("PLAYER_LEAVE_ONE", null);
                broadcastUpdate("PLAYER_SIT_ONE", chairOneOccupant);
            } else {
                chairOneOccupant = null;
                betAmount = 0;
                broadcastUpdate("PLAYER_LEAVE_ONE", null);
            }
        }
        if (chairTwoOccupant != null && !hasClient(chairTwoOccupant.getUniqueId())) {
            chairTwoOccupant = null;
            broadcastUpdate("PLAYER_LEAVE_TWO", null);
        }
    }

    private void handlePayout(Player one, Player two, int payout, int winner) {
        UUID winnerId = (winner == 0)
            ? (one != null ? one.getUniqueId() : null)
            : (two != null ? two.getUniqueId() : null);
        UUID loserId = (winner == 0)
            ? (two != null ? two.getUniqueId() : null)
            : (one != null ? one.getUniqueId() : null);

        // Resolve fresh Player references by UUID rather than trusting the
        // cached chair-occupant objects, which can go stale across a
        // reconnect while the game was active (seating is locked while
        // gameActive, so those fields can't be refreshed mid-round).
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
     * Settles an in-flight round during shutdown. Once a winner has been
     * committed (decisive reveal or a timeout forfeit), that authoritative
     * payout is saved; before that, each side's stake is refunded -- this
     * covers the pick phase and any tie-rethrow loop the round was in.
     */
    void refundForShutdown() {
        if (!gameActive) {
            return;
        }

        int payout = betAmount;
        int stake = payout / 2;
        Integer winner = committedWinner;
        Player payoutOne = chairOneOccupant;
        Player payoutTwo = chairTwoOccupant;
        gameActive = false;
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
            if (payoutOne != null && stake > 0) {
                queuePendingPayout(payoutOne.getUniqueId(), stake, PayoutMessages.serverRestartRefundContext("Rock Paper Scissors"));
            }
            if (payoutTwo != null && stake > 0) {
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
    }

    /**
     * Kicked players forfeit unconditionally, regardless of round phase --
     * no refund, no pending payout. Pregame, this clears their seat/bet
     * state directly (bypassing the normal PLAYER_LEAVE broadcast so their
     * own already-removed client can't self-refund). Mid-round, it just
     * marks them so handlePayout/refund helpers deny them even if they'd
     * have won -- the round's own post-payout cleanup frees the seat once
     * it ends.
     */
    void forfeitPlayer(UUID playerId) {
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
                broadcastUpdate("PLAYER_LEAVE_TWO", null);
                broadcastUpdate("PLAYER_LEAVE_ONE", null);
                broadcastUpdate("PLAYER_SIT_ONE", chairOneOccupant);
            } else {
                chairOneOccupant = null;
                betAmount = 0;
                broadcastUpdate("PLAYER_LEAVE_ONE", null);
            }
        } else if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(playerId)) {
            chairTwoOccupant = null;
            broadcastUpdate("PLAYER_LEAVE_TWO", null);
        }
    }

    void registerRidingSession(UUID playerId) {
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

            broadcastUpdate("UPDATE_TIMER", timeLeft);
            timeLeft--;

            if (timeLeft <= 3) {
                playCountdownSound();
            }

        }, 0L, 20L); // Run every second
    }

    /**
     * The pick timer ran out. If exactly one seated player locked in a
     * throw, they win by forfeit; if neither did, there's no one to award
     * the pot to, so the round is voided instead.
     */
    private void handleTimeout() {
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }

        UUID oneId = chairOneOccupant != null ? chairOneOccupant.getUniqueId() : null;
        UUID twoId = chairTwoOccupant != null ? chairTwoOccupant.getUniqueId() : null;
        boolean oneChose = oneId != null && picks.containsKey(oneId);
        boolean twoChose = twoId != null && picks.containsKey(twoId);

        if (oneChose == twoChose) {
            voidRound();
            return;
        }

        int winner = oneChose ? 0 : 1;
        committedWinner = winner;
        broadcastUpdate("FORFEIT_TIMEOUT", winner);
        Bukkit.getScheduler().runTaskLater(plugin, () -> resolveRound(committedWinner != null ? committedWinner : winner), 20L);
    }

}
