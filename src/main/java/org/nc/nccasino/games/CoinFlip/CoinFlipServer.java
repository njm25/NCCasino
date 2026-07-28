package org.nc.nccasino.games.CoinFlip;

import java.util.HashSet;
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

public class CoinFlipServer extends Server {


    private int countdownTaskId = -1;
    private int timeLeft = 0;

    protected Player chairOneOccupant;
    protected Player chairTwoOccupant;
    protected int betAmount;
    protected Boolean gameActive;
    private Integer committedWinner;
    private final Set<UUID> forfeited = new HashSet<>();

    public CoinFlipServer(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, plugin, internalName);
            
        this.chairOneOccupant = null;
        this.chairTwoOccupant = null;
        this.betAmount = 0;
        this.gameActive = false;
    }

    @Override
    protected Client createClientForPlayer(Player player) {
        CoinFlipClient client = new CoinFlipClient(this, player, plugin, internalName);
        return client;
    }

    @Override
    public void onClientUpdate(Client client, String eventType, Object data) {
        switch (eventType){
            case"PLAYER_SIT_ONE":
                if(gameActive) return;
                chairOneOccupant = client.getPlayer();
                broadcastUpdate("PLAYER_SIT_ONE", chairOneOccupant);
                break;
            case"PLAYER_SIT_TWO":
                if(gameActive) return;
                chairTwoOccupant = client.getPlayer();
                broadcastUpdate("PLAYER_SIT_TWO", chairTwoOccupant);
                break;
            case"PLAYER_LEAVE_ONE":
                if(gameActive) return;
                if(chairTwoOccupant != null){
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
            case"PLAYER_LEAVE_TWO":
                if(gameActive) return;
                chairTwoOccupant = null;
                broadcastUpdate("PLAYER_LEAVE_TWO", null);
                break;
            case "PLAYER_SUBMIT_BET":
                if(gameActive) return;
                if(chairOneOccupant != null){
                    if(betAmount == 0){
                        betAmount = (int) data;
                        broadcastUpdate("PLAYER_SUBMIT_BET", data);
                    }
                    else{
                        betAmount = 0;
                        broadcastUpdate("PLAYER_CANCEL_BET", null);
                    }
                }
                break;
            case "PLAYER_ACCEPT_BET":
                if(gameActive) return;
                if(chairTwoOccupant != null && betAmount != 0){
                    broadcastUpdate("PLAYER_ACCEPT_BET", data);
                    gameActive = true;
                    betAmount = betAmount * 2;
                    startTimer();
                }
                break;
            case "ANIMATION_FINISHED":
                if (data instanceof Integer w) {
                    resolveRound(committedWinner != null ? committedWinner : w);
                }
                break;
            case "GET_CHAIRS":
                Object[] chairs = {
                    (chairOneOccupant != null) ? chairOneOccupant : null,
                    (chairTwoOccupant != null) ? chairTwoOccupant : null,
                    betAmount,
                    gameActive,
                    timeLeft,
                };
                client.onServerUpdate("GET_CHAIRS", chairs);
                break;
        }
    }

    /**
     * Authoritative resolution for a round, called either by whichever
     * seated client's local flip animation reports back first, or by the
     * server's own fixed-delay fallback timer scheduled alongside the
     * WINNER broadcast — whichever fires first wins, the other becomes a
     * safe no-op via the gameActive guard. This no longer depends on any
     * client actually being present to report an animation as finished:
     * previously, if both seated players disconnected before either one's
     * client-side animation completed, the payout would never run at all.
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
        handlePayout(payoutOne, payoutTwo, payout, winner);
        forfeited.clear();
        if(chairOneOccupant !=null && !hasClient(chairOneOccupant.getUniqueId())){
            if(chairTwoOccupant != null){
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
        if(chairTwoOccupant !=null && !hasClient(chairTwoOccupant.getUniqueId())){
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
                sendPayoutMessage(winnerPlayer, payout, true, payout/2);
                applyWinEffects(winnerPlayer);
            } else {
                queuePendingPayout(winnerId, payout);
            }
        }

        if (loserId != null && !forfeited.contains(loserId)) {
            Player loserPlayer = Bukkit.getPlayer(loserId);
            if (loserPlayer != null && loserPlayer.isOnline()) {
                sendPayoutMessage(loserPlayer, payout, false, payout/2);
                applyLoseEffects(loserPlayer);
            }
        }
    }

    private void queuePendingPayout(UUID playerId, double amount) {
        queuePendingPayout(playerId, amount, PayoutMessages.disconnectedMidGameContext("Coin Flip"));
    }

    private void queuePendingPayout(UUID playerId, double amount, String context) {
        Material currencyMaterial = plugin.getCurrency(internalName);
        PendingPayout pendingPayout = PendingPayout.create(
            playerId,
            "Coin Flip",
            internalName,
            currencyMode,
            currencyMaterial != null ? currencyMaterial.name() : null,
            currencyName,
            amount,
            context
        );
        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(pendingPayout);
        if (!persisted) {
            plugin.getLogger().warning("[NCCasino] Coin Flip pending payout failed to persist for " + playerId + ".");
        }
    }

    /**
     * Settles an accepted flip during shutdown. Once a winner has been
     * selected, that authoritative payout is saved; before selection,
     * each side's stake is refunded.
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

        if (winner != null) {
            Player winningPlayer = winner == 0 ? payoutOne : payoutTwo;
            if (winningPlayer != null && payout > 0
                && !forfeited.contains(winningPlayer.getUniqueId())) {
                queuePendingPayout(
                    winningPlayer.getUniqueId(),
                    payout,
                    "The server restarted after your Coin Flip result was determined. Your payout was saved."
                );
            }
        } else {
            if (payoutOne != null && stake > 0) {
                queuePendingPayout(payoutOne.getUniqueId(), stake, PayoutMessages.serverRestartRefundContext("Coin Flip"));
            }
            if (payoutTwo != null && stake > 0) {
                queuePendingPayout(payoutTwo.getUniqueId(), stake, PayoutMessages.serverRestartRefundContext("Coin Flip"));
            }
        }
        forfeited.clear();
    }

    /**
     * Kicked players forfeit unconditionally, regardless of round phase —
     * no refund, no pending payout. Pregame, this clears their seat/bet
     * state directly (bypassing the normal PLAYER_LEAVE broadcast so their
     * own already-removed client can't self-refund). Mid-round, it just
     * marks them so handlePayout denies them even if they'd have won —
     * resolveRound's existing post-payout cleanup frees the seat once the
     * round ends.
     */
    void forfeitPlayer(UUID playerId) {
        forfeited.add(playerId);

        if (gameActive) {
            return;
        }

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

    private void startTimer() {
        if (countdownTaskId != -1) return; // Timer is already running

        gameState = GameState.WAITING;
        timeLeft = plugin.getTimer(internalName);

        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (timeLeft <= 0) {
                Bukkit.getScheduler().cancelTask(countdownTaskId);
                countdownTaskId = -1;
                // Otherwise, start the game
                int winner = Math.random() < 0.5 ? 0 : 1;
                committedWinner = winner;
                broadcastUpdate("WINNER", winner);
                // Resolve authoritatively on a fixed server-side delay
                // rather than relying solely on a client to report its
                // animation as finished — comfortably past the ~55-tick
                // client-side animation, so in the normal case the
                // client-driven ANIMATION_FINISHED still resolves first
                // and this becomes a harmless no-op.
                Bukkit.getScheduler().runTaskLater(plugin, () -> resolveRound(winner), 70L);
                return;
            }

            broadcastUpdate("UPDATE_TIMER", timeLeft);
            timeLeft--;

            if (timeLeft <= 3) {
                playCountdownSound();
            }

        }, 0L, 20L); // Run every second
    }

}
