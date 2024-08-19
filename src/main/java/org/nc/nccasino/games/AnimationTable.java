package org.nc.nccasino.games;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnimationTable extends DealerInventory implements InventoryHolder, Listener {
    private final Inventory inventory;
    private final UUID playerId;
    private final Nccasino plugin;
    private final String animationMessage;
    private final Map<UUID, Integer> animationTasks;
    private final Map<UUID, Boolean> animationCompleted;
    private final Map<UUID, Boolean> clickAllowed;
    private final Map<UUID, Boolean> animationStopped; // Track if animation is already stopped
    private final Map<UUID, Runnable> animationCallbacks;

    public AnimationTable(Player player, Nccasino plugin, String animationMessage, int index) {
        super(player.getUniqueId(), 54, "Animation Table");  // Use player's UUID as dealerId or customize as needed

        this.playerId = player.getUniqueId();
        this.plugin = plugin;
        this.animationMessage = animationMessage;
        this.inventory = Bukkit.createInventory(this, 54, "Animation");
        this.animationTasks = new HashMap<>();
        this.animationCompleted = new HashMap<>();
        this.clickAllowed = new HashMap<>();
        this.animationCallbacks = new HashMap<>();
        this.animationStopped = new HashMap<>(); // Track animation stop state

        // Register the event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void animateMessage(Player player, Runnable onAnimationComplete, String printmsg) {
        UUID playerUUID = player.getUniqueId();
        animationCallbacks.put(playerUUID, onAnimationComplete);
        animationStopped.put(playerUUID, false); // Reset stop state
        animationCompleted.put(playerUUID, false);  // Reset completion state
        clickAllowed.put(playerUUID, true); // Allow clicks by default

        int[][] fullt = new int[][]{
            {0,0,0,0,0,0,0,0,0,   0,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,0,0,1,1,1,0,0,0,1,1,1,1,0,1,1,1,1,0,0,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,0,1,1,1,1,1,0,1,0,0,1,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,0,0,1,1,1,1,0,0,0,1,1,1,1,0,1,1,1,1,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,1,0,1,0,0,0,1,0,1,1,1,1,0                        },
            {0,0,0,0,0,0,0,0,0,   1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,1,0,1,1,0,1,1,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,1,0,1,0,0,0,1,0,0,0,0,1,0                        },
            {0,0,0,0,0,0,0,0,0,   1,0,0,0,1,0,1,1,1,1,1,0,1,0,0,0,0,0,1,0,0,0,1,0,1,1,1,1,0,1,1,1,0,0,1,0,0,0,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,1,0,0,0,1,0,0,0,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,0,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,1,0,0,1,0,1,0,0,0,0,1,0,0                        },
            {0,0,0,0,0,0,0,0,0,   1,1,1,1,1,0,1,0,0,0,1,0,1,0,0,0,0,0,1,0,0,0,1,0,1,0,0,0,0,1,0,0,0,0,1,0,0,1,1,0,1,0,0,0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,1,1,0,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,1,1,0,1,0,0,0,1,0,1,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,0,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0                        },
            {0,0,0,0,0,0,0,0,0,   1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,0,1,0,1,0,0,1,0,0,0,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,1,0,1,0,0,0,0,0,0,1,1,1,0,0,1,0,0,0,1,0,0,0,0,0,1,0,0,0,1,0,0,0,1,0,0,0,1,0,0,1,0,1,0,0,0,1,0,1,0,1,0,0,0,0,1,0,0,0,1,0,0,0,0                       },
            {0,0,0,0,0,0,0,0,0,   1,0,0,0,1,0,1,1,1,1,0,0,0,1,1,1,0,0,1,1,1,0,0,0,1,1,1,1,0,1,0,0,0,0,0,1,1,1,0,0,1,0,0,0,1,0,1,1,1,1,1,0,0,1,1,0,0,0,1,0,0,1,0,1,1,1,1,0,1,0,0,0,1,0,1,0,0,0,1,0,0,1,1,1,0,0,1,0,0,0,0,0,0,0,0,0,1,0,1,0,0,0,1,0,1,1,1,1,0,0,0,0,1,0,0,0,1,1,1,1,1,0,0,0,1,0,0,0,0,0,1,0,1,0,0,0,0,0,1,0,0,0,1,1,1,1,0                        }
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

    private void startBlockAnimation(Player player, Runnable onAnimationComplete, int[][] printmsg) {
        UUID playerUUID = player.getUniqueId();
        animationCompleted.put(playerUUID, false);  // Reset the animation completed flag

        // Ensure that no duplicate animations are started
        if (animationTasks.containsKey(playerUUID) || animationStopped.get(playerUUID)) {
            return;
        }

        final int[] taskId = new int[1];
        final int initialRowShift = 0;  // Adjust this to change the starting shift

        taskId[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int rowShift = -initialRowShift;  // Start with a negative shift

            @Override
public void run() {
    if (animationStopped.get(playerUUID)) {
        Bukkit.getScheduler().cancelTask(taskId[0]);
        animationTasks.remove(playerUUID);
        return;
    }

    // Clear the inventory before drawing the next frame
    inventory.clear();

    // Check that printmsg is not null and has content
    if (printmsg == null || printmsg.length == 0 || printmsg[0].length == 0) {
        Bukkit.getScheduler().cancelTask(taskId[0]);
        animationTasks.remove(playerUUID);
        return;
    }

    // Draw the current frame
    for (int row = 0; row < 6; row++) {
        // Ensure row is within bounds
        if (row >= printmsg.length) break;

        for (int col = 0; col < 9; col++) {
            int arrayIndex = col + rowShift;

            // Ensure arrayIndex is within bounds
            if (arrayIndex >= 0 && arrayIndex < printmsg[row].length) {
                int blockType = printmsg[row][arrayIndex]; // Safe access now
                Material material = (blockType == 1) ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;

                int slot = row * 9 + col; // Calculate slot in inventory grid
                inventory.setItem(slot, createCustomItem(material, "CLICK TO SKIP", 1));
            }
        }
    }

    // Shift the display for the next frame
    rowShift++;

    // Stop when the entire string has moved through
    if (rowShift >= printmsg[0].length) {
        Bukkit.getScheduler().cancelTask(taskId[0]);
        animationTasks.remove(playerUUID);
        if (!animationStopped.get(playerUUID)) {
            animationCallbacks.get(playerUUID).run();  // Execute the next action after animation completes
        }
    }
}

        }, 0L, 2L).getTaskId(); // Increase tick delay between animations to slow it down

        // Store the task ID so it can be canceled later
        animationTasks.put(playerUUID, taskId[0]);
    }

    private void stopAnimation(Player player) {
        UUID playerUUID = player.getUniqueId();
        clickAllowed.put(playerUUID, false); // Disable further clicks

        if (animationTasks.containsKey(playerUUID)) {
            int taskId = animationTasks.get(playerUUID);
            Bukkit.getScheduler().cancelTask(taskId);
            animationTasks.remove(playerUUID);
        }

        if (!animationStopped.get(playerUUID)) {
            animationStopped.put(playerUUID, true); // Mark animation as stopped
            animationCallbacks.get(playerUUID).run(); // Complete the animation callback
        }

        // Unregister event listeners for this player
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        if (!playerUUID.equals(playerId)) return; // Ensure the event is for the correct player
        if (event.getInventory().getHolder() != this) return;

        event.setCancelled(true); // Cancel the default behavior

        // Stop the animation only if click is allowed
        if (clickAllowed.getOrDefault(playerUUID, false) && !animationStopped.get(playerUUID)) {
            stopAnimation(player); // Mark the animation as finished and open the game table
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!playerUUID.equals(playerId)) return; // Ensure the event is for the correct player
        if (event.getInventory().getHolder() != this) return;

        // Stop animation only if the inventory is closed intentionally
        if (event.getReason() == InventoryCloseEvent.Reason.PLAYER) {
            stopAnimation(player); // Stop the animation if the inventory is closed
        }
    }
}
