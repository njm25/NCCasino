package org.nc.nccasino.components;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.helpers.AnimationSongs;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.VSE.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnimationTable extends DealerInventory implements Listener {
    private final MultiChannelEngine mce;
    private final Inventory inventory;
    private final UUID playerId;
    private final Nccasino plugin;
    private final String animationMessage;
    private final Map<UUID, Integer> animationTasks;
    private final Map<UUID, Boolean> animationCompleted;
    private final Map<UUID, Boolean> clickAllowed;
    private final Map<UUID, Boolean> animationStopped; // Track if animation is already stopped
    private final Map<UUID, Runnable> animationCallbacks;

    private static final Map<Character, int[][]> letterMap = new HashMap<>();

   

    public AnimationTable(Player player, Nccasino plugin, String animationMessage, int index) {
        super(player.getUniqueId(), 54, "Animation Table");

        this.playerId = player.getUniqueId();
        this.plugin = plugin;
        this.animationMessage = animationMessage;
        this.inventory = Bukkit.createInventory(this, 54, "Animation");
        this.animationTasks = new HashMap<>();
        this.animationCompleted = new HashMap<>();
        this.clickAllowed = new HashMap<>();
        this.animationCallbacks = new HashMap<>();
        this.animationStopped = new HashMap<>();
        this.mce = new MultiChannelEngine(plugin);

        // Register the event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void animateMessage(Player player, Runnable onAnimationComplete) {
        mce.addPlayerToChannel(player.getUniqueId().toString(), player);
        mce.playSong(player.getUniqueId().toString(), AnimationSongs.getAnimationSong(animationMessage), false, animationMessage);
        UUID playerUUID = player.getUniqueId();
        animationCallbacks.put(playerUUID, onAnimationComplete);
        animationStopped.put(playerUUID, false);
        animationCompleted.put(playerUUID, false);
        clickAllowed.put(playerUUID, true);

        int[][] fullt = parseMessage(animationMessage);
        startBlockAnimation(player, onAnimationComplete, fullt);
    }
    private int[][] parseMessage(String message) {
        if(message==null){
            message="Error";
        }
        // Convert message to uppercase to handle lowercase letters
        //message = message.toUpperCase();
    
        // CalcuWlate total width needed, including padding for the front and back
        int totalColumns = 0;
        for (char c : message.toCharArray()) {
            if (letterMap.containsKey(c)) {
                totalColumns += letterMap.get(c)[0].length + 1; // +1 for spacing between letters
            }
        }
        totalColumns -= 1; // Remove the last space after the final letter
        totalColumns += 2 * (letterMap.get(' ')[0].length + 1); // Add padding to front and back
    
        // Create the full array with padding
        int[][] parsedMessage = new int[6][totalColumns];
        int columnOffset = letterMap.get(' ')[0].length + 1; // Start with front padding
    
        // Add the letters to the array
        for (char c : message.toCharArray()) {
            if (!letterMap.containsKey(c)) continue;
    
            int[][] letter = letterMap.get(c);
            for (int row = 0; row < letter.length; row++) {
                System.arraycopy(letter[row], 0, parsedMessage[row], columnOffset, letter[row].length);
            }
            columnOffset += letter[0].length + 1; // Move to the next letter's position
        }
    
        // No need to add back padding explicitly, as the space is already accounted for in totalColumns
    
        return parsedMessage;
    }
    

    private void startBlockAnimation(Player player, Runnable onAnimationComplete, int[][] printmsg) {
        UUID playerUUID = player.getUniqueId();
        animationCompleted.put(playerUUID, false);

        // Ensure that no duplicate animations are started
        if (animationTasks.containsKey(playerUUID) || animationStopped.get(playerUUID)) {
            return;
        }

        final int[] taskId = new int[1];
        final int initialRowShift = 0; 

        taskId[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int rowShift = -initialRowShift;

            @Override
            public void run() {
                if (animationStopped.get(playerUUID)) {
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    animationTasks.remove(playerUUID);
                    return;
                }

                // Clear the inventory before drawing the next frame
                inventory.clear();

                if (printmsg == null || printmsg.length == 0 || printmsg[0].length == 0) {
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    animationTasks.remove(playerUUID);
                    return;
                }

                // Draw the current frame
                for (int row = 0; row < 6; row++) {
                    if (row >= printmsg.length) break;

                    for (int col = 0; col < 9; col++) {
                        int arrayIndex = col + rowShift;

                        if (arrayIndex >= 0 && arrayIndex < printmsg[row].length) {
                            int blockType = printmsg[row][arrayIndex];
                            Material material = (blockType == 1) ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;

                            int slot = row * 9 + col;
                            inventory.setItem(slot, createCustomItem(material, "CLICK TO SKIP", 1));
                        }
                    }
                }

                // Shift the display for the next frame
                rowShift++;

                if (rowShift >= printmsg[0].length) {
                    stopAnimation(player);
                    stopAnimation(player);
                    if(SoundHelper.getSoundSafely("item.chorus_fruit.teleport")!=null)player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.MASTER,1.0f, 1.0f); 
                    mce.removePlayerFromAllChannels(player);
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    animationTasks.remove(playerUUID);
                    if (!animationStopped.get(playerUUID)) {
                        animationCallbacks.get(playerUUID).run();
                    }
                }
            }
        }, 0L, 2L).getTaskId(); 

        animationTasks.put(playerUUID, taskId[0]);
    }
    private void stopAnimation(Player player) {
        UUID playerUUID = player.getUniqueId();
        clickAllowed.put(playerUUID, false);

        if (animationTasks.containsKey(playerUUID)) {
            int taskId = animationTasks.get(playerUUID);
            Bukkit.getScheduler().cancelTask(taskId);
            animationTasks.remove(playerUUID);
        }

        if (!animationStopped.get(playerUUID)) {
            animationStopped.put(playerUUID, true);
            animationCallbacks.get(playerUUID).run();
        }

        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();

        if (!playerUUID.equals(playerId)) return;
        if (event.getInventory().getHolder() != this) return;

        event.setCancelled(true);

        if (clickAllowed.getOrDefault(playerUUID, false) && !animationStopped.get(playerUUID)) {
            if(SoundHelper.getSoundSafely("item.flintandsteel.use")!=null)player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.MASTER,1.0f, 1.0f);  
            stopAnimation(player);
            if(SoundHelper.getSoundSafely("item.chorus_fruit.teleport")!=null)player.playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.MASTER,1.0f, 1.0f); 
            mce.removePlayerFromAllChannels(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
          if (event.getInventory().getHolder() != this) return;
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        if (!playerUUID.equals(playerId)) return;
        if (event.getPlayer() instanceof Player ) {
            stopAnimation(player);
            mce.removePlayerFromAllChannels(player);
        }
    }


    static {
        // Full alphabet using 6x5 grids for each letter
        letterMap.put(' ', new int[][]{
            {0},
            {0},
            {0},
            {0},
            {0},
            {0}
        });
        letterMap.put('!', new int[][]{
            {1},
            {1},
            {1},
            {1},
            {0},
            {1}
        });
        letterMap.put('\"', new int[][]{
            {1,0,1},
            {1,0,1},
            {1,0,1},
            {0,0,0},
            {0,0,0},
            {0,0,0}
        });
        letterMap.put('#', new int[][]{
            {0,0,0,0,0},
            {0,1,0,1,0},
            {1,1,1,1,1},
            {0,1,0,1,0},
            {1,1,1,1,1},
            {0,1,0,1,0}
        });
        letterMap.put('$', new int[][]{
            {0,0,1,0,1,0,0},
            {0,1,1,1,1,1,1},
            {1,0,1,0,1,0,0},
            {0,1,1,1,1,1,0},
            {0,0,1,0,1,0,1},
            {1,1,1,1,1,1,0}
        });
        letterMap.put('%', new int[][]{
            {1,1,1,0,0,1,0,0,0},
            {1,0,1,0,0,1,0,0,0},
            {1,1,1,0,1,0,0,0,0},
            {0,0,0,0,1,0,1,1,1},
            {0,0,0,1,0,0,1,0,1},
            {0,0,0,1,0,0,1,1,1}
        });
        letterMap.put('&', new int[][]{
            {0,1,0,0,0},
            {1,0,1,0,0},
            {0,1,0,0,0},
            {1,0,1,0,1},
            {1,0,0,1,0},
            {0,1,1,0,1}
        });
        letterMap.put('\'', new int[][]{
            {1,0,0},
            {1,0,0},
            {1,0,0},
            {0,0,0},
            {0,0,0},
            {0,0,0}
        });
        letterMap.put('(', new int[][]{
            {0,1},
            {1,0},
            {1,0},
            {1,0},
            {1,0},
            {0,1}
        });
        letterMap.put(')', new int[][]{
            {1,0},
            {0,1},
            {0,1},
            {0,1},
            {0,1},
            {1,0}
        });
        letterMap.put('*', new int[][]{
            {1,0,1},
            {0,1,0},
            {1,0,1},
            {0,0,0},
            {0,0,0},
            {0,0,0}
        });
        letterMap.put('+', new int[][]{
            {0,0,0},
            {0,0,0},
            {0,1,0},
            {1,1,1},
            {0,1,0},
            {0,0,0}
        });
        letterMap.put(',', new int[][]{
            {0,0},
            {0,0},
            {0,0},
            {0,1},
            {0,1},
            {1,0}
        });
        letterMap.put('-', new int[][]{
            {0,0,0,0},
            {0,0,0,0},
            {1,1,1,1},
            {1,1,1,1},
            {0,0,0,0},
            {0,0,0,0}
        });
        letterMap.put('.', new int[][]{
            {0},
            {0},
            {0},
            {0},
            {0},
            {1}
        });
        letterMap.put('/', new int[][]{
            {0,0,1},
            {0,0,1},
            {0,1,0},
            {0,1,0},
            {1,0,0},
            {1,0,0}
        });
        letterMap.put('0', new int[][]{
            {0,1,1,0},
            {1,0,0,1},
            {1,0,0,1},
            {1,0,0,1},
            {1,0,0,1},
            {0,1,1,0}
        });
        letterMap.put('1', new int[][]{
            {0,1,0},
            {1,1,0},
            {0,1,0},
            {0,1,0},
            {0,1,0},
            {1,1,1}
        });
        letterMap.put('2', new int[][]{
            {0,1,1,0},
            {1,0,0,1},
            {0,0,0,1},
            {0,1,1,0},
            {1,0,0,0},
            {1,1,1,1}
        });
        letterMap.put('3', new int[][]{
            {0,1,1,0},
            {1,0,0,1},
            {0,0,1,0},
            {0,0,0,1},
            {1,0,0,1},
            {0,1,1,0}
        });
        letterMap.put('4', new int[][]{
            {0,0,1,1,0},
            {0,1,0,1,0},
            {1,0,0,1,0},
            {1,1,1,1,1},
            {0,0,0,1,0},
            {0,0,0,1,0}
        });
        letterMap.put('5', new int[][]{
            {1,1,1,1},
            {1,0,0,0},
            {0,1,1,0},
            {0,0,0,1},
            {1,0,0,1},
            {0,1,1,0}
        });
        letterMap.put('6', new int[][]{
            {0,1,1,1},
            {1,0,0,0},
            {1,1,1,0},
            {1,0,0,1},
            {1,0,0,1},
            {0,1,1,0}
        });
        letterMap.put('7', new int[][]{
            {1,1,1,1},
            {0,0,0,1},
            {0,0,1,0},
            {0,1,0,0},
            {0,1,0,0},
            {0,1,0,0}
        });
        letterMap.put('8', new int[][]{
            {0,1,1,0},
            {1,0,0,1},
            {0,1,1,0},
            {1,0,0,1},
            {1,0,0,1},
            {0,1,1,0}
        });
        letterMap.put('9', new int[][]{
            {0,1,1,0},
            {1,0,0,1},
            {1,0,0,1},
            {0,1,1,1},
            {0,0,0,1},
            {1,1,1,0}
        });
        letterMap.put(':', new int[][]{
            {0},
            {1},
            {0},
            {0},
            {1},
            {0}
        });
        letterMap.put(';', new int[][]{
            {0,0},
            {0,1},
            {0,0},
            {0,1},
            {1,0},
            {0,0}
        });
        letterMap.put('<', new int[][]{
            {0,0,0},
            {0,0,1},
            {0,1,0},
            {1,0,0},
            {0,1,0},
            {0,0,1}
        });
        letterMap.put('=', new int[][]{
            {0,0,0,0},
            {0,0,0,0},
            {1,1,1,1},
            {0,0,0,0},
            {1,1,1,1},
            {0,0,0,0}
        });
        letterMap.put('>', new int[][]{
            {0,0,0},
            {1,0,0},
            {0,1,0},
            {0,0,1},
            {0,1,0},
            {1,0,0}
        });
        letterMap.put('?', new int[][]{
            {0,1,1,0},
            {1,0,0,1},
            {0,0,0,1},
            {0,1,1,0},
            {0,0,0,0},
            {0,1,0,0}
        });
        letterMap.put('@', new int[][]{
            {0,1,1,1,1,0,0,0},
            {1,0,0,0,0,1,0,0},
            {1,0,0,1,0,1,0,0},
            {1,0,1,0,1,1,0,0},
            {1,0,0,1,0,0,0,1},
            {0,1,1,1,1,1,1,0}
        });
        letterMap.put('A', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,1,1,1,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        letterMap.put('B', new int[][]{
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,1,1,1,0}
        });
        letterMap.put('C', new int[][]{
            {0,1,1,1,1},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {0,1,1,1,1}
        });
        letterMap.put('D', new int[][]{
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,1,1,1,0}
        });
        letterMap.put('E', new int[][]{
            {1,1,1,1,1},
            {1,0,0,0,0},
            {1,1,1,1,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,1,1,1,1}
        });
        letterMap.put('F', new int[][]{
            {1,1,1,1,1},
            {1,0,0,0,0},
            {1,1,1,1,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0}
        });
        letterMap.put('G', new int[][]{
            {0,1,1,1,1},
            {1,0,0,0,0},
            {1,0,0,1,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,1}
        });
        letterMap.put('H', new int[][]{
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,1,1,1,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        letterMap.put('I', new int[][]{
            {1,1,1,1,1},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {1,1,1,1,1}
        });
        letterMap.put('J', new int[][]{
            {0,0,0,0,1},
            {0,0,0,0,1},
            {0,0,0,0,1},
            {0,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        letterMap.put('K', new int[][]{
            {1,0,0,0,1},
            {1,0,0,1,0},
            {1,1,1,0,0},
            {1,0,0,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        letterMap.put('L', new int[][]{
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,1,1,1,1}
        });
        letterMap.put('M', new int[][]{
            {1,0,0,0,1},
            {1,1,0,1,1},
            {1,0,1,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        letterMap.put('N', new int[][]{
            {1,0,0,0,1},
            {1,1,0,0,1},
            {1,0,1,0,1},
            {1,0,0,1,1},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        letterMap.put('O', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        letterMap.put('P', new int[][]{
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,1,1,1,0},
            {1,0,0,0,0},
            {1,0,0,0,0},
            {1,0,0,0,0}
        });
        letterMap.put('Q', new int[][]{
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,1,0,1},
            {1,0,0,1,0},
            {0,1,1,0,1}
        });
        letterMap.put('R', new int[][]{
            {1,1,1,1,0},
            {1,0,0,0,1},
            {1,1,1,1,0},
            {1,0,1,0,0},
            {1,0,0,1,0},
            {1,0,0,0,1}
        });
        letterMap.put('S', new int[][]{
            {0,1,1,1,1},
            {1,0,0,0,0},
            {0,1,1,1,0},
            {0,0,0,0,1},
            {0,0,0,0,1},
            {1,1,1,1,0}
        });
        letterMap.put('T', new int[][]{
            {1,1,1,1,1},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0}
        });
        letterMap.put('U', new int[][]{
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,1,1,0}
        });
        letterMap.put('V', new int[][]{
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {0,1,0,1,0},
            {0,0,1,0,0}
        });
        letterMap.put('W', new int[][]{
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,1,0,1},
            {1,1,0,1,1},
            {1,0,0,0,1}
        });
        letterMap.put('X', new int[][]{
            {1,0,0,0,1},
            {0,1,0,1,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,1,0,1,0},
            {1,0,0,0,1}
        });
        letterMap.put('Y', new int[][]{
            {1,0,0,0,1},
            {0,1,0,1,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0},
            {0,0,1,0,0}
        });
        letterMap.put('Z', new int[][]{
            {1,1,1,1,1},
            {0,0,0,0,1},
            {0,0,0,1,0},
            {0,0,1,0,0},
            {0,1,0,0,0},
            {1,1,1,1,1}
        });
        letterMap.put('[', new int[][]{
            {1,1},
            {1,0},
            {1,0},
            {1,0},
            {1,0},
            {1,1}
        });
        letterMap.put('\\', new int[][]{
            {1,0,0},
            {1,0,0},
            {0,1,0},
            {0,1,0},
            {0,0,1},
            {0,0,1}
        });
        letterMap.put(']', new int[][]{
            {1,1},
            {0,1},
            {0,1},
            {0,1},
            {0,1},
            {1,1}
        });
        letterMap.put('^', new int[][]{
            {0,1,0},
            {1,0,1},
            {0,0,0},
            {0,0,0},
            {0,0,0},
            {0,0,0}
        });
        letterMap.put('_', new int[][]{
            {0,0,0},
            {0,0,0},
            {0,0,0},
            {0,0,0},
            {0,0,0},
            {1,1,1}
        });
        letterMap.put('`', new int[][]{
            {1,1,0},
            {0,0,1},
            {0,0,0},
            {0,0,0},
            {0,0,0},
            {0,0,0}
        });
        letterMap.put('a', new int[][]{
            {0,0,0,0,0},
            {0,1,1,0,1},
            {1,0,0,1,0},
            {1,0,0,1,0},
            {1,0,0,1,1},
            {0,1,1,0,1}
        });
        letterMap.put('b', new int[][]{
            {1,0,0,0},
            {1,0,0,0},
            {1,1,1,0},
            {1,0,0,1},
            {1,0,0,1},
            {1,1,1,1}
        });
        letterMap.put('c', new int[][]{
            {0,0,0,0},
            {0,1,1,0},
            {1,0,0,1},
            {1,0,0,0},
            {1,0,0,1},
            {0,1,1,0}
        });
        letterMap.put('d', new int[][]{
            {0,0,0,1},
            {0,0,0,1},
            {0,1,1,1},
            {1,0,0,1},
            {1,0,0,1},
            {0,1,1,1}
        });
        letterMap.put('e', new int[][]{
            {0,0,0,0},
            {0,1,1,0},
            {1,0,0,1},
            {1,1,1,1},
            {1,0,0,0},
            {0,1,1,1}
        });
        letterMap.put('f', new int[][]{
            {0,0,0,0},
            {0,1,1,1},
            {0,1,0,0},
            {1,1,1,1},
            {0,1,0,0},
            {0,1,0,0}
        });
        letterMap.put('g', new int[][]{
            {0,1,1,0},
            {1,0,0,1},
            {1,0,0,1},
            {0,1,1,1},
            {0,0,0,1},
            {1,1,1,0}
        });
        letterMap.put('h', new int[][]{
            {1,0,0,0},
            {1,0,0,0},
            {1,0,0,0},
            {1,1,1,0},
            {1,0,0,1},
            {1,0,0,1}
        });
        letterMap.put('i', new int[][]{
            {0},
            {1},
            {0},
            {1},
            {1},
            {1}
        });
        letterMap.put('j', new int[][]{
            {0,1},
            {0,0},
            {0,1},
            {0,1},
            {0,1},
            {1,1}
        });
        letterMap.put('k', new int[][]{
            {0,0,0},
            {1,0,0},
            {1,0,1},
            {1,1,0},
            {1,1,0},
            {1,0,1}
        });
        letterMap.put('l', new int[][]{
            {1},
            {1},
            {1},
            {1},
            {1},
            {1}
        });
        letterMap.put('m', new int[][]{
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,1,0,1,0},
            {1,0,1,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        letterMap.put('n', new int[][]{
            {0,0,0,0,0},
            {0,0,0,0,0},
            {0,1,1,1,0},
            {1,0,0,0,1},
            {1,0,0,0,1},
            {1,0,0,0,1}
        });
        letterMap.put('o', new int[][]{
            {0,0,0,0},
            {0,0,0,0},
            {0,1,1,0},
            {1,0,0,1},
            {1,0,0,1},
            {0,1,1,0}
        });
        letterMap.put('p', new int[][]{
            {0,0,0,0},
            {1,1,1,0},
            {1,0,0,1},
            {1,1,1,0},
            {1,0,0,0},
            {1,0,0,0}
        });
        letterMap.put('q', new int[][]{
            {0,1,1,1,0},
            {1,0,0,1,0},
            {1,0,0,1,0},
            {0,1,1,1,0},
            {0,0,0,1,0},
            {0,0,0,1,1}
        });
        letterMap.put('r', new int[][]{
            {0,0,0},
            {0,0,0},
            {1,1,1},
            {1,0,0},
            {1,0,0},
            {1,0,0}
        });
        letterMap.put('s', new int[][]{
            {0,0,0,0},
            {0,1,1,1},
            {1,0,0,0},
            {0,1,1,0},
            {0,0,0,1},
            {1,1,1,0}
        });
        letterMap.put('t', new int[][]{
            {0,1,0},
            {1,1,1},
            {0,1,0},
            {0,1,0},
            {0,1,0},
            {0,1,0}
        });
        letterMap.put('u', new int[][]{
            {0,0,0,0},
            {0,0,0,0},
            {1,0,0,1},
            {1,0,0,1},
            {1,0,0,1},
            {1,1,1,1}
        });
        letterMap.put('v', new int[][]{
            {0,0,0},
            {0,0,0},
            {1,0,1},
            {1,0,1},
            {1,0,1},
            {0,1,0}
        });
        letterMap.put('w', new int[][]{
            {0,0,0,0,0},
            {0,0,0,0,0},
            {1,0,0,0,1},
            {1,0,1,0,1},
            {1,0,1,0,1},
            {0,1,0,1,0}
        });
        letterMap.put('x', new int[][]{
            {0,0,0,0,0},
            {1,0,0,0,1},
            {0,1,0,1,0},
            {0,0,1,0,0},
            {0,1,0,1,0},
            {1,0,0,0,1}
        });
        letterMap.put('y', new int[][]{
            {0,0,0,0},
            {0,1,0,1},
            {0,1,0,1},
            {0,0,1,0},
            {0,1,0,0},
            {1,0,0,0}
        });
        letterMap.put('z', new int[][]{
            {0,0,0,0},
            {0,0,0,0},
            {1,1,1,1},
            {0,0,1,0},
            {0,1,0,0},
            {1,1,1,1}
        });
        letterMap.put('{', new int[][]{
            {0,1,1},
            {0,1,0},
            {1,1,0},
            {0,1,0},
            {0,1,0},
            {0,1,1}
        });
        letterMap.put('|', new int[][]{
            {1},
            {1},
            {1},
            {1},
            {1},
            {1}
        });
        letterMap.put('}', new int[][]{
            {1,1,0},
            {0,1,0},
            {0,1,1},
            {0,1,0},
            {0,1,0},
            {1,1,0}
        });
        letterMap.put('~', new int[][]{
            {0,0,0,0},
            {0,0,0,0},
            {0,1,0,1},
            {1,0,1,0},
            {0,0,0,0},
            {0,0,0,0}
        });

    }

}
