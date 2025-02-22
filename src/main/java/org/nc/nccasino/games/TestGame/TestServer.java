package org.nc.nccasino.games.TestGame;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;

import java.util.Random;
import java.util.UUID;

public class TestServer extends Server {

    // Track the two seats
    private UUID seat1 = null;
    private UUID seat2 = null;

    // Track the amounts each seat bet
    private double seat1Bet = 0.0;
    private double seat2Bet = 0.0;

    // Simple random for 50/50
    private final Random random = new Random();

    // Define different server states
    // Feel free to rename or adjust these as needed.
    public enum GameState {
        LOBBY,              // No one seated or partial seating
        WAITING_FOR_ACCEPT, // Seat1 placed a bet, waiting for seat2 to accept
        COMPLETED           // Round finished, can reset or remain seated
    }

    private GameState gameState = GameState.LOBBY;

    public TestServer(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, "Test Game Server", plugin, internalName);
    }

    /**
     * Called when a player tries to open the "server inventory" (which we cancel).
     * Instead, we pass them into the client UI.
     */
    @Override
    protected Client createClientForPlayer(Player player) {
        TestClient client = new TestClient(this, player, plugin, internalName);
        client.initializeUI(false);
        return client;
    }

    /**
     * This method is called whenever the Client sends updates back to the Server
     * via `sendUpdateToServer(eventType, data)`.
     */
    @Override
    public void onClientUpdate(Client client, String eventType, Object data) {
        super.onClientUpdate(client, eventType, data);

        UUID clientId = client.getOwnerId();
        Player clientPlayer = Bukkit.getPlayer(clientId);
        if (clientPlayer == null) return;

        switch (eventType) {
            case "SIT":
                // data should be an Integer seatIndex => 0 for seat1, 1 for seat2
                handleSit(clientPlayer, (Integer) data);
                break;

            case "PLACE_BET":
                // data is a Double specifying how much the occupant bet
                handlePlaceBet(clientPlayer, (Double) data);
                break;

            case "ACCEPT_BET":
                handleAcceptBet(clientPlayer);
                break;

            case "RESET":
                // Possibly allow either occupant to reset the game
                resetGame();
                break;

            default:
                Bukkit.getLogger().info("[TestServer] Unknown eventType: " + eventType);
                break;
        }
        // After handling any event, refresh all clients' UI
        updateAllClients();
    }

    /**
     * Player attempts to sit down in one of the two seats.
     */
    private void handleSit(Player player, int seatIndex) {
        UUID playerId = player.getUniqueId();
    
        // If the player is already in seat1 or seat2, deny sitting in the other.
        if (seat1 != null && seat1.equals(playerId) && seatIndex == 1) {
            // The same player is already in seat1 and is now trying to sit in seat2
            player.sendMessage("You are already seated in seat1!");
            return;
        }
        if (seat2 != null && seat2.equals(playerId) && seatIndex == 0) {
            // The same player is already in seat2 and is now trying to sit in seat1
            player.sendMessage("You are already seated in seat2!");
            return;
        }
    
        // Otherwise, proceed to seat them if that seat is free or already occupied by them.
        if (seatIndex == 0) {
            // Seat1
            if (seat1 == null || seat1.equals(playerId)) {
                seat1 = playerId; // Occupy seat1
            } else {
                // seat1 is taken by another player
                player.sendMessage("Someone else is already seated in seat1!");
            }
        } else {
            // Seat2
            if (seat2 == null || seat2.equals(playerId)) {
                seat2 = playerId; // Occupy seat2
            } else {
                // seat2 is taken by another player
                player.sendMessage("Someone else is already seated in seat2!");
            }
        }
    
        // If seats were newly occupied and game was COMPLETED, optionally reset
        if (gameState == GameState.COMPLETED) {
            seat1Bet = 0.0;
            seat2Bet = 0.0;
            gameState = GameState.LOBBY;
        }
    }

    /**
     * The occupant of seat1 places a bet. 
     * We'll only allow seat1 occupant to propose a bet if gameState is LOBBY.
     */
    private void handlePlaceBet(Player player, double amount) {
        if (seat1 == null || !seat1.equals(player.getUniqueId())) {
            // Only seat1 occupant is allowed to place the initial bet
            return;
        }
        if (gameState != GameState.LOBBY) {
            return; // Can't place bet if not in LOBBY
        }
        seat1Bet = amount;
        // Move state to WAITING_FOR_ACCEPT if seat2 occupant exists
        if (seat2 != null) {
            gameState = GameState.WAITING_FOR_ACCEPT;
        }
    }

    /**
     * The occupant of seat2 needs to match the bet from seat1, then we do the 50/50.
     */
    private void handleAcceptBet(Player player) {
        if (seat2 == null || !seat2.equals(player.getUniqueId())) {
            // Only seat2 occupant can accept
            return;
        }
        if (gameState != GameState.WAITING_FOR_ACCEPT) {
            return; 
        }

        // The seat2 occupant must match seat1Bet 
        // (We skip the separate "placing" mechanism and assume a single accept means they put up seat1Bet.)
        seat2Bet = seat1Bet;

        // Now we do the 50/50 outcome
        boolean seat1Wins = random.nextBoolean();
        if (seat1Wins && seat1 != null) {
            // seat1 occupant wins the entire pot
            Player winner = Bukkit.getPlayer(seat1);
            if (winner != null) {
                int totalWin = (int) (seat1Bet + seat2Bet);
                // Refund the seat1 occupant's bet plus seat2 occupant's bet
                // We can reuse the parent's utility: client.removeCurrencyFromInventory(...) etc.
                // But for clarity, we do direct add. Typically you'd do something like:
                TestClient seat1Client = getClientFor(seat1);
                if (seat1Client != null) {
                    seat1Client.refundCurrency(winner, totalWin);
                }
                winner.sendMessage("You won " + totalWin + " " + plugin.getCurrencyName(internalName) + "!");
            }
            if (seat2 != null) {
                Player loser = Bukkit.getPlayer(seat2);
                if (loser != null) {
                    loser.sendMessage("You lost! " + seat1Bet + " " + plugin.getCurrencyName(internalName) + " was taken.");
                }
            }
        } else {
            // seat2 occupant wins
            Player winner = Bukkit.getPlayer(seat2);
            if (winner != null) {
                int totalWin = (int) (seat1Bet + seat2Bet);
                TestClient seat2Client = getClientFor(seat2);
                if (seat2Client != null) {
                    seat2Client.refundCurrency(winner, totalWin);
                }
                winner.sendMessage("You won " + totalWin + " " + plugin.getCurrencyName(internalName) + "!");
            }
            if (seat1 != null) {
                Player loser = Bukkit.getPlayer(seat1);
                if (loser != null) {
                    loser.sendMessage("You lost! " + seat1Bet + " " + plugin.getCurrencyName(internalName) + " was taken.");
                }
            }
        }

        gameState = GameState.COMPLETED;
    }

    /**
     * Reset everything, clearing seats and bets.
     */
    private void resetGame() {
        seat1 = null;
        seat2 = null;
        seat1Bet = 0.0;
        seat2Bet = 0.0;
        gameState = GameState.LOBBY;
    }

    /**
     * Fetch a typed TestClient by UUID (if it exists).
     */
    private TestClient getClientFor(UUID uuid) {
        Client c = clients.get(uuid);
        if (c instanceof TestClient) {
            return (TestClient) c;
        }
        return null;
    }

    /**
     * Convenience method to re‐send a "REFRESH_UI" event to all connected Clients.
     */
    private void updateAllClients() {
        for (Client c : clients.values()) {
            c.onServerUpdate("REFRESH_UI", null);
        }
    }

    // Getters so the clients can read the game state
    public UUID getSeat1() { return seat1; }
    public UUID getSeat2() { return seat2; }
    public double getSeat1Bet() { return seat1Bet; }
    public double getSeat2Bet() { return seat2Bet; }
    public GameState getGameState() { return gameState; }
}
