package org.nc.nccasino.games;




import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class RouletteInventory extends DealerInventory {
private double selectedWager;
private int pageNum;
//private final DealerInventory inventory;
//private final ItemStack[] wagerItems;


    public RouletteInventory(UUID dealerId) {
        super(dealerId, 54, "Roulette Table"); // Using 27 slots as an example
        initializeRoulette();
    }






    // Initialize Roulette-specific inventory items
    private void initializeRoulette() {
        addItem(createCustomItem(Material.RED_WOOL, "Start Roulette"), 43);
        pageNum=1;
        // Add other Roulette-related items here
    }

    // Create an item stack with a custom display name
    private ItemStack createCustomItem(Material material, String name) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }
  /* 
    @Override
    public void handleClick(int slot, Player player) {
        switch (slot) {
            case 43:
                player.sendMessage("Starting Roulette...");
                
                addItem(createCustomItem(Material.RED_WOOL, "1"), 0);
                addItem(createCustomItem(Material.BLACK_WOOL, "2"), 1);
                addItem(createCustomItem(Material.RED_WOOL, "3"), 2);
                addItem(createCustomItem(Material.BLACK_WOOL, "4"), 3);
                addItem(createCustomItem(Material.RED_WOOL, "5"), 4);
                addItem(createCustomItem(Material.BLACK_WOOL, "6"), 5);
                addItem(createCustomItem(Material.RED_WOOL, "7"), 6);
                addItem(createCustomItem(Material.BLACK_WOOL, "8"), 7);
                addItem(createCustomItem(Material.RED_WOOL, "9"), 8);

                addItem(createCustomItem(Material.BLACK_WOOL, "10"), 9);
                addItem(createCustomItem(Material.BLACK_WOOL, "11"), 10);
                addItem(createCustomItem(Material.RED_WOOL, "12"), 11);
                addItem(createCustomItem(Material.BLACK_WOOL, "13"), 12);
                addItem(createCustomItem(Material.RED_WOOL, "14"), 13);
                addItem(createCustomItem(Material.BLACK_WOOL, "15"), 14);
                addItem(createCustomItem(Material.RED_WOOL, "16"), 15);
                addItem(createCustomItem(Material.BLACK_WOOL, "17"), 16);
                addItem(createCustomItem(Material.RED_WOOL, "18"), 17);
                addItem(createCustomItem(Material.RED_WOOL, "19"), 18);
                addItem(createCustomItem(Material.BLACK_WOOL, "20"), 19);
                addItem(createCustomItem(Material.RED_WOOL, "21"), 20);
                addItem(createCustomItem(Material.BLACK_WOOL, "22"), 21);
                addItem(createCustomItem(Material.RED_WOOL, "23"), 22);
                addItem(createCustomItem(Material.BLACK_WOOL, "24"), 23);
                addItem(createCustomItem(Material.RED_WOOL, "25"), 24);
                addItem(createCustomItem(Material.BLACK_WOOL, "26"), 25);
                addItem(createCustomItem(Material.RED_WOOL, "27"), 26);
                addItem(createCustomItem(Material.BLACK_WOOL, "28"), 27);
                addItem(createCustomItem(Material.BLACK_WOOL, "29"), 28);
                addItem(createCustomItem(Material.RED_WOOL, "30"), 29);
                addItem(createCustomItem(Material.BLACK_WOOL, "31"), 30);
                addItem(createCustomItem(Material.RED_WOOL, "32"), 31);
                addItem(createCustomItem(Material.BLACK_WOOL, "33"), 32);
                addItem(createCustomItem(Material.RED_WOOL, "34"), 33);
                addItem(createCustomItem(Material.BLACK_WOOL, "35"), 34);
                addItem(createCustomItem(Material.RED_WOOL, "36"), 35);
                addItem(createCustomItem(Material.GREEN_WOOL, "0"), 36);
                





                 //Add logic for starting the Roulette game
                break;
            default:
                // Handle other slots if needed
                break;
        }
    }
*/


    public void handleClick(int slot, Player player,InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        switch(pageNum) {

            case 1:
            if (slot==43){
                player.sendMessage("Starting Roulette Case1...");
                inventory.clear();
                addItem(createCustomItem(Material.RED_WOOL, "1"), 0);
                addItem(createCustomItem(Material.BLACK_WOOL, "2"), 1);
                addItem(createCustomItem(Material.RED_WOOL, "3"), 2);
                addItem(createCustomItem(Material.BLACK_WOOL, "4"), 3);
                addItem(createCustomItem(Material.RED_WOOL, "5"), 4);
                addItem(createCustomItem(Material.BLACK_WOOL, "6"), 5);
                addItem(createCustomItem(Material.RED_WOOL, "7"), 6);
                addItem(createCustomItem(Material.BLACK_WOOL, "8"), 7);
                addItem(createCustomItem(Material.RED_WOOL, "9"), 8);
    
                addItem(createCustomItem(Material.BLACK_WOOL, "10"), 9);
                addItem(createCustomItem(Material.BLACK_WOOL, "11"), 10);
                addItem(createCustomItem(Material.RED_WOOL, "12"), 11);
                addItem(createCustomItem(Material.BLACK_WOOL, "13"), 12);
                addItem(createCustomItem(Material.RED_WOOL, "14"), 13);
                addItem(createCustomItem(Material.BLACK_WOOL, "15"), 14);
                addItem(createCustomItem(Material.RED_WOOL, "16"), 15);
                addItem(createCustomItem(Material.BLACK_WOOL, "17"), 16);
                addItem(createCustomItem(Material.RED_WOOL, "18"), 17);
                addItem(createCustomItem(Material.RED_WOOL, "19"), 18);
                addItem(createCustomItem(Material.BLACK_WOOL, "20"), 19);
                addItem(createCustomItem(Material.RED_WOOL, "21"), 20);
                addItem(createCustomItem(Material.BLACK_WOOL, "22"), 21);
                addItem(createCustomItem(Material.RED_WOOL, "23"), 22);
                addItem(createCustomItem(Material.BLACK_WOOL, "24"), 23);
                addItem(createCustomItem(Material.RED_WOOL, "25"), 24);
                addItem(createCustomItem(Material.BLACK_WOOL, "26"), 25);
                addItem(createCustomItem(Material.RED_WOOL, "27"), 26);
                addItem(createCustomItem(Material.BLACK_WOOL, "28"), 27);
                addItem(createCustomItem(Material.BLACK_WOOL, "29"), 28);
                addItem(createCustomItem(Material.RED_WOOL, "30"), 29);
                addItem(createCustomItem(Material.BLACK_WOOL, "31"), 30);
                addItem(createCustomItem(Material.RED_WOOL, "32"), 31);
                addItem(createCustomItem(Material.BLACK_WOOL, "33"), 32);
                addItem(createCustomItem(Material.RED_WOOL, "34"), 33);
                addItem(createCustomItem(Material.BLACK_WOOL, "35"), 34);
                addItem(createCustomItem(Material.RED_WOOL, "36"), 35);
                addItem(createCustomItem(Material.GREEN_WOOL, "0"), 36);
                addItem(createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)"), 45);
                addItem(createCustomItem(Material.DIAMOND, "1 DIAMOND CHIP"), 47);
                addItem(createCustomItem(Material.DIAMOND, "5 DIAMOND CHIP"), 48);
                addItem(createCustomItem(Material.DIAMOND, "10 DIAMOND CHIP"), 49);
                addItem(createCustomItem(Material.DIAMOND, "25 DIAMOND CHIP"), 50);
                addItem(createCustomItem(Material.DIAMOND, "50 DIAMOND CHIP"), 51);
                //addItem(createCustomItem(Material.DIAMOND, "Last Page"), 52);
                addItem(createCustomItem(Material.ARROW, "Next Page"), 53);
                pageNum=2;
            }

     
            case 2:
            if (slot==53){
                pageNum=3;
                inventory.clear();
                addItem(createCustomItem(Material.RED_WOOL, "21"), 0);
                    addItem(createCustomItem(Material.BLACK_WOOL, "24"), 1);
                    addItem(createCustomItem(Material.RED_WOOL, "27"), 2);
                    addItem(createCustomItem(Material.RED_WOOL, "30"), 3);
                    addItem(createCustomItem(Material.BLACK_WOOL, "33"), 4);
                    addItem(createCustomItem(Material.RED_WOOL, "36"), 5);
                    addItem(createCustomItem(Material.BLACK_WOOL, "20"), 9);
                    addItem(createCustomItem(Material.RED_WOOL, "23"), 10);
                    addItem(createCustomItem(Material.BLACK_WOOL, "26"), 11);
                    addItem(createCustomItem(Material.BLACK_WOOL, "29"), 12);
                    addItem(createCustomItem(Material.RED_WOOL, "32"), 13);
                    addItem(createCustomItem(Material.BLACK_WOOL, "35"), 14);
                    addItem(createCustomItem(Material.RED_WOOL, "19"), 9);
                    addItem(createCustomItem(Material.BLACK_WOOL, "22"), 10);
                    addItem(createCustomItem(Material.RED_WOOL, "25"), 11);
                    addItem(createCustomItem(Material.BLACK_WOOL, "28"), 12);
                    addItem(createCustomItem(Material.BLACK_WOOL, "31"), 13);
                    addItem(createCustomItem(Material.RED_WOOL, "34"), 14);
                    addItem(createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)"), 45);
                    addItem(createCustomItem(Material.DIAMOND, "1 DIAMOND CHIP"), 47);
                    addItem(createCustomItem(Material.DIAMOND, "5 DIAMOND CHIP"), 48);
                    addItem(createCustomItem(Material.DIAMOND, "10 DIAMOND CHIP"), 49);
                    addItem(createCustomItem(Material.DIAMOND, "25 DIAMOND CHIP"), 50);
                    addItem(createCustomItem(Material.DIAMOND, "50 DIAMOND CHIP"), 51);
                    addItem(createCustomItem(Material.BOW, "Last Page"), 52);
    
                    
    
                }
              

                if (slot >= 47 && slot <= 51) {
                    if (clickedItem != null && clickedItem.getItemMeta() != null) {
                        // Set the selected wager amount based on the clicked item's name
                        String itemName = clickedItem.getItemMeta().getDisplayName();
                        selectedWager = getWagerAmountFromName(itemName);
                        player.sendMessage( "Selected wager: " + selectedWager + " Diamonds");
                    }
                    return;
                }
        
                // Check if a number was clicked for placing a bet
                if (slot >= 0 && slot < 37) {
                    if (clickedItem != null && clickedItem.getItemMeta() != null) {
                        if (hasEnoughWager(player, selectedWager)) {
                            // Place the bet and remove the wager from the player's inventory
                            removeWagerFromInventory(player, selectedWager);
                            String number = clickedItem.getItemMeta().getDisplayName();
                            // STORE BET IN PROPER PERSISTENT STRUCTURE, maybe can payout even if player exits menu?
                            player.sendMessage( "Put "+selectedWager+ " on " + number);
                        } else {
                            player.sendMessage("Not enough Diamonds to place this bet.");
                        }
                    }
                }
            case 3:
                if(slot==52){
                    inventory.clear();
                    addItem(createCustomItem(Material.RED_WOOL, "1"), 0);
                    addItem(createCustomItem(Material.BLACK_WOOL, "2"), 1);
                    addItem(createCustomItem(Material.RED_WOOL, "3"), 2);
                    addItem(createCustomItem(Material.BLACK_WOOL, "4"), 3);
                    addItem(createCustomItem(Material.RED_WOOL, "5"), 4);
                    addItem(createCustomItem(Material.BLACK_WOOL, "6"), 5);
                    addItem(createCustomItem(Material.RED_WOOL, "7"), 6);
                    addItem(createCustomItem(Material.BLACK_WOOL, "8"), 7);
                    addItem(createCustomItem(Material.RED_WOOL, "9"), 8);
        
                    addItem(createCustomItem(Material.BLACK_WOOL, "10"), 9);
                    addItem(createCustomItem(Material.BLACK_WOOL, "11"), 10);
                    addItem(createCustomItem(Material.RED_WOOL, "12"), 11);
                    addItem(createCustomItem(Material.BLACK_WOOL, "13"), 12);
                    addItem(createCustomItem(Material.RED_WOOL, "14"), 13);
                    addItem(createCustomItem(Material.BLACK_WOOL, "15"), 14);
                    addItem(createCustomItem(Material.RED_WOOL, "16"), 15);
                    addItem(createCustomItem(Material.BLACK_WOOL, "17"), 16);
                    addItem(createCustomItem(Material.RED_WOOL, "18"), 17);
                    addItem(createCustomItem(Material.RED_WOOL, "19"), 18);
                    addItem(createCustomItem(Material.BLACK_WOOL, "20"), 19);
                    addItem(createCustomItem(Material.RED_WOOL, "21"), 20);
                    addItem(createCustomItem(Material.BLACK_WOOL, "22"), 21);
                    addItem(createCustomItem(Material.RED_WOOL, "23"), 22);
                    addItem(createCustomItem(Material.BLACK_WOOL, "24"), 23);
                    addItem(createCustomItem(Material.RED_WOOL, "25"), 24);
                    addItem(createCustomItem(Material.BLACK_WOOL, "26"), 25);
                    addItem(createCustomItem(Material.RED_WOOL, "27"), 26);
                    addItem(createCustomItem(Material.BLACK_WOOL, "28"), 27);
                    addItem(createCustomItem(Material.BLACK_WOOL, "29"), 28);
                    addItem(createCustomItem(Material.RED_WOOL, "30"), 29);
                    addItem(createCustomItem(Material.BLACK_WOOL, "31"), 30);
                    addItem(createCustomItem(Material.RED_WOOL, "32"), 31);
                    addItem(createCustomItem(Material.BLACK_WOOL, "33"), 32);
                    addItem(createCustomItem(Material.RED_WOOL, "34"), 33);
                    addItem(createCustomItem(Material.BLACK_WOOL, "35"), 34);
                    addItem(createCustomItem(Material.RED_WOOL, "36"), 35);
                    addItem(createCustomItem(Material.GREEN_WOOL, "0"), 36);
                    addItem(createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)"), 45);
                    addItem(createCustomItem(Material.DIAMOND, "1 DIAMOND CHIP"), 47);
                    addItem(createCustomItem(Material.DIAMOND, "5 DIAMOND CHIP"), 48);
                    addItem(createCustomItem(Material.DIAMOND, "10 DIAMOND CHIP"), 49);
                    addItem(createCustomItem(Material.DIAMOND, "25 DIAMOND CHIP"), 50);
                    addItem(createCustomItem(Material.DIAMOND, "50 DIAMOND CHIP"), 51);
                    //addItem(createCustomItem(Material.DIAMOND, "Last Page"), 52);
                    addItem(createCustomItem(Material.ARROW, "Next Page"), 53);
                    pageNum=2;


                }


            default:}
        
       
      /*   if (slot==44){
            player.sendMessage("Starting Roulette...");
                
            addItem(createCustomItem(Material.RED_WOOL, "1"), 0);
            addItem(createCustomItem(Material.BLACK_WOOL, "2"), 1);
            addItem(createCustomItem(Material.RED_WOOL, "3"), 2);
            addItem(createCustomItem(Material.BLACK_WOOL, "4"), 3);
            addItem(createCustomItem(Material.RED_WOOL, "5"), 4);
            addItem(createCustomItem(Material.BLACK_WOOL, "6"), 5);
            addItem(createCustomItem(Material.RED_WOOL, "7"), 6);
            addItem(createCustomItem(Material.BLACK_WOOL, "8"), 7);
            addItem(createCustomItem(Material.RED_WOOL, "9"), 8);

            addItem(createCustomItem(Material.BLACK_WOOL, "10"), 9);
            addItem(createCustomItem(Material.BLACK_WOOL, "11"), 10);
            addItem(createCustomItem(Material.RED_WOOL, "12"), 11);
            addItem(createCustomItem(Material.BLACK_WOOL, "13"), 12);
            addItem(createCustomItem(Material.RED_WOOL, "14"), 13);
            addItem(createCustomItem(Material.BLACK_WOOL, "15"), 14);
            addItem(createCustomItem(Material.RED_WOOL, "16"), 15);
            addItem(createCustomItem(Material.BLACK_WOOL, "17"), 16);
            addItem(createCustomItem(Material.RED_WOOL, "18"), 17);
            addItem(createCustomItem(Material.RED_WOOL, "19"), 18);
            addItem(createCustomItem(Material.BLACK_WOOL, "20"), 19);
            addItem(createCustomItem(Material.RED_WOOL, "21"), 20);
            addItem(createCustomItem(Material.BLACK_WOOL, "22"), 21);
            addItem(createCustomItem(Material.RED_WOOL, "23"), 22);
            addItem(createCustomItem(Material.BLACK_WOOL, "24"), 23);
            addItem(createCustomItem(Material.RED_WOOL, "25"), 24);
            addItem(createCustomItem(Material.BLACK_WOOL, "26"), 25);
            addItem(createCustomItem(Material.RED_WOOL, "27"), 26);
            addItem(createCustomItem(Material.BLACK_WOOL, "28"), 27);
            addItem(createCustomItem(Material.BLACK_WOOL, "29"), 28);
            addItem(createCustomItem(Material.RED_WOOL, "30"), 29);
            addItem(createCustomItem(Material.BLACK_WOOL, "31"), 30);
            addItem(createCustomItem(Material.RED_WOOL, "32"), 31);
            addItem(createCustomItem(Material.BLACK_WOOL, "33"), 32);
            addItem(createCustomItem(Material.RED_WOOL, "34"), 33);
            addItem(createCustomItem(Material.BLACK_WOOL, "35"), 34);
            addItem(createCustomItem(Material.RED_WOOL, "36"), 35);
            addItem(createCustomItem(Material.GREEN_WOOL, "0"), 36);
            addItem(createCustomItem(Material.REDSTONE_BLOCK, "LEAVE GAME (Placed Bets may be lost)"), 45);
            addItem(createCustomItem(Material.DIAMOND, "1 DIAMOND CHIP"), 47);
            addItem(createCustomItem(Material.DIAMOND, "5 DIAMOND CHIP"), 48);
            addItem(createCustomItem(Material.DIAMOND, "10 DIAMOND CHIP"), 49);
            addItem(createCustomItem(Material.DIAMOND, "25 DIAMOND CHIP"), 50);
            addItem(createCustomItem(Material.DIAMOND, "50 DIAMOND CHIP"), 51);
            //addItem(createCustomItem(Material.DIAMOND, "Last Page"), 52);
            addItem(createCustomItem(Material.ARROW, "Next Page"), 53);
    }*/

            




        
       
        
    }

    private double getWagerAmountFromName(String name) {
        switch (name) {
            case "1 DIAMOND CHIP":
                return 1;
            case "5 DIAMOND CHIP":
                return 5;
            case "10 DIAMOND CHIP":
                return 10;
            case "25 DIAMOND CHIP":
                return 25;
            case "50 DIAMOND CHIP":
                return 50;
            default:
                return 0;
        }
    }

    private boolean hasEnoughWager(Player player, double amount) {
        int requiredDiamonds = (int) Math.ceil(amount);
        return player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), requiredDiamonds);
    }

    private void removeWagerFromInventory(Player player, double amount) {
        int requiredDiamonds = (int) Math.ceil(amount);
        if (requiredDiamonds > 0) {
            player.getInventory().removeItem(new ItemStack(Material.DIAMOND, requiredDiamonds));
        } else {
            player.sendMessage("Invalid wager amount: " + amount);
        }
    }
}
