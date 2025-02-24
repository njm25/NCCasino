package org.nc.nccasino.games.CoinFlip;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;

public class CoinFlipClient extends Client {

    private enum SlotOption
    {
        HANDLE_CHAIR_1,
        HANDLE_CHAIR_2,
        HANDLE_SUBMIT_BET,
    }
    protected final Map<SlotOption, Integer> slotMapping = new HashMap<>();

    protected Player chairOneOccupant;
    protected Player chairTwoOccupant;

    private final String clickHereToSit = "Click here to sit";
    
    public CoinFlipClient(Server server, Player player, Nccasino plugin, String internalName) {
        super(server, player, "Coin Flip", plugin, internalName);
        this.chairOneOccupant = null;
        this.chairTwoOccupant = null;

        initializeUI(false, true);
        
    }

    @Override
    public void initializeUI(boolean rebetSwitch, boolean betSlip) {
        super.initializeUI(rebetSwitch, betSlip);
        slotMapping.put(SlotOption.HANDLE_CHAIR_1,20);
        slotMapping.put(SlotOption.HANDLE_CHAIR_2, 24);

        slotMapping.put(SlotOption.HANDLE_SUBMIT_BET, 43);

        sendUpdateToServer("GET_CHAIRS", null);
 
    }

    /*
     * 
     * CLIENT INTERACTION
     * 
     */

    @Override
    protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
        SlotOption option = getKeyByValue(slotMapping, slot);
        if (option == null) return;
        switch(option)
        {
            case HANDLE_CHAIR_1:
                handleChairOne();
                break;
            case HANDLE_CHAIR_2:
                handleChairTwo();
                break;
            case HANDLE_SUBMIT_BET:
                handleSubmitBet();
                break;
        }
    }

    private void handleChairOne(){
        if (chairOneOccupant == null){
            chairOneOccupant = player;
            player.sendMessage("You have taken chair one");
            sendUpdateToServer("PLAYER_SIT_ONE", null);
        }
        else if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            sendUpdateToServer("PLAYER_LEAVE_ONE", null);
        }
    }

    private void handleChairTwo(){
        if (chairOneOccupant != null && chairTwoOccupant == null){
            if (chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
                denyAction(player, "You are already seated.");
                return;
            }
            chairTwoOccupant = player;
            player.sendMessage("You have taken chair one");
            sendUpdateToServer("PLAYER_SIT_TWO", null);
        }
        else if(chairTwoOccupant != null){
            if(chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
                sendUpdateToServer("PLAYER_LEAVE_TWO", null);
            }
        }
    }

    private void handleSubmitBet(){

    }

    /*
     * 
     * 
     *  SERVER INTERACTION
     * 
     * 
     */

    @Override
    public void onServerUpdate(String eventType, Object data) {
        switch(eventType) {
            case "PLAYER_SIT_ONE":
                handlePlayerOneSit(data);
                break;
            case "PLAYER_SIT_TWO":
                handlePlayerTwoSit(data);
                break;
            case "PLAYER_LEAVE_ONE":
                handlePlayerOneLeave();
                break;
            case "PLAYER_LEAVE_TWO":
                handlePlayerTwoLeave();
                break;
            case "GET_CHAIRS":
                handleGetChairs(data);
                break;
            default:
                break;
        }
    }

    private void handlePlayerOneSit(Object data){
        Player playerData = (Player) data; // Use the PlayerData wrapper class
        chairOneOccupant = playerData;
        inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1), 
        createPlayerHead(playerData.getUniqueId(), playerData.getDisplayName()));
        addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_2));
    }

    private void handlePlayerTwoSit(Object data){
        Player playerData2 = (Player) data; // Use the PlayerData wrapper class
        chairTwoOccupant = playerData2;
        inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2), 
        createPlayerHead(playerData2.getUniqueId(), playerData2.getDisplayName()));
    }

    private void handlePlayerOneLeave(){
        chairOneOccupant = null;
        addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
        if(chairTwoOccupant == null){
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2), null);
        }
    }

    private void handlePlayerTwoLeave(){
        chairTwoOccupant = null;
        addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_2));
    }

    private void handleGetChairs(Object data){
        Object[] occupants = (Object[]) data;
        PlayerData chairOne = (occupants.length > 0 && occupants[0] instanceof PlayerData) ? (PlayerData) occupants[0] : null;
        PlayerData chairTwo = (occupants.length > 1 && occupants[1] instanceof PlayerData) ? (PlayerData) occupants[1] : null;
        if (chairOne == null && chairTwo == null) {
            addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
        } else if (chairOne != null && chairTwo != null) {
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1), 
                createPlayerHead(chairOne.getPlayer().getUniqueId(), chairOne.getPlayer().getDisplayName()));
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2), 
                createPlayerHead(chairTwo.getPlayer().getUniqueId(), chairTwo.getPlayer().getDisplayName()));

            Player player1 = chairOne.getPlayer();
            Player player2 = chairTwo.getPlayer();

            chairOneOccupant = player1;
            chairTwoOccupant = player2;
        } else if (chairOne != null) {
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1), 
                createPlayerHead(chairOne.getPlayer().getUniqueId(), chairOne.getPlayer().getDisplayName()));
            Player player = chairOne.getPlayer();
            chairOneOccupant = player;
            addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_2));
        } else {
            addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2), 
                createPlayerHead(chairTwo.getPlayer().getUniqueId(), chairTwo.getPlayer().getDisplayName()));
        }
    }

    @Override
    protected void handleClientInventoryClose() {
        if(player == chairOneOccupant){
            sendUpdateToServer("PLAYER_LEAVE_ONE", null);
        }
        if(player == chairTwoOccupant){
            sendUpdateToServer("PLAYER_LEAVE_TWO", null);
        }
        super.handleClientInventoryClose();
    }
}
