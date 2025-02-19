package org.nc.nccasino.games.TestGame;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.entities.Client;

import java.util.UUID;

public class TestServer extends Server {

    public TestServer(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, 27, "Test Game Server", plugin, internalName);
    }

    @Override
    protected Client createClientForPlayer(Player player) {
        TestClient client = new TestClient(this, player, plugin, internalName);
        client.initializeUI();

        // Send initial game state
        setClientState(player.getUniqueId(), SessionState.LOBBY);
    
        return client;
    }

    @Override
    public void onClientUpdate(Client client, String eventType, Object data) {
        super.onClientUpdate(client, eventType, data); // Log the event

        if (eventType.equals("SLOT_CLICKED")) {
            int slot = (int) data; // Ensure data is an integer slot number
            Bukkit.getLogger().info("[TestServer] Player clicked slot " + slot);

            // Toggle between LOBBY and IN_PROGRESS and update **all clients**
            SessionState newState = (getServerState() == SessionState.LOBBY) 
                ? SessionState.IN_PROGRESS 
                : SessionState.LOBBY;

            Bukkit.getLogger().info("[TestServer] Transitioning to " + newState);
            setServerState(newState); // This will now notify **all clients**
        }
    }
}
