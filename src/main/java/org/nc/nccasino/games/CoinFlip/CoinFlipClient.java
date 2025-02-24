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
        LEAVE
    }
    protected final Map<SlotOption, Integer> slotMapping = new HashMap<>();

    protected Player chairOneOccupant;
    protected Player chairTwoOccupant;

    protected int betAmount = 0;

    private final String clickHereToSit = "Click here to sit";
    private boolean gameActive = false;
    
    public CoinFlipClient(Server server, Player player, Nccasino plugin, String internalName) {
        super(server, player, "Coin Flip", plugin, internalName);
        this.chairOneOccupant = null;
        this.chairTwoOccupant = null;

        slotMapping.put(SlotOption.HANDLE_CHAIR_1,20);
        slotMapping.put(SlotOption.HANDLE_CHAIR_2, 24);
        slotMapping.put(SlotOption.LEAVE, 36);
        slotMapping.put(SlotOption.HANDLE_SUBMIT_BET, 44);

        
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Leave", slotMapping.get(SlotOption.LEAVE));
        populateGlassPattern();
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
                if (gameActive) return;
                handleChairOne();
                break;
            case HANDLE_CHAIR_2:
                if (gameActive) return;
                handleChairTwo();
                break;
            case HANDLE_SUBMIT_BET:
                if (gameActive) return;
                handleSubmitBet();
                break;
            case LEAVE:
                player.closeInventory();
                break;
        }
    }
    
    @Override
    protected void handleClientInventoryClose() {
        if(!gameActive){
            if(player == chairOneOccupant){
                sendUpdateToServer("PLAYER_LEAVE_ONE", null);
            }
            if(player == chairTwoOccupant){
                sendUpdateToServer("PLAYER_LEAVE_TWO", null);
            }
        }
        super.handleClientInventoryClose();
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

    private void handleSubmitBet() {
        if (chairOneOccupant!=null && chairOneOccupant.getUniqueId().equals(player.getUniqueId())) {
            if(!betStack.isEmpty()){
                int totalBet = (int) betStack.stream().mapToDouble(Double::doubleValue).sum();
                if(totalBet > 0){
                    sendUpdateToServer("PLAYER_SUBMIT_BET", totalBet);
                }
            }
        } else if (chairTwoOccupant !=null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())) {
            sendUpdateToServer("PLAYER_ACCEPT_BET", null);
        }
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
            case "PLAYER_SUBMIT_BET":
                handleSubmitBet((int) data);
                break;
            case "PLAYER_CANCEL_BET":
                handleCancelBet();
                break;
            case "PLAYER_ACCEPT_BET":
                handleAcceptBet();
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
        if(playerData.getUniqueId().equals(player.getUniqueId())){
            initializeUI(false, true);
            resetPlayerOneUI();
            addItemAndLore(Material.OAK_STAIRS, 1, "Player 2's Seat", slotMapping.get(SlotOption.HANDLE_CHAIR_2));
        }
        else{
            addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_2));
        }
        hidePotChest();
        betAmount = 0;
        chairOneOccupant = playerData;
        inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1), 
        createPlayerHead(playerData.getUniqueId(), playerData.getDisplayName()));
    }

    private void handlePlayerTwoSit(Object data){
        Player playerData2 = (Player) data; // Use the PlayerData wrapper class
        if (playerData2.getUniqueId().equals(player.getUniqueId())){

            String lore = betAmount == 0 ? "Waiting for " + chairOneOccupant.getDisplayName() + "'s bet" : "Click to accept bet";
            String name = betAmount == 0 ? "Waiting " : "Accept Bet";
            addItemAndLore(Material.LEVER
            , 1
            , name
            , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
            , lore
        );
        }
        chairTwoOccupant = playerData2;
        inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2), 
        createPlayerHead(playerData2.getUniqueId(), playerData2.getDisplayName()));
    }

    private void handlePlayerOneLeave(){
        if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            clearBettingRow();
            replaceBottomRow();
            if(!betStack.isEmpty()){
                undoAllBets();
                updateBetLore(53, 0);
            }
            clearHandleButton();
        }
        hidePotChest();
        chairOneOccupant = null;
        addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
        if(chairTwoOccupant == null){
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2), null);
        }
    }

    private void handlePlayerTwoLeave(){
        
        addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_2));
        if(chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
            clearHandleButton();
        }
        else if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            
            addItemAndLore(Material.OAK_STAIRS, 1, "Player 2's Seat", slotMapping.get(SlotOption.HANDLE_CHAIR_2));
        }
        chairTwoOccupant = null;
    }

    private void handleSubmitBet(int data){
        betAmount = data;
        if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            if(chairTwoOccupant == null){            
                addItemAndLore(Material.LEVER, 1, "Waiting for Player 2", slotMapping.get(SlotOption.HANDLE_SUBMIT_BET), "Click to cancel bet");
            }
            else{
                addItemAndLore(Material.LEVER, 1, "Waiting for " + chairTwoOccupant.getDisplayName(), slotMapping.get(SlotOption.HANDLE_SUBMIT_BET), "Click to cancel bet");

            }
            bettingEnabled = false;
            replaceBottomRow();
        }
        else if(chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
            addItemAndLore(Material.LEVER, 1, "Accept Bet", slotMapping.get(SlotOption.HANDLE_SUBMIT_BET), "Click to accept bet\nCurrent: §a" + betAmount);
        }
        
        addItemAndLore(Material.CHEST, 1, "Pot", 40, "Current: §a" + betAmount);
    }
                        
    private void handleCancelBet(){
        if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            undoAllBets();
            updateBetLore(53, 0);
            resetPlayerOneUI();
            bettingEnabled = true;
            betAmount = 0;
            initializeUI(rebetEnabled, bettingEnabled);
        }
        else if(chairTwoOccupant!= null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
            resetPlayerTwoUI();
        }
        hidePotChest();
    }

    private void  handleAcceptBet(){
        System.out.println("Bet accepted");
        gameActive = true;
    }


    /*
     * 
     * 
     * UI FUNCTIONS
     * 
     * 
     * 
     */

    private void handleGetChairs(Object data){
        Object[] dataArr = (Object[]) data;
        Player chairOne = (dataArr.length > 0 && dataArr[0] instanceof Player) ? (Player) dataArr[0] : null;
        Player chairTwo = (dataArr.length > 1 && dataArr[1] instanceof Player) ? (Player) dataArr[1] : null;
        int betAmount = (dataArr.length > 2 && dataArr[2] instanceof Integer) ? (int) dataArr[2] : 0;
        gameActive = (dataArr.length > 3 && dataArr[3] instanceof Boolean) ? (boolean) dataArr[3] : false;
        if (chairOne == null && chairTwo == null) {
            addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
        } else if (chairOne != null && chairTwo != null) {
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1), 
                createPlayerHead(chairOne.getUniqueId(), chairOne.getDisplayName()));
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2), 
                createPlayerHead(chairTwo.getUniqueId(), chairTwo.getDisplayName()));

            Player player1 = chairOne;
            Player player2 = chairTwo;

            chairOneOccupant = player1;
            chairTwoOccupant = player2;
        } else if (chairOne != null) {
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1), 
                createPlayerHead(chairOne.getUniqueId(), chairOne.getDisplayName()));
            Player player = chairOne;
            chairOneOccupant = player;
            addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_2));
        } else {
            addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2), 
                createPlayerHead(chairTwo.getUniqueId(), chairTwo.getDisplayName()));
        }
        if(betAmount!=0){
            this.betAmount = betAmount;
            addItemAndLore(Material.CHEST, 1, "Pot", 40, "Current: §a" + betAmount);
        }
    }

    
    private void clearHandleButton(){
        inventory.setItem(slotMapping.get(SlotOption.HANDLE_SUBMIT_BET), null);
    }

    private void populateGlassPattern() {
        // Define materials
        Material blackPane = Material.BLACK_STAINED_GLASS_PANE;
        Material limePane = Material.LIME_STAINED_GLASS_PANE;
        String paneName = "";
    
        // Define slot positions for lime stained glass panes
        int[] limeSlots = {10, 11, 12, 13, 14, 15, 16, 19, 21, 22, 23, 25, 28, 29, 30, 32, 33, 34};
    
        // Define slot positions for black stained glass panes
        int[] blackSlots = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 
            9, 17, 
            18, 26, 
            27, 35, 
            37, 38, 39, 40, 41, 42, 43,
            45, 46, 47, 48, 49, 50, 51, 52, 53
        };
    
        // Place black stained glass panes
        for (int slot : blackSlots) {
            addItemAndLore(blackPane, 1, paneName, slot);
        }
    
        // Place lime stained glass panes
        for (int slot : limeSlots) {
            addItemAndLore(limePane, 1, paneName, slot);
        }

        addItemAndLore(Material.SUNFLOWER, 1, "Coin", 31);

    }

    private void replaceBottomRow() {
        Material blackPane = Material.BLACK_STAINED_GLASS_PANE;
        String paneName = "";
        for(int i = 45; i < 54; i++){
            addItemAndLore(blackPane, 1, paneName, i);
        }
    }

    private void resetPlayerOneUI(){
        addItemAndLore(Material.LEVER, 1, "Submit bet", slotMapping.get(SlotOption.HANDLE_SUBMIT_BET), "Click here to submit your bet");
        hidePotChest();
    }

    private void resetPlayerTwoUI(){
        addItemAndLore(Material.LEVER
            , 1
            , "Waiting "
            , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
            , "Waiting for " + chairOneOccupant.getDisplayName() + "'s bet"
        );
        hidePotChest();
    }

    private void hidePotChest(){
        addItemAndLore(Material.BLACK_STAINED_GLASS_PANE, 1, "", 40);
    }

}
