package org.nc.nccasino.games.TestGame;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;

import java.util.UUID;

public class TestClient extends Client {

    // We define a few slot constants for readability
    private static final int SEAT1_SLOT = 10;
    private static final int SEAT2_SLOT = 12;
    private static final int ACCEPT_BET_SLOT = 14;
    private static final int RESET_SLOT = 16;
    private static final int STATUS_SLOT = 22;

    public TestClient(TestServer server, Player player, Nccasino plugin, String internalName) {
        // 54‐slot inventory with "Two Player Bet Game"
        super(server, player, 54, "Two Player Bet Game", plugin, internalName);
    }

    @Override
    public void initializeUI(boolean rebetSwitch) {
        super.initializeUI(rebetSwitch);
        refreshUI();
    }

    @Override
    public void onServerStateChange(Server.SessionState oldState, Server.SessionState newState) {
        // The parent base class has a default no‐op. 
        // We can override if we need to react to generic session states (LOBBY, IN_PROGRESS, etc.)
        // But we have our own "GameState" in TestServer, so we mostly handle that in onServerUpdate below.
    }

    @Override
    public void onServerUpdate(String eventType, Object data) {
        super.onServerUpdate(eventType, data);
        if (eventType.equals("REFRESH_UI")) {
            refreshUI();
        }
    }

    @Override
    protected void reapplyPreviousBets() {
        // TODO Auto-generated method stub
        
    }

    /**
     * (Re)builds the entire UI. We remove everything and then add:
     *  - Two seat items at slots 10 and 12
     *  - If seated, show the occupant's head
     *  - If occupant is seat1, they can place bets (via the standard bet row in 43..53)
     *  - If occupant is seat2 and seat1 has placed a bet, show "Accept Bet" button
     *  - A status item showing the current game state
     *  - Possibly a "Reset" button (optional)
     */
    private void refreshUI() {
        inventory.clear();
        // Rebuild the base bet row from the parent
        super.initializeUI(false);

        TestServer testServer = (TestServer) server;
        TestServer.GameState gameState = testServer.getGameState();

        // Render seat1
        UUID seat1 = testServer.getSeat1();
        if (seat1 == null) {
            inventory.setItem(SEAT1_SLOT, createCustomItem(Material.OAK_STAIRS, "Seat 1 (Click to Sit)", 1));
        } else {
            inventory.setItem(SEAT1_SLOT, createPlayerHead(seat1, "Seat 1 Occupied"));
        }

        // Render seat2
        UUID seat2 = testServer.getSeat2();
        if (seat2 == null) {
            inventory.setItem(SEAT2_SLOT, createCustomItem(Material.OAK_STAIRS, "Seat 2 (Click to Sit)", 1));
        } else {
            inventory.setItem(SEAT2_SLOT, createPlayerHead(seat2, "Seat 2 Occupied"));
        }

        // Add a status item (slot 22)
        String statusText;
        switch (gameState) {
            case LOBBY:
                statusText = "Game State: LOBBY";
                break;
            case WAITING_FOR_ACCEPT:
                statusText = "Waiting for Seat2 to accept bet...";
                break;
            case COMPLETED:
                statusText = "Round finished. Click reset to start again.";
                break;
            default:
                statusText = "Unknown State!";
        }
        inventory.setItem(STATUS_SLOT, createCustomItem(Material.PAPER, statusText, 1));

        // If I am occupant of seat1, and game state is LOBBY, I can place a bet using the bet row.
        // The bet row is always visible (slots 43..53), but the user can only effectively place 
        // a bet if they are seat1 in LOBBY.

        // If I'm occupant of seat2 and game state == WAITING_FOR_ACCEPT, show an ACCEPT button
        if (seat2 != null && seat2.equals(player.getUniqueId()) 
                && gameState == TestServer.GameState.WAITING_FOR_ACCEPT) {
            double seat1Bet = testServer.getSeat1Bet();
            ItemStack acceptBet = createCustomItem(
                    Material.LIME_WOOL, 
                    "Accept Bet: " + (int) seat1Bet + " " + plugin.getCurrencyName(internalName), 
                    1
            );
            inventory.setItem(ACCEPT_BET_SLOT, acceptBet);
        }

        // If game is COMPLETED, show a reset button
        if (gameState == TestServer.GameState.COMPLETED) {
            ItemStack resetButton = createCustomItem(Material.BARRIER, "Reset Game", 1);
            inventory.setItem(RESET_SLOT, resetButton);
        }

        // Force a visual update
        player.updateInventory();
        // If we had an ongoing bet, we want to update the bet-lore in slot 53
        double currentBetTotal = betStack.stream().mapToDouble(Double::doubleValue).sum();
        updateBetLore(53, currentBetTotal);
    }

    /**
     * Handle non‐bet‐slot clicks (the base class handles 43..53 as bet row).
     */
    @Override
    protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        TestServer testServer = (TestServer) server;
        TestServer.GameState gameState = testServer.getGameState();

        if (slot == SEAT1_SLOT) {
            // Attempt to sit in seat1
            sendUpdateToServer("SIT", 0);
        } 
        else if (slot == SEAT2_SLOT) {
            // Attempt to sit in seat2
            sendUpdateToServer("SIT", 1);
        }
        else if (slot == ACCEPT_BET_SLOT) {
            // Accept bet if I'm seat2 occupant
            sendUpdateToServer("ACCEPT_BET", null);
        }
        else if (slot == RESET_SLOT && gameState == TestServer.GameState.COMPLETED) {
            // Attempt to reset
            sendUpdateToServer("RESET", null);
        }
        else if (slot == 53) {
            // The "place bet" slot in the bet row. We'll override its default 
            // so that only seat1 occupant in LOBBY can place a bet.
            // We'll basically call the parent's handleBet but then also let the server know.
            if (isSeat1Occupant() && gameState == TestServer.GameState.LOBBY) {
                super.handleBet(slot, player, event); // This does the normal currency removal and push to betStack
                double totalBet = betStack.stream().mapToDouble(Double::doubleValue).sum();
                if (totalBet > 0) {
                    // Notify server that seat1 placed a bet (the sum of betStack).
                    // For simplicity, let's say seat1 places the entire sum at once
                    sendUpdateToServer("PLACE_BET", totalBet);
                    // Then we can also clear the betStack to avoid double-betting
                    betStack.clear();
                    updateBetLore(53, 0);
                }
            } else {
                // If not seat1 occupant or not in LOBBY, do nothing special
            }
        }
    }

    /**
     * Determine if this client is occupant of seat1 on the server.
     */
    private boolean isSeat1Occupant() {
        TestServer testServer = (TestServer) server;
        UUID seat1 = testServer.getSeat1();
        return seat1 != null && seat1.equals(player.getUniqueId());
    }

    /**
     * Utility: Create a player head for a specific UUID.
     */
    private ItemStack createPlayerHead(UUID ownerUuid, String displayName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerUuid));
            meta.setDisplayName(displayName);
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
