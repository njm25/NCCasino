package org.nc.nccasino.games.CoinFlip;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.helpers.SchedulerHelper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class CoinFlipServer extends Server {


    private ScheduledTask countdownTask = null;
    private int timeLeft = 0;
    
    protected Player chairOneOccupant;
    protected Player chairTwoOccupant;
    protected int betAmount;
    protected Boolean gameActive;
    
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
                if(gameActive){
                    broadcastUpdate("ANIMATION_FINISHED", data);
                    Player payoutOne = chairOneOccupant;
                    Player payoutTwo = chairTwoOccupant;
                    int payout = betAmount;
                    gameActive = false;
                    betAmount = 0; 
                    timeLeft = 0;
                    countdownTask = null;
                    handlePayout(payoutOne, payoutTwo, payout, (int) data);
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

    private void handlePayout(Player one, Player two, int payout, int winner) {
        Player winnerPlayer = (winner == 0) ? one : two;
        Player loserPlayer = (winner == 0) ? two : one;
    
        // Handle payout and messages for the winner
        if (winnerPlayer != null && payout > 0) {
            creditPlayer(winnerPlayer, payout);
            sendPayoutMessage(winnerPlayer, payout, true, payout/2);
            applyWinEffects(winnerPlayer);
        }
    
        // Handle losing message and effects for the loser
        if (loserPlayer != null) {
            sendPayoutMessage(loserPlayer, payout, false, payout/2);
            applyLoseEffects(loserPlayer);
        }
    }

    
    private void startTimer() {
        if (countdownTask != null) return; // Timer is already running

        gameState = GameState.WAITING;
        timeLeft = plugin.getTimer(internalName);
           
        countdownTask = SchedulerHelper.executeRegionTaskTimer(plugin, plugin.getServer().getWorlds().get(0).getSpawnLocation(), () -> {
            if (timeLeft <= 0) {
                if (countdownTask != null) {
                    countdownTask.cancel();
                    countdownTask = null;
                }
                if (clients.isEmpty()) {
                    Bukkit.broadcastMessage("No viewers and no bets - pausing game.");
                    return;
                }

                // Otherwise, start the game
                int winner = Math.random() < 0.5 ? 0 : 1;
                broadcastUpdate("WINNER", winner);
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
