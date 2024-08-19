package org.nc.nccasino.games;


import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnimationTable implements InventoryHolder {
    private final Inventory inventory;
    private final UUID playerId;
    private final Nccasino plugin;
    private final String animationMessage;
    private final Map<Player, Integer> animationTasks;
    //private final int[][] letterMapping;
    private final Map<Player, Boolean> animationCompleted;
    private boolean clickAllowed = true;
    private boolean finished = false;

    public AnimationTable(Player player, Nccasino plugin, String animationMessage,int index) {
        this.playerId = player.getUniqueId();
        this.plugin = plugin;
        this.animationMessage = animationMessage;
        this.inventory = Bukkit.createInventory(this, 54, "Animation");
        this.animationTasks = new HashMap<>();
        this.animationCompleted=new HashMap<>();
        // Load letter mappings (from A-Z) or custom ones
       // this.letterMapping = loadLetterMapping();
      //  
        // Start the animation
        if(index==0){
           //intro animation 

        }
        //startAnimation(player);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void animateMessage(Player player, Runnable onAnimationComplete, String printmsg){
//parse printmsg, construct int[][] dynamic??
//int[][] result = new int[][];



        int[][] fullt = new int[][]{
            {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,1,1,1,1,1,0,1,0,0,0,1,0,1,1,1,1,0,0,1,1,1,1, 0,0,0,0,0,0,  1,0,0,0,1,0,0,1,1,1,0,0,0,1,1,1,0,0,0,1,1,1,0,0,0,1,1,1,1,0,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,0,0,},
            {0,0,0,0,0,0,0,0,0,     1,1,0,1,1,0,0,0,1,0,0,0,1,1,0,0,1,0,1,0,0,0,0,1,0,0,0,0, 0,0,0,0,0,0,  1,1,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,1,0,0,0,1,1,0,0,1,0,1,0,0,0,1,0,},
            {0,0,0,0,0,0,0,0,0,     1,0,1,0,1,0,0,0,1,0,0,0,1,0,1,0,1,0,1,1,1,1,0,0,1,1,1,0, 0,1,1,1,1,0,  1,0,1,0,1,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,1,1,1,0,0,0,0,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,0,},
            {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,0,1,0,0,0,0,0,0,0,0,1, 0,1,1,1,1,0,  1,0,0,1,1,0,1,0,0,0,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,0,1,0,0,0,1,0,},
            {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,0,1, 0,0,0,0,0,0,  1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,},
            {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,1,1,1,1,1,0,1,0,0,0,1,0,1,1,1,1,0,1,1,1,1,0, 0,0,0,0,0,0,  1,0,0,0,1,0,0,1,1,1,0,0,0,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,0,0,}
        };
    

        startBlockAnimation(player, onAnimationComplete, fullt);
    }

    private ItemStack createCustomItem(Material material, String name, int amount) {
        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }


    private void startBlockAnimation(Player player, Runnable onAnimationComplete, int[][]printmsg) {
        animationCompleted.put(player, false);  // Reset the animation completed flag
       
        // Ensure that no duplicate animations are started
        if (animationTasks.containsKey(player)) {
            return;
        }
        
        int[][] fullt = new int[][]{
            {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,1,1,1,1,1,0,1,0,0,0,1,0,1,1,1,1,0,0,1,1,1,1, 0,0,0,0,0,0,  1,0,0,0,1,0,0,1,1,1,0,0,0,1,1,1,0,0,0,1,1,1,0,0,0,1,1,1,1,0,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,0,0,},
            {0,0,0,0,0,0,0,0,0,     1,1,0,1,1,0,0,0,1,0,0,0,1,1,0,0,1,0,1,0,0,0,0,1,0,0,0,0, 0,0,0,0,0,0,  1,1,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,1,0,0,0,1,1,0,0,1,0,1,0,0,0,1,0,},
            {0,0,0,0,0,0,0,0,0,     1,0,1,0,1,0,0,0,1,0,0,0,1,0,1,0,1,0,1,1,1,1,0,0,1,1,1,0, 0,1,1,1,1,0,  1,0,1,0,1,0,1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,1,1,1,0,0,0,0,1,0,0,0,1,0,1,0,1,0,1,0,0,0,1,0,},
            {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,0,1,0,0,0,0,0,0,0,0,1, 0,1,1,1,1,0,  1,0,0,1,1,0,1,0,0,0,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,1,0,1,0,0,0,1,0,},
            {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,0,1, 0,0,0,0,0,0,  1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,},
            {0,0,0,0,0,0,0,0,0,     1,0,0,0,1,0,1,1,1,1,1,0,1,0,0,0,1,0,1,1,1,1,0,1,1,1,1,0, 0,0,0,0,0,0,  1,0,0,0,1,0,0,1,1,1,0,0,0,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,1,1,1,1,1,0,1,0,0,0,1,0,0,1,1,1,0,0,}
        };
    
        int[][] full = new int[][]{
            {0,0,0,0,0,0,0,0,0,   0,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,0,0,1,1,1,0,0,0,1,1,1,1,0,1,1,1,1,0,0,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,0,1,1,1,1,1,0,1,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,1,0,1,1,1,1,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,1,0,1,0,0,0,1,0,1,1,1,1,0                        },
            {0,0,0,0,0,0,0,0,0,   1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,1,0,1,1,0,1,1,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0                        },
            {0,0,0,0,0,0,0,0,0,   1,0,0,0,1,0,1,1,1,1,1,0,1,0,0,0,0,0,1,0,0,0,1,0,1,1,1,1,0,1,1,1,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,1,0,0,0,1,0,0,0,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,0,1,0,0                        },
            {0,0,0,0,0,0,0,0,0,   1,1,1,1,1,0,1,0,0,0,1,0,1,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,1,0,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,1,1,0,1,0,0,0,1,0,1,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0                        },
            {0,0,0,0,0,0,0,0,0,   1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,0,1,1,1,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0                       },
            {0,0,0,0,0,0,0,0,0,   1,0,0,0,1,0,1,1,1,1,0,0,0,1,1,1,0,0,1,1,1,0,0,0,1,1,1,1,0,1,0,0,0,0,0,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,0,0,1,1,0,0,0,1,0,0,1,0,1,1,1,1,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,1,0,1,1,1,1,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,0,1,0,1,0,0,0,0,0,1,0,0,0,1,1,1,1,0                        }
        };
    
        final int[] taskId = new int[1];
        final int initialRowShift =0;  // Adjust this to change the starting shift, should ensure "CASI" is visible
    
        taskId[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int rowShift = -initialRowShift;  // Start with a negative shift to position the first visible letter correctly
            
            @Override
            public void run() {
                if (finished) {
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    animationTasks.remove(player);
                    //System.out.println("Finished apparatnelty");
                    return;
                }
    
                // Clear the inventory before drawing the next frame
                inventory.clear();
    
                // Draw the current frame
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        int arrayIndex = col + rowShift;
                        if (arrayIndex >= 0 && arrayIndex < full[row].length) { // Prevent out of bounds and ensure we're within array limits
                            int blockType = full[row][arrayIndex];
                            Material material = (blockType == 1) ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
    
                            int slot = row * 9 + col; // calculate slot in inventory grid
                            inventory.setItem(slot, createCustomItem(material, "CLICK TO SKIP", 1));
                        }
                    }
                }
    
                // Shift the display for the next frame
                rowShift++;
    
                // Adjust this condition to allow for the entire "CASINO MINES" text to be shown
                if (rowShift >= full[0].length) { // Stop when the entire string has moved through
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    animationTasks.remove(player);
                    onAnimationComplete.run();  // Execute the next action after the animation completes
                }
            }
        }, 0L, 1L).getTaskId();  // Adjust tick speed if needed
    
        // Store the task ID so it can be canceled later
        int temp=taskId[0];
        //System.out.println("put");
        animationTasks.put(player, 1);
    }

    private int[][] loadLetterMapping() {
        // Load or generate int[][] mapping for the letters A-Z
        // This will be reused for all animations
        return new int[6][100];  // Placeholder, actual implementation will vary
    }

    private void openGameTable(Player player) {
        // Open the actual game table (e.g., MinesTable) after the animation finishes
        // This could involve passing control back to the game inventory logic
    }

    private void stopAnimation(Player player) {
        // Allow the player to skip the animation by clicking
        finished = true;
        openGameTable(player);
    }
}
