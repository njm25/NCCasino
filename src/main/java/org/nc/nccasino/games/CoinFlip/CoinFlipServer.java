package org.nc.nccasino.games.CoinFlip;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;

public class CoinFlipServer extends Server {


    protected Player chairOneOccupant;
    protected Player chairTwoOccupant;
    protected int chairOneBet;
    protected Boolean gameActive;
    
    public CoinFlipServer(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, plugin, internalName);
            
        this.chairOneOccupant = null;
        this.chairTwoOccupant = null;
        this.chairOneBet = 0;
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
                chairOneOccupant = client.getPlayer();
                broadcastUpdate("PLAYER_SIT_ONE", chairOneOccupant);
                break;
            case"PLAYER_SIT_TWO":
                chairTwoOccupant = client.getPlayer();
                broadcastUpdate("PLAYER_SIT_TWO", chairTwoOccupant);
                break;
            case"PLAYER_LEAVE_ONE":
                if(chairTwoOccupant != null){
                    chairOneOccupant = chairTwoOccupant;
                    chairTwoOccupant = null;
                    chairOneBet = 0;
                    broadcastUpdate("PLAYER_LEAVE_TWO", null);
                    broadcastUpdate("PLAYER_LEAVE_ONE", null);
                    broadcastUpdate("PLAYER_SIT_ONE", chairOneOccupant);
                } else {
                    chairOneOccupant = null;
                    chairOneBet = 0;
                    broadcastUpdate("PLAYER_LEAVE_ONE", null);
                }
                break;
            case"PLAYER_LEAVE_TWO":
                chairTwoOccupant = null;
                broadcastUpdate("PLAYER_LEAVE_TWO", null);
                break;
            case "PLAYER_SUBMIT_BET":
                if(chairOneOccupant != null){
                    if(chairOneBet == 0){
                        chairOneBet = (int) data;
                        broadcastUpdate("PLAYER_SUBMIT_BET", data);
                    }
                    else{
                        chairOneBet = 0;
                        broadcastUpdate("PLAYER_CANCEL_BET", null);
                    }
                }
                break;
            case "PLAYER_ACCEPT_BET":
                if(chairTwoOccupant != null && chairOneBet != 0){
                    broadcastUpdate("PLAYER_ACCEPT_BET", null);
                    gameActive = true;
                }
                break;
            
            case "GET_CHAIRS":
                Object[] chairs = {
                    (chairOneOccupant != null) ? chairOneOccupant : null,
                    (chairTwoOccupant != null) ? chairTwoOccupant : null,
                    chairOneBet,
                    gameActive,
                };
                client.onServerUpdate("GET_CHAIRS", chairs);
                break;
        }
    }
    
}
